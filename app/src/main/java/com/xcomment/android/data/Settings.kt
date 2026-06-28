package com.xcomment.android.data

import android.content.Context
import com.xcomment.android.R

/** Marker for enums that can be displayed in a dropdown. */
interface HasLabel {
    val labelRes: Int
}

/** Reply tone presets. [promptHint] is injected into the AI system prompt. */
enum class Tone(override val labelRes: Int, val promptHint: String) : HasLabel {
    FRIENDLY(R.string.tone_friendly, "warm, friendly and casual"),
    PROFESSIONAL(R.string.tone_professional, "clear, professional and respectful"),
    WITTY(R.string.tone_witty, "witty and clever, with light humor"),
    SUPPORTIVE(R.string.tone_supportive, "supportive, kind and encouraging"),
    CONTRARIAN(R.string.tone_contrarian, "politely contrarian, offering a different angle");

    companion object {
        fun fromName(name: String?): Tone =
            entries.firstOrNull { it.name == name } ?: FRIENDLY
    }
}

/** Reply language preference. */
enum class ReplyLanguage(override val labelRes: Int, val promptHint: String) : HasLabel {
    AUTO(R.string.lang_auto, "the same language as the post"),
    ENGLISH(R.string.lang_english, "English"),
    PERSIAN(R.string.lang_persian, "Persian (Farsi)");

    companion object {
        fun fromName(name: String?): ReplyLanguage =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/** Immutable snapshot of user settings. */
data class Settings(
    val apiKey: String,
    val apiUrl: String,
    val model: String,
    val tone: Tone,
    val language: ReplyLanguage,
    val count: Int,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
}

/** Single source of truth for persisted settings. */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Settings = Settings(
        apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
        apiUrl = prefs.getString(KEY_API_URL, DEFAULT_URL).orEmpty().ifBlank { DEFAULT_URL },
        model = prefs.getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL },
        tone = Tone.fromName(prefs.getString(KEY_TONE, Tone.FRIENDLY.name)),
        language = ReplyLanguage.fromName(prefs.getString(KEY_LANG, ReplyLanguage.AUTO.name)),
        count = prefs.getInt(KEY_COUNT, DEFAULT_COUNT).coerceIn(MIN_COUNT, MAX_COUNT),
    )

    fun save(settings: Settings) {
        prefs.edit()
            .putString(KEY_API_KEY, settings.apiKey.trim())
            .putString(KEY_API_URL, settings.apiUrl.trim().ifBlank { DEFAULT_URL })
            .putString(KEY_MODEL, settings.model.trim().ifBlank { DEFAULT_MODEL })
            .putString(KEY_TONE, settings.tone.name)
            .putString(KEY_LANG, settings.language.name)
            .putInt(KEY_COUNT, settings.count.coerceIn(MIN_COUNT, MAX_COUNT))
            .apply()
    }

    /** Persist only the tone (used by the overlay's quick tone switcher). */
    fun saveTone(tone: Tone) {
        prefs.edit().putString(KEY_TONE, tone.name).apply()
    }

    companion object {
        const val PREFS = "xcomment_settings"
        const val DEFAULT_URL = "https://api.freemodel.dev/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-5.4"
        const val DEFAULT_COUNT = 3
        const val MIN_COUNT = 2
        const val MAX_COUNT = 5

        private const val KEY_API_KEY = "apiKey"
        private const val KEY_API_URL = "apiUrl"
        private const val KEY_MODEL = "model"
        private const val KEY_TONE = "tone"
        private const val KEY_LANG = "language"
        private const val KEY_COUNT = "count"
    }
}
