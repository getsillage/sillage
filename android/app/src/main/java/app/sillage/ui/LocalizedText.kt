package app.sillage.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import app.sillage.data.SessionStore
import java.util.Locale

internal fun Context.localizedString(
    languageMode: String,
    @StringRes resourceId: Int,
    vararg formatArgs: Any,
): String {
    val locale = Locale.forLanguageTag(SessionStore.normalizeLanguageMode(languageMode))
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(configuration).resources.getString(resourceId, *formatArgs)
}
