package ch.qscqlmpa.wingtracker.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The core icon set has no chart glyph, so the Material "bar chart" shape is defined here. */
val BarChartIcon: ImageVector =
    ImageVector
        .Builder(
            name = "BarChart",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill =
                androidx.compose.ui.graphics
                    .SolidColor(Color.Black),
        ) {
            moveTo(5f, 9.2f)
            horizontalLineToRelative(3f)
            verticalLineTo(19f)
            horizontalLineTo(5f)
            close()
            moveTo(10.6f, 5f)
            horizontalLineToRelative(2.8f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(-2.8f)
            close()
            moveTo(16.2f, 13f)
            horizontalLineTo(19f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(-2.8f)
            close()
        }.build()
