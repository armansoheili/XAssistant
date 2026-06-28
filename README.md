# XComment AI — Smart replies for X (Twitter)

**Version 0.2.0** — A polished Android app that generates AI-powered reply ideas for X (Twitter) posts and inserts them into your reply box.

<div align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-green" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Kotlin-2.0-blue" alt="Kotlin 2.0"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-✓-brightgreen" alt="Jetpack Compose"/>
</div>

---

## ✨ Features

- **🫧 Floating AI bubble** — Shows only when you're in X/Twitter
- **🎨 Material 3 design** — Modern UI with light/dark mode support
- **🌍 Bilingual** — Full support for English and Persian (فارسی)
- **🎭 Tone control** — Choose friendly, professional, witty, supportive, or contrarian
- **⚡ Instant + AI suggestions** — Quick ideas appear immediately, refined AI suggestions follow
- **🔐 Privacy-first** — Only inserts text; you always press Post manually
- **🎯 Smart text detection** — Reads the visible post via Accessibility API

---

## 📱 What it does

1. Open X (Twitter) and view any post you want to reply to.
2. Tap the floating **AI** bubble.
3. Select a **tone** (friendly, professional, witty, etc.).
4. Tap a **reply idea** to insert it into the focused reply box.
5. Review and **press Reply yourself** — XComment never auto-posts.

If no reply box is focused, the text is copied to your clipboard.

---

## 🚀 Setup

### Option A: Install from GitHub Actions (No Android Studio needed)

1. Fork or upload this repository to your GitHub account.
2. Go to **Actions** → **Build Debug APK** → **Run workflow**.
3. When complete, download the `XCommentAI-debug-apk` artifact.
4. Extract the ZIP and install the APK on your phone.
5. If blocked, enable **Install unknown apps** for your file manager.

### Option B: Build with Android Studio

1. Clone this repository.
2. Open the folder in **Android Studio Koala | 2024.1.1** or newer.
3. Sync Gradle (Android Studio does this automatically).
4. Run on your device or emulator.

### After installation

1. Open **XComment AI** on your phone.
2. Tap **Grant** next to **Floating button** → allow overlay permission.
3. Tap **Enable** next to **Accessibility service** → enable **XComment AI Assistant**.
4. Tap **Open** next to **API key** and enter:
   - **API key**: your FreeModel or OpenAI key (e.g., `fe_...` or `sk-...`)
   - **API URL**: `https://api.freemodel.dev/v1/chat/completions` (or your custom endpoint)
   - **Model**: `gpt-5.4` (or any fast OpenAI-compatible model)
5. Tap **Save settings**.

---

## 🎨 Screenshots

<table>
  <tr>
    <td><b>Home (Light)</b></td>
    <td><b>Home (Dark)</b></td>
    <td><b>Overlay with ideas</b></td>
  </tr>
  <tr>
    <td><i>Setup progress, permissions, settings</i></td>
    <td><i>Full dark mode support</i></td>
    <td><i>Tone selector + AI suggestions</i></td>
  </tr>
</table>

---

## 🏗️ Architecture

```
app/
├── ai/
│   └── AiClient.kt           # OpenAI-compatible API client
├── data/
│   └── Settings.kt           # Settings store, tone/language enums
├── ui/theme/
│   ├── Color.kt              # Material 3 color scheme
│   ├── Theme.kt              # Light/dark theme with edge-to-edge
│   └── Type.kt               # Typography
├── MainActivity.kt           # Jetpack Compose home screen
├── Permissions.kt            # Overlay + Accessibility checks
└── XCommentAccessibilityService.kt  # Floating bubble + overlay panel
```

### Key improvements in v0.2.0

- **Jetpack Compose + Material 3** — Modern, reactive UI with live permission status.
- **Fixed package detection bug** — Old version matched any app with "x" in the package name; now correctly detects only X/Twitter.
- **Tone selector** — Choose reply tone on-the-fly in the overlay.
- **Bilingual** — Full English + Persian (RTL) support.
- **Dark mode** — Overlay and home screen respect system theme.
- **Better AI parsing** — Handles malformed JSON, extracts text from markdown code blocks.
- **Clean separation** — `AiClient`, `SettingsStore`, theme package.

---

## 🔒 Privacy & safety

- **No auto-posting**: XComment only inserts text into the reply box. You always press Post manually.
- **Local processing**: Your API key stays on your device; we never see your data.
- **Open source**: Audit the code yourself.

---

## 🛠️ Customization

### Change the default AI provider

Edit `app/src/main/java/com/xcomment/android/data/Settings.kt`:

```kotlin
const val DEFAULT_URL = "https://api.openai.com/v1/chat/completions"
const val DEFAULT_MODEL = "gpt-4o-mini"
```

### Add more tones

Add a new entry to the `Tone` enum in `Settings.kt`:

```kotlin
SARCASTIC(R.string.tone_sarcastic, "sarcastic and dry"),
```

Then add the string resource in `res/values/strings.xml`:

```xml
<string name="tone_sarcastic">Sarcastic</string>
```

### Modify the system prompt

Edit `AiClient.kt`, function `buildPayload`, line ~45.

---

## 📦 Dependencies

- **Jetpack Compose BOM** `2024.10.01`
- **Material 3** — Google's latest design system
- **Activity Compose** `1.9.3`
- **Lifecycle Runtime Compose** `2.8.7`
- No third-party HTTP libraries — uses `HttpURLConnection` for zero bloat

---

## 🐛 Troubleshooting

**Q: The bubble doesn't appear on X.**  
A: Make sure the accessibility service is enabled: Settings → Accessibility → XComment AI Assistant → On.

**Q: "API error 401"**  
A: Your API key is invalid or expired. Check it in XComment settings.

**Q: "No post detected"**  
A: Open a tweet/post in X first, then tap the AI bubble.

**Q: Dark mode overlay looks wrong.**  
A: Make sure your Android system dark mode is on. The overlay follows system theme.

**Q: Can I use this with a local LLM?**  
A: Yes, as long as it exposes an OpenAI-compatible `/v1/chat/completions` endpoint. Set the API URL to your local server (e.g., `http://192.168.1.100:8000/v1/chat/completions`).

---

## 🚧 Roadmap

- [ ] Conversation context (read parent tweets before generating replies)
- [ ] Custom prompt templates
- [ ] Reply history log
- [ ] Auto-detect best reply box (handle X UI changes)
- [ ] Optional one-tap post (if user opts in)

---

## 📄 License

MIT License — see `LICENSE` file.

---

## 🙏 Credits

Built with:
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material 3](https://m3.material.io/)
- [FreeModel API](https://freemodel.dev) (default provider)

---

**Made with ❤️ for the X community**
