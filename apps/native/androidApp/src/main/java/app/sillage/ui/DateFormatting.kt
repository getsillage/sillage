package app.sillage.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun localizedDate(raw: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(raw, locale) {
        runCatching {
            LocalDate.parse(raw).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
        }.getOrDefault(raw)
    }
}

@Composable
internal fun localizedTimestamp(raw: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(raw, locale) {
        runCatching {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(raw))
        }.getOrDefault(raw)
    }
}
