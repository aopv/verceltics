package com.apoorvdarshan.verceltics.ui.screens.about

import android.content.Context
import com.apoorvdarshan.verceltics.BuildConfig

class SharedPreferencesAppearancePreferenceStore(
    context: Context,
) : AppearancePreferenceStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    override fun load(): AboutAppearance {
        val storedValue = preferences.getString(KEY_APPEARANCE, null)
        val appearance = AboutAppearance.fromStoredValue(storedValue)
        if (storedValue != null && storedValue != appearance.persistedValue) {
            preferences.edit().remove(KEY_APPEARANCE).commit()
        }
        return appearance
    }

    override fun save(appearance: AboutAppearance) {
        check(preferences.edit().putString(KEY_APPEARANCE, appearance.persistedValue).commit()) {
            "Unable to persist the app appearance."
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "verceltics.preferences"
        const val KEY_APPEARANCE = "app.appearance"
    }
}

fun currentAndroidAppVersion(): AboutAppVersion = AboutAppVersion(
    name = BuildConfig.VERSION_NAME,
    code = BuildConfig.VERSION_CODE.toLong(),
)
