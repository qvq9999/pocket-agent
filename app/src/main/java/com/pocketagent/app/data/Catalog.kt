package com.pocketagent.app.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * 厂商目录：随包发行的静态资源。
 * 端点变更只需替换 assets/providers.json，不用改代码、不用发新版逻辑。
 */
object Catalog {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Volatile
    private var cached: CatalogFile? = null

    fun load(context: Context): CatalogFile {
        cached?.let { return it }
        val text = context.assets.open("providers.json").bufferedReader().use { it.readText() }
        val parsed = json.decodeFromString(CatalogFile.serializer(), text)
        cached = parsed
        return parsed
    }

    fun find(context: Context, id: String): ProviderDef? =
        load(context).providers.firstOrNull { it.id == id }

    fun byRegion(context: Context): Map<String, List<ProviderDef>> =
        load(context).providers.groupBy { it.region }
}
