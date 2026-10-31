package com.pocketagent.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.Canvas

/**
 * 厂商图标：从 assets/icons.json 里读 SVG path，运行时转成 Compose Path 绘制。
 * 加新厂商只需往 icons.json 里加一条，不用改 Kotlin。
 */
object VendorIcons {

    class Def(val viewBox: Float, val paths: List<Path>)

    private val cache = mutableMapOf<String, Def?>()
    private val loaded = mutableStateOf(false)

    private val colors: Map<String, Color> = mapOf(
        "openai" to Color(0xFF0F0F0F),
        "anthropic" to Color(0xFFD97757),
        "deepseek" to Color(0xFF4D6BFE),
        "ollama" to Color(0xFF16191D),
        "qwen" to Color(0xFF082DFF),
        "xiaomi" to Color(0xFFFF6900),
        "kimi" to Color(0xFF16191D),
        "zhipu" to Color(0xFF3859FF),
        "openrouter" to Color(0xFF16191D),
        "lmstudio" to Color(0xFF4A5568),
    )

    fun colorOf(slug: String): Color = colors[slug] ?: Color(0xFF5F6368)

    /**
     * 深色主题下的图标颜色。
     *
     * OpenAI(#0F0F0F)、Ollama/Kimi/OpenRouter(#16191D) 这类近黑品牌色，
     * 放在 #0A0A0A 的图标底上等于隐形。做法是按亮度向白色插值，
     * 直到线性亮度达到 0.55 —— 这样既保住品牌色相（插值不改变色相），
     * 又保证在任何深色底上都能看清。彩色品牌（DeepSeek 蓝、小米橙）本来就够亮，不受影响。
     */
    fun colorOn(slug: String, dark: Boolean): Color {
        val base = colorOf(slug)
        if (!dark) return base
        var mixed = base
        var t = 0f
        while (linearLum(mixed) < 0.55f && t < 0.95f) {
            t += 0.05f
            mixed = androidx.compose.ui.graphics.lerp(base, Color.White, t)
        }
        return mixed
    }

    private fun linearLum(c: Color): Float =
        0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    fun ensureLoaded(context: Context) {
        if (loaded.value) return
        runCatching {
            val text = context.assets.open("icons.json").bufferedReader().use { it.readText() }
            val root = Json { ignoreUnknownKeys = true }.parseToJsonElement(text) as JsonObject
            root.forEach { (slug, value) ->
                val obj = value as? JsonObject ?: return@forEach
                val vb = runCatching { obj["vb"]!!.jsonPrimitive.float }.getOrDefault(24f)
                val ds = runCatching { obj["paths"]!!.jsonArray }.getOrNull() ?: return@forEach
                val paths = ds.mapNotNull { el ->
                    runCatching {
                        PathParser().parsePathString(el.jsonPrimitive.content).toPath()
                    }.getOrNull()
                }
                cache[slug] = Def(vb, paths)
            }
        }
        loaded.value = true
    }

    fun def(slug: String): Def? = cache[slug]

    /** 只有提供了位图资源的厂商才走这里（目前只有 Agnes） */
    private val bitmapSlugs = setOf("agnes")
    private val bitmaps = mutableMapOf<String, android.graphics.Bitmap?>()

    fun bitmap(context: Context, slug: String): android.graphics.Bitmap? {
        if (slug !in bitmapSlugs) return null
        return bitmaps.getOrPut(slug) {
            runCatching {
                context.assets.open("$slug.png").use(android.graphics.BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }
}

/**
 * 头像 / 图标块。有矢量图就画矢量图，没有就退回字母块（Agnes 属于后者）。
 * 圆形用于消息头像，圆角方形用于厂商列表。
 */
@Composable
fun VendorMark(
    slug: String?,
    fallbackText: String,
    fallbackColor: Color,
    dim: Dp = 26.dp,
    glyphDim: Dp = 15.dp,
    round: Boolean = true,
    showBorder: Boolean = true,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Agnes 只有位图（官网只给了 PNG），且自带圆形黑底，必须按圆形裁切
    val bmp = remember(slug) { slug?.let { VendorIcons.bitmap(ctx, it) } }
    val isGemini = slug == "gemini"
    val forceRound = slug == "agnes"

    val shape = if (round || forceRound) CircleShape else RoundedCornerShape(dim * 0.32f)
    val svg = if (bmp == null && !isGemini) VendorIcons.def(slug.orEmpty()) else null
    val hasArt = bmp != null || isGemini || svg != null
    // 在 Composable 上下文里先取好颜色：DrawScope 的 lambda 不是 @Composable，不能调 pal()
    val svgInk = if (svg != null) SolidColor(VendorIcons.colorOn(slug.orEmpty(), pal().isDark)) else null

    Box(
        modifier = Modifier
            .size(dim)
            .clip(shape)
            .background(
                when {
                    bmp != null -> Color.Transparent
                    hasArt -> pal().surface
                    else -> fallbackColor.copy(alpha = 0.12f)
                }
            )
            .then(if (showBorder) Modifier.border(1.dp, pal().lineSoft, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            isGemini -> GeminiStar(glyphDim)

            svg != null -> Canvas(Modifier.size(glyphDim)) {
                drawSvg(svg, svgInk!!)
            }

            else -> Text(
                text = fallbackText.take(2),
                color = fallbackColor,
                fontSize = (dim.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Gemini 的四角星。
 *
 * 原先直接用 lobehub 的彩色 SVG，但它的 path 大量使用相对圆弧（a 命令），
 * Compose 的 PathParser 解析后形状是错的 —— 渲染出来是一块紫色不规则团。
 * 这里按官方标志的几何关系重画：四段二次贝塞尔，控制点在轴向上，形成内凹的星角。
 */
@Composable
private fun GeminiStar(dim: Dp) {
    Canvas(Modifier.size(dim)) {
        val s = size.minDimension / 24f
        val star = Path().apply {
            moveTo(12f * s, 2f * s)
            quadraticBezierTo(12.9f * s, 11.1f * s, 22f * s, 12f * s)
            quadraticBezierTo(12.9f * s, 12.9f * s, 12f * s, 22f * s)
            quadraticBezierTo(11.1f * s, 12.9f * s, 2f * s, 12f * s)
            quadraticBezierTo(11.1f * s, 11.1f * s, 12f * s, 2f * s)
            close()
        }
        drawPath(
            star,
            Brush.linearGradient(
                colors = listOf(Color(0xFF4285F4), Color(0xFF9B72CB), Color(0xFFD96570)),
                start = Offset(3f * s, 3f * s),
                end = Offset(21f * s, 21f * s),
            ),
        )
    }
}

private fun DrawScope.drawSvg(def: VendorIcons.Def, brush: Brush) {
    val target = size.minDimension
    val scaleFactor = target / def.viewBox
    scale(scaleFactor, scaleFactor, pivot = Offset.Zero) {
        def.paths.forEach { drawPath(it, brush) }
    }
}
