package com.pocketagent.app.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 一个内置技能的元信息。**不含内容** —— 只用来在菜单里列出条目。
 *
 * 内容放在 `assets/skills.zip` 里，首次启动解压到 `filesDir/skills/<id>/`。
 * 为什么打包成一个 zip 再解压，而不是把 1.3 万个文件直接铺进 assets：
 *  - APK 里多 1.3 万个条目会让打包和安装都变慢，zip 只有一个条目；
 *  - 解压后是普通文件，读手册比走 AssetManager 流快，也好做路径校验。
 */
@Serializable
data class SkillMeta(
    val id: String,
    val name: String,
    /** 菜单里显示的一句话（中文，面向用户） */
    val summary: String = "",
    /** SKILL.md 里那句英文原描述，给模型看的触发条件 */
    val description: String = "",
    val version: String = "",
    val license: String = "",
    val author: String = "",
    val source: String = "",
    /** 是否含可执行脚本（本机没有 Python/Node，脚本模式需要 Linux 环境） */
    val hasScripts: Boolean = false,
    val entry: String = "SKILL.md",
    val sizeBytes: Long = 0,
    /** 用户自己装的（在 skills_user/ 下），内置的没有这个标记 */
    val custom: Boolean = false,
)

@Serializable
private data class SkillCatalogFile(
    val version: Int = 1,
    val skills: List<SkillMeta> = emptyList(),
)

/**
 * 内置技能的资源管理：目录读取、首次解压、文件读取。
 *
 * 只有「解压」是重的（37MB / 1.3 万个小文件），所以在 App 启动时后台做一次；
 * 菜单只读 catalog（几 KB），任何时候都能立刻弹出来。
 */
object SkillStore {

    /** 资源包版本。换了 assets/skills.zip 就把这个数字 +1，旧副本会被重装 */
    private const val PACK_VERSION = 3
    private const val ZIP = "skills.zip"
    private const val CATALOG = "skills_catalog.json"
    private const val DIR = "skills"
    private const val USER_DIR = "skills_user"
    private const val STAMP = ".pack"
    private const val META = ".meta.json"

    /** 单个文件读取上限：技能手册一般 < 20KB，超过这个尺寸的多半是资源（字体/音频） */
    private const val MAX_TEXT = 200 * 1024

    private const val TAG = "SkillStore"

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedCatalog: List<SkillMeta>? = null

    /**
     * 内置 + 用户技能，合成一份清单。
     *
     * 内置的读 assets 里的 JSON（几 KB）；用户的逐个读 `.meta.json`（通常就几个目录）。
     * 同 id 时**用户版覆盖内置版**：想换某份手册的版本，装一个新的同名技能即可。
     */
    fun catalog(ctx: Context): List<SkillMeta> {
        cachedCatalog?.let { return it }
        return synchronized(this) {
            cachedCatalog ?: run {
                val text = runCatching {
                    ctx.assets.open(CATALOG).bufferedReader().use { it.readText() }
                }.getOrNull().orEmpty()
                val builtin = runCatching {
                    json.decodeFromString<SkillCatalogFile>(text).skills
                }.getOrDefault(emptyList())
                val merged = LinkedHashMap<String, SkillMeta>()
                builtin.forEach { merged[it.id] = it }
                userMetas(ctx).forEach { merged[it.id] = it }
                val list = merged.values.toList()
                cachedCatalog = list
                list
            }
        }
    }

    @Volatile
    private var cachedUser: List<SkillMeta>? = null

    private fun userMetas(ctx: Context): List<SkillMeta> {
        cachedUser?.let { return it }
        val out = ArrayList<SkillMeta>()
        val dirs = userRoot(ctx).listFiles()?.filter { it.isDirectory }.orEmpty()
        for (d in dirs) {
            val f = File(d, META)
            val meta = runCatching {
                json.decodeFromString(SkillMeta.serializer(), f.readText())
            }.getOrNull()
            out.add(
                meta ?: SkillMeta(
                    id = d.name,
                    name = d.name,
                    summary = "自定义技能",
                    source = "本地",
                    sizeBytes = d.walkTopDown().filter { x -> x.isFile }.sumOf { x -> x.length() },
                    custom = true,
                )
            )
        }
        cachedUser = out.sortedBy { it.name }
        return cachedUser!!
    }

    fun find(ctx: Context, id: String): SkillMeta? = catalog(ctx).firstOrNull { it.id == id }

    fun root(ctx: Context): File = File(ctx.filesDir, DIR)

    /** 用户自己装的技能，与内置的物理分开：内置的重装/升级不会冲掉用户的 */
    fun userRoot(ctx: Context): File = File(ctx.filesDir, USER_DIR).apply { mkdirs() }

    fun isBuiltin(ctx: Context, id: String): Boolean = catalog(ctx).any { it.id == id && !it.custom }

    fun isUserSkill(ctx: Context, id: String): Boolean = File(userRoot(ctx), id).isDirectory

    /**
     * 技能目录。先看用户目录再看内置目录 ——
     * 允许用户装一个同名技能覆盖内置的（想换版本时这是最自然的做法）。
     */
    fun skillDir(ctx: Context, id: String): File {
        val user = File(userRoot(ctx), id)
        if (user.isDirectory) return user
        return File(root(ctx), id)
    }

    /** 装完/删完要让 catalog 重新读一次 */
    fun invalidate() {
        cachedCatalog = null
        cachedUser = null
    }

    fun isReady(ctx: Context): Boolean {
        val stamp = File(root(ctx), STAMP)
        if (!stamp.isFile) return false
        return runCatching { stamp.readText().trim() }.getOrNull() == PACK_VERSION.toString()
    }

    /**
     * 解压内置资源包。
     *
     * 先解到 `skills.tmp` 再整体改名：中途被系统杀掉也不会留下"半个包"——
     * 那种状态下读手册会报"文件不存在"，而用户完全不知道发生了什么。
     */
    @Synchronized
    fun ensure(ctx: Context): Boolean {
        if (isReady(ctx)) return true
        return runCatching {
            val root = root(ctx)
            val tmp = File(ctx.filesDir, "$DIR.tmp")
            tmp.deleteRecursively()
            tmp.mkdirs()
            val tmpPrefix = tmp.canonicalPath + File.separator

            ctx.assets.open(ZIP).use { raw ->
                ZipInputStream(BufferedInputStream(raw, 64 * 1024)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        // 防 zip-slip：绝对路径和 .. 一律丢弃（内置包本来可信，这道是纪律）
                        // zip 内的路径是 "skills/<id>/..."，而解压根目录本身就是 filesDir/skills，
                        // 不剥掉这层前缀就会解成 skills/skills/<id>/...（曾经就这么错过一次）
                        val rel = name.removePrefix("skills/")
                        if (rel.isNotBlank() && !name.startsWith("/") && !name.contains("..")) {
                            val target = File(tmp, rel)
                            if (entry.isDirectory) {
                                target.mkdirs()
                            } else if (target.canonicalPath.startsWith(tmpPrefix)) {
                                target.parentFile?.mkdirs()
                                target.outputStream().use { out -> zis.copyTo(out, 64 * 1024) }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            root.deleteRecursively()
            if (!tmp.renameTo(root)) {
                tmp.copyRecursively(root, overwrite = true)
                tmp.deleteRecursively()
            }
            File(root, STAMP).writeText(PACK_VERSION.toString())
            Log.i(TAG, "skills extracted: " + tree(ctx, "ppt-master", 99999).size + " files (ppt-master)")
            true
        }.onFailure { Log.w(TAG, "extract failed", it) }.getOrDefault(false)
    }

    /**
     * 读技能里的一个文本文件；[rel] 为空读入口（SKILL.md）。
     *
     * 三道校验：技能必须存在、路径不许越界、文件必须够小。
     * 任何一条不满足都抛 [IllegalArgumentException]，由工具层转成给模型看的一句话。
     */
    fun readText(ctx: Context, id: String, rel: String = ""): String {
        val all = catalog(ctx)
        val meta = find(ctx, id) ?: throw IllegalArgumentException(
            // 文案要**完整可读**：原来把全部 id 拼进一句话，界面上两行就被截成
            // "可用：ppt-" —— 用户看到的是一条断掉的信息，等于什么都没说。
            // 现在只列 3 个，并给一个最接近的候选（模型经常把 id 记成 project-manager 这种）。
            buildString {
                append("没有「").append(id).append("」这个技能。")
                nearestSkill(id, all)?.let { append("你是想用「").append(it.id).append("」吗？") }
                append("可用的有：")
                append(all.take(3).joinToString("、") { it.id })
                if (all.size > 3) append(" 等 ").append(all.size).append(" 个")
            },
        )
        val path = rel.trim().ifBlank { meta.entry }
        if (path.startsWith("/") || path.contains("..")) {
            throw IllegalArgumentException("路径不合法：$path（只能用技能目录内的相对路径）")
        }
        val dir = skillDir(ctx, id)
        val file = File(dir, path)
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            throw IllegalArgumentException("路径不合法：$path")
        }
        if (!file.isFile) {
            throw IllegalArgumentException(
                buildString {
                    append("「").append(id).append("」里没有 ").append(path).append("。")
                    append("实际有的是：").append(tree(ctx, id).take(6).joinToString("、"))
                    append("（用 skill_read 不传 path 可以直接读 SKILL.md）")
                },
            )
        }
        if (file.length() > MAX_TEXT) {
            throw IllegalArgumentException(
                "$path 有 ${file.length() / 1024} KB，这是资源文件不是技能手册，不要整篇读",
            )
        }
        return file.readText()
    }

    /**
     * 删除。
     *
     * 内置技能的资源在 assets 里删不掉，只能删解压出来的副本（下次重装还能回来）；
     * 用户装的技能是真删 —— 它在 filesDir 里只有这一份，删了就得重装。
     */
    /**
     * 猜"用户/模型其实想用哪个技能"。
     *
     * 只做一件很朴素的事：看两个名字是不是包含关系（"ppt" 与 "ppt-master" 命中，
     * "project-manager" 与任何技能都不命中）。不引编辑距离 —— 这个场景不需要那么准；
     * 给一个可能错的候选，也好过给一句被截断的列表。
     */
    private fun nearestSkill(asked: String, all: List<SkillMeta>): SkillMeta? {
        val q = asked.lowercase().trim()
        if (q.isBlank()) return null
        return all.firstOrNull { it.id.lowercase() in q || q in it.id.lowercase() }
            ?: all.firstOrNull { sk ->
                val n = sk.name.lowercase()
                n.isNotBlank() && (n in q || q in n)
            }
    }

    fun deleteCopy(ctx: Context, id: String): Boolean = runCatching {
        val user = File(userRoot(ctx), id)
        val ok = if (user.isDirectory) user.deleteRecursively()
        else File(root(ctx), id).deleteRecursively()
        invalidate()
        ok
    }.getOrDefault(false)

    /** 重装：清掉标记后整体重新解压（只影响 filesDir 里的副本，不动用户工作区） */
    fun reinstall(ctx: Context): Boolean {
        runCatching { File(root(ctx), STAMP).delete() }
        runCatching { root(ctx).deleteRecursively() }
        return ensure(ctx)
    }

    /**
     * 技能是否可读：内置的看解压副本在不在，自定义的看目录在不在。
     * 停用只是不启用，不会删文件 —— 所以"可读"与"启用"是两件事。
     */
    fun isAvailable(ctx: Context, id: String, disabled: List<String>): Boolean =
        id !in disabled && skillDir(ctx, id).isDirectory

    /** 列技能的文件（两层，够看清结构）：给"这个技能包含什么"用 */
    fun tree(ctx: Context, id: String, limit: Int = 40): List<String> {
        val dir = skillDir(ctx, id)
        if (!dir.isDirectory) return emptyList()
        return dir.walkTopDown()
            .maxDepth(2)
            .filter { it.isFile }
            .map { it.relativeTo(dir).path.replace(File.separatorChar, '/') }
            .sorted()
            .take(limit)
            .toList()
    }
}
