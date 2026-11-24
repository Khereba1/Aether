# Aether: handoff notes for an AI assistant

Paste this file together with the project zip when asking an AI to change the app.
The family-wide rules are in STANDARD.md (ask the owner to attach it if missing).

## What this is
Aether is a native Android personal AI agent workspace app (Claude-inspired warm editorial UI) that connects over loopback (127.0.0.1) to an embedded zero-dependency Python 3 bridge inside Termux Ubuntu, driving Antigravity CLI (`agy`, Google AI Pro), Gemini CLI (`gemini`), or Claude Code (`claude`) inside isolated per-project folders (`~/aether_workspaces/<slug>`) with per-project `GEMINI.md` rules, file uploads, and one-tap ZIP export.
Android app, Kotlin + Jetpack Compose Material3. Source package `com.khereba1.app`, applicationId `com.khereba1.aether`.
minSdk 29, compileSdk 35. UI languages: Arabic + English. Developer name: Khereba1.
Permissions: `INTERNET` (loopback `127.0.0.1` cleartext only via `network_security_config.xml`) and `com.termux.permission.RUN_COMMAND` (auto-waking/bootstrapping Termux Ubuntu bridge).

## How it is built (IMPORTANT)
The owner has only a phone. There is NO local build. Code is pushed from Termux to GitHub and GitHub Actions
(.github/workflows/build.yml) builds the APK with Gradle 8.10.2 (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01).
You cannot compile: be careful and conservative. A mistake shows up only as a failed Actions run.
- Avoid Kotlin JVM signature clashes (`var x` with `private set` plus `fun setX(...)` breaks the build).
- Every new string needs an entry in every language file the app has (values/strings.xml, and values-ar/strings.xml).
- Do not bump library versions without checking that the exact version exists.

## Files (app/src/main/java/com/khereba1/app)
- `App.kt` — Application entry, `AppLog` crash/error logger, `LocaleHelper`
- `MainActivity.kt` — Activity wiring `RUN_COMMAND` permission, file picker, ZIP `FileProvider` share, and Compose screens
- `AetherViewModel.kt` — App state, Termux auto-wake/bootstrap, projects CRUD, `GEMINI.md` rules, streaming chat, ZIP export
- `data/Models.kt` — Data classes (`CliEngine`, `ProjectTemplate`, `ProjectInfo`, `ChatMessage`, `BridgeHealth`, `SetupStatus`, `AppSettings`)
- `data/BridgeScripts.kt` — Embedded zero-dependency Python 3 loopback server (`aether_bridge.py`) and Termux/Ubuntu bootstrap commands
- `data/TermuxBridge.kt` — Termux installation check, `RUN_COMMAND` permission check, background/interactive command launcher
- `data/BridgeClient.kt` — Loopback HTTP/NDJSON client (stdlib `HttpURLConnection`, no external network libs) + MediaStore ZIP export
- `data/LocalStore.kt` — SharedPreferences settings and per-project JSON chat history persistence
- `ui/Theme.kt` — Claude-inspired warm alabaster / warm charcoal Material 3 theme + Serif/Sans typography
- `ui/SetupScreen.kt` — Step-by-step interactive onboarding wizard checking Termux, permissions, Ubuntu bridge, and CLI
- `ui/ChatScreen.kt` — Claude-like chat UI, isolated projects sidebar, `GEMINI.md` rules editor, files viewer & ZIP export
- `ui/SettingsScreen.kt` — Multi-CLI switcher, model override, port, theme/language settings, and `AppLog` viewer

## REQUIRED answer format (the owner has only a phone + Termux)
1. Briefly say what you changed and why (plain language; the owner writes Egyptian Arabic mixed with English terms, reply in the same).
2. Deliver ONE zip containing only the changed/new files, complete (never snippets or diffs), with paths relative to the project root.
   Name it `fixN.zip` (fix1, fix2, ...). If you cannot create a zip, give each file in full in its own code block with its path, and say so.
3. Raise `versionCode` (+1) and `versionName` in `app/build.gradle.kts` for every functional change, and include that file.
4. Give the install command, ready to copy, with the real folder name:
   `cd ~/aether && unzip -o "$(ls -t ~/storage/downloads/fixN*.zip | head -1)" -d . && git add . && git commit -m "short message" && git push`
   (GitHub repo: Khereba1/Aether, APK: github.com/Khereba1/Aether/releases/tag/latest)
5. Say honestly that nothing was compiled or tested, and what to check first on the phone.
6. If the build fails, the owner sends lines containing `e: ` from the Actions log; fix only those.

## Signing and sharing
The stable signing key lives in GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).
Never print, commit or ask for them, and never ask for the backup folder.
`bash share_zip.sh` builds a clean `<folder>-for-AI.zip` (no keys, env files, build output or APKs) for sharing.
