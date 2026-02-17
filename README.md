# Abubble

**Your AI-powered writing assistant that floats over any app.**

Abubble is an Android overlay app that reads what you're typing in any app — WhatsApp, Messenger, Email, Slack — processes it with AI, and replaces the text with an improved version automatically.

---

## How It Works

1. **A floating bubble** sits on the edge of your screen (like a Messenger Chat Head).
2. **Tap it** while typing in any app — it captures the text from the focused input field.
3. **Give a command** like *"Fix grammar"*, *"Make professional"*, or *"Translate to Spanish"*.
4. **AI processes it** and the improved text is pasted back into the original text field automatically.

No copy-pasting. No switching apps. It just works.

---

## Features

- **Works Everywhere** — Floats over any app using Android's Accessibility Service & overlay system.
- **AI-Powered** — Uses [OpenRouter](https://openrouter.ai) to access models like GPT-4, Claude, DeepSeek, and more.
- **BYOK (Bring Your Own Key)** — Your OpenRouter API key is stored securely with `EncryptedSharedPreferences`.
- **Model Picker** — Choose from all available OpenRouter models with a built-in searchable picker.
- **Deep Thinking Mode** — Toggle reasoning/chain-of-thought for models that support it (e.g., DeepSeek R1).
- **Smart Text Replacement** — AI output goes directly into the text field you were typing in. No clipboard juggling.
- **Draggable Bubble** — Move the bubble anywhere on screen. It snaps to edges.
- **Long-Press Menu** — Long-press the bubble to close it. Relaunch from the app anytime.

---

## Screenshots

*Coming soon*

---

## Setup

### Prerequisites

- Android device running **Android 7.0 (API 24)** or higher
- An [OpenRouter API key](https://openrouter.ai/keys)

### Installation

1. Clone the repo:
   ```bash
   git clone https://github.com/your-username/Abubble.git
   ```
2. Open in **Android Studio**.
3. Sync Gradle and build.
4. Install on your device.

### First Launch

1. Open Abubble and grant the two required permissions:
   - **Accessibility Service** — lets Abubble read and write to text fields.
   - **Display Over Other Apps** — lets the floating bubble appear.
2. Paste your **OpenRouter API key** and save.
3. Pick a **model** (e.g., `deepseek/deepseek-chat`, `openai/gpt-4o`).
4. Tap **Launch Bubble** — you're ready to go!

> **Xiaomi/Redmi users:** Go to *Settings → Apps → Abubble → Battery Saver → No restrictions* and lock the app in recent apps to prevent the OS from killing the service.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI (Settings) | Jetpack Compose + Material 3 |
| UI (Overlay) | WindowManager + XML layouts |
| Networking | Retrofit 2 + OkHttp |
| AI Backend | OpenRouter API (v1) |
| Security | AndroidX Security Crypto (`EncryptedSharedPreferences`) |
| Service | AccessibilityService with `TYPE_ACCESSIBILITY_OVERLAY` |
| Concurrency | Kotlin Coroutines |

---

## Project Structure

```
app/src/main/java/com/shadow/abubble/
├── MainActivity.kt              # Settings screen (Compose)
├── data/
│   ├── OpenRouterModels.kt      # API data models
│   ├── OpenRouterApi.kt         # Retrofit API interface
│   ├── RetrofitClient.kt        # OkHttp + Retrofit singleton
│   └── ModelRepository.kt       # Cached model repository
├── service/
│   └── BubbleService.kt         # Core service: bubble, prompt, AI, paste
└── util/
    └── SecurePrefs.kt           # Encrypted SharedPreferences helper
```

---

## Permissions

| Permission | Why |
|-----------|-----|
| `SYSTEM_ALERT_WINDOW` | Draw the floating bubble and prompt dialog over other apps |
| `BIND_ACCESSIBILITY_SERVICE` | Read text from focused input fields and paste AI results back |
| `INTERNET` | Communicate with the OpenRouter API |

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

---

## Acknowledgments

- [OpenRouter](https://openrouter.ai) for unified AI model access
- Built with Android's Accessibility APIs and WindowManager overlay system
