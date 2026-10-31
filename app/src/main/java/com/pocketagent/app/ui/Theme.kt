package com.pocketagent.app.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * 调色板。浅色/深色各一份，通过 LocalPalette 下发。
 * 之所以不直接用 MaterialTheme.colorScheme：视觉稿的语义比 M3 更细（diff 行、权限档位、媒体标签），
 * 硬塞进 M3 的 primary/secondary 反而会丢语义。
 */
@Immutable
data class Palette(
    val isDark: Boolean,
    val ink: Color,
    val muted: Color,
    val hint: Color,
    val faint: Color,
    val line: Color,
    val lineSoft: Color,
    val surface: Color,
    val surfaceAlt: Color,
    /** 卡片 / 面板底色（浅色下是纯白，深色下是卡片灰） */
    val card: Color,
    val pageBg: Color,
    val scrim: Color,

    val accent: Color,
    /** 强调色上的文字/图标色：浅色主题为白，深色主题为深色（深色主题的强调色是亮蓝） */
    val onAccent: Color,
    val accentSoft: Color,
    val accentInk: Color,

    val ok: Color,
    val okSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val warnLine: Color,
    val bad: Color,
    val badSoft: Color,
    val media: Color,
    val mediaSoft: Color,

    val modelChipBg: Color,
    val modelChipInk: Color,
    val permChipBg: Color,
    val permChipInk: Color,
    val userBubble: Color,
    /** AI 回复气泡底色。比页面底色略深/略亮一点，靠一层柔和底把长段回复圈起来 */
    val aiBubble: Color,

    val diffAddBg: Color,
    val diffAddInk: Color,
    val diffDelBg: Color,
    val diffDelInk: Color,

    val infoBg: Color,
    val infoLine: Color,
    val infoInk: Color,
)

val LightPalette = Palette(
    isDark = false,
    ink = Color(0xFF1B1C1E),
    muted = Color(0xFF5F6368),
    hint = Color(0xFF8A9099),
    faint = Color(0xFFA2A8B0),
    line = Color(0xFFE3E5EA),
    lineSoft = Color(0xFFEDEFF3),
    surface = Color(0xFFF7F8FA),
    surfaceAlt = Color(0xFFF1F3F6),
    card = Color(0xFFFFFFFF),
    pageBg = Color(0xFFFFFFFF),
    scrim = Color(0x57181C22),

    accent = Color(0xFF1A73E8),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE8F0FE),
    accentInk = Color(0xFF12508F),

    ok = Color(0xFF1E7B4D),
    okSoft = Color(0xFFE7F4EC),
    warn = Color(0xFF8A5A00),
    warnSoft = Color(0xFFFDF3E0),
    warnLine = Color(0xFFF3E2C4),
    bad = Color(0xFFB3261E),
    badSoft = Color(0xFFFDECEC),
    media = Color(0xFF6B3FA0),
    mediaSoft = Color(0xFFF4EEFB),

    modelChipBg = Color(0xFFEAF1FD),
    modelChipInk = Color(0xFF12508F),
    permChipBg = Color(0xFFE9F4EF),
    permChipInk = Color(0xFF1E6B50),
    userBubble = Color(0xFFDCE8FB),
    aiBubble = Color(0xFFF4F5F7),

    diffAddBg = Color(0xFFEAF6EF),
    diffAddInk = Color(0xFF17603C),
    diffDelBg = Color(0xFFFCEEEC),
    diffDelInk = Color(0xFFA3241B),

    infoBg = Color(0xFFF4F8FE),
    infoLine = Color(0xFFDCE8FA),
    infoInk = Color(0xFF2D4A6B),
)

val DarkPalette = Palette(
    isDark = true,
    // OLED 友好：页面背景纯黑 → 像素真正熄灭；卡片只抬高到 #0A0A0A，
    // 保留层级感同时避免大面积纯黑与纯黑相接导致滚动拖影
    ink = Color(0xFFE5E5E5),
    muted = Color(0xFFA3A3A3),
    hint = Color(0xFF808080),
    faint = Color(0xFF6A6A6A),
    line = Color(0xFF1F1F1F),
    lineSoft = Color(0xFF171717),
    surface = Color(0xFF0A0A0A),
    surfaceAlt = Color(0xFF141414),
    card = Color(0xFF0A0A0A),
    pageBg = Color(0xFF000000),
    scrim = Color(0xB3000000),

    accent = Color(0xFF8AB4F8),
    onAccent = Color(0xFF0B1B2B),
    accentSoft = Color(0xFF1E2C3D),
    accentInk = Color(0xFFAECBFA),

    ok = Color(0xFF6BC48F),
    okSoft = Color(0xFF16281F),
    warn = Color(0xFFE0B15C),
    warnSoft = Color(0xFF2E2415),
    warnLine = Color(0xFF46361C),
    bad = Color(0xFFF08A80),
    badSoft = Color(0xFF33191A),
    media = Color(0xFFB79BE0),
    mediaSoft = Color(0xFF241C31),

    modelChipBg = Color(0xFF1E2C3D),
    modelChipInk = Color(0xFFAECBFA),
    permChipBg = Color(0xFF16281F),
    permChipInk = Color(0xFF9CD3B4),
    userBubble = Color(0xFF1E2A42),
    aiBubble = Color(0xFF1A1A1A),

    diffAddBg = Color(0xFF14251A),
    diffAddInk = Color(0xFF8FD3A8),
    diffDelBg = Color(0xFF2E1717),
    diffDelInk = Color(0xFFF0A9A1),

    infoBg = Color(0xFF14202D),
    infoLine = Color(0xFF23384F),
    infoInk = Color(0xFFA8C8E8),
)

/** 文字样式也依赖调色板（颜色内嵌在 TextStyle 里），所以与 Palette 一并下发 */
class Ty(val p: Palette) {
    val mono: FontFamily = FontFamily.Monospace

    val screenTitle = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
    val barTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = p.ink)
    val body = TextStyle(fontSize = 14.5.sp, lineHeight = 23.sp, color = p.ink)
    val bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = p.ink)
    val caption = TextStyle(fontSize = 11.5.sp, lineHeight = 17.sp, color = p.hint)
    val micro = TextStyle(fontSize = 10.5.sp, lineHeight = 15.sp, color = p.faint)
    val label = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = p.muted)
    val modelName = TextStyle(fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = p.hint)
    val monoId = TextStyle(fontSize = 11.sp, fontFamily = mono, color = p.hint)
    val monoCode = TextStyle(fontSize = 12.sp, fontFamily = mono, color = p.ink)
}

val LocalPalette = staticCompositionLocalOf { LightPalette }
val LocalTy = staticCompositionLocalOf { Ty(LightPalette) }

@Composable
@ReadOnlyComposable
fun pal(): Palette = LocalPalette.current

@Composable
@ReadOnlyComposable
fun ty(): Ty = LocalTy.current

private val LightScheme = lightColorScheme(
    primary = LightPalette.accent,
    onPrimary = Color.White,
    background = LightPalette.pageBg,
    onBackground = LightPalette.ink,
    surface = LightPalette.card,
    onSurface = LightPalette.ink,
    surfaceVariant = LightPalette.surfaceAlt,
    outline = LightPalette.line,
)

private val DarkScheme = darkColorScheme(
    primary = DarkPalette.accent,
    onPrimary = Color(0xFF0B1B2B),
    background = DarkPalette.pageBg,
    onBackground = DarkPalette.ink,
    surface = DarkPalette.card,
    onSurface = DarkPalette.ink,
    surfaceVariant = DarkPalette.surfaceAlt,
    outline = DarkPalette.line,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun AgentTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (dark) DarkPalette else LightPalette
    val typography = remember(palette) { Ty(palette) }

    // 状态栏/导航栏图标明暗必须跟着主题走，否则深色下图标会"隐身"
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalPalette provides palette, LocalTy provides typography) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            content = content,
        )
    }
}
