package ch.qscqlmpa.wingtracker.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The core icon set has no update glyph, so the Material "upgrade" shape is defined here. */
val UpdateIcon: ImageVector =
    ImageVector
        .Builder(
            name = "Upgrade",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(16f, 18f)
            verticalLineToRelative(2f)
            horizontalLineTo(8f)
            verticalLineToRelative(-2f)
            close()
            moveTo(11f, 7.99f)
            verticalLineTo(16f)
            horizontalLineToRelative(2f)
            verticalLineTo(7.99f)
            horizontalLineToRelative(3f)
            lineTo(12f, 4f)
            lineTo(8f, 7.99f)
            close()
        }.build()
