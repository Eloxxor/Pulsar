package com.esusic.recoverytracker.ui.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.time.format.DateTimeFormatter
import java.util.Locale

// Read through the configuration rather than Locale.getDefault() so a language change
// recomposes instead of leaving stale text on screen.
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

// The pattern itself is a translated resource: German writes "27. Jul", English "Jul 27".
@Composable
fun rememberDateFormatter(@StringRes pattern: Int): DateTimeFormatter {
    val patternText = stringResource(pattern)
    val locale = currentLocale()
    return remember(patternText, locale) { DateTimeFormatter.ofPattern(patternText, locale) }
}
