package ch.qscqlmpa.wingtracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Soft, consistent corners: small for chips and fields, medium for cards, large for dialogs. */
internal val AppShapes =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

private val Base = Typography()

/**
 * The platform font is kept; hierarchy comes from weight. Headings and the titles inside cards are semibold so
 * the eye finds them first, and body text stays regular for long reading.
 */
internal val AppTypography =
    Typography(
        headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
