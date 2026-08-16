package org.openmeds.reminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MedicationLightColors = lightColorScheme(
    primary = DeepGreen,
    onPrimary = OnDeepGreen,
    primaryContainer = DeepGreenDark,
    onPrimaryContainer = OnDeepGreen,
    secondary = DeepGreenDark,
    onSecondary = OnDeepGreen,
    background = WarmWhite,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = AmberContainer,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = OnDeepGreen
)

@Composable
fun MedicationReminderTheme(content: @Composable () -> Unit) {
    // The app is intentionally always light: high contrast for older users.
    val colorScheme = MedicationLightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MedicationTypography,
        content = content
    )
}
