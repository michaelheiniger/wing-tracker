package ch.qscqlmpa.wingtracker.ui.statistics

import androidx.compose.ui.graphics.Color

/**
 * Chart colors. The categorical set is the documented data-visualization palette, validated for
 * color-blind separation of neighbouring slots on exactly these surfaces; its slot order is part of
 * that validation, so never reorder or add slots. A series keeps the slot of its wing (see [Series]),
 * so hiding another wing never repaints it.
 */
class ChartTheme private constructor(
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val grid: Color,
    private val palette: List<Color>,
    private val other: Color,
) {
    fun colorOf(slot: Int): Color = palette[slot]

    val otherColor: Color get() = other

    companion object {
        private val LIGHT =
            listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4, 0xFF008300, 0xFF6250D6, 0xFFE34948)
        private val DARK =
            listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181, 0xFF008300, 0xFF9085E9, 0xFFE66767)

        val Light =
            ChartTheme(
                surface = Color(0xFFFCFCFB),
                textPrimary = Color(0xFF0B0B0B),
                textSecondary = Color(0xFF52514E),
                grid = Color(0xFFE4E3DF),
                palette = LIGHT.map(::Color),
                other = Color(0xFF75746F),
            )
        val Dark =
            ChartTheme(
                surface = Color(0xFF1A1A19),
                textPrimary = Color(0xFFF0EFEC),
                textSecondary = Color(0xFFC3C2B7),
                grid = Color(0xFF383835),
                palette = DARK.map(::Color),
                other = Color(0xFF8F8E88),
            )

        const val SLOT_COUNT = 8
    }
}
