# Aether — AI Handoff

## App Identity
- **App name:** Aether
- **Source package (`namespace`):** `com.khereba1.app` (fixed per STANDARD.md)
- **Installed `applicationId`:** `com.khereba1.aether`
- **Current version:** `1.2.2` (`versionCode = 7`)
- **GitHub repo:** `Khereba1/Aether`
- **Build pipeline:** GitHub Actions (`.github/workflows/build.yml`, JDK 17, Gradle 8.10.2, AGP 8.7.3, Kotlin 2.0.21, Compose BOM `2024.10.01`).

## Architecture
- **UI (Jetpack Compose M3):** Warm Claude-inspired editorial theme (`ui/Theme.kt`) with unified `Noto Sans Arabic` typography across all 15 Material 3 text slots, English LTR chrome, and automatic `TextDirection.Content` bidi for Arabic/English chat.
- **Markdown Renderer (`ui/MarkdownText.kt`):** Renders fenced code blocks with Copy button, headings, bullets, blockquotes, horizontal dividers, `**bold**`, `*italic*`, `` `inline code` ``, and `[label](url)` links without raw symbols.
- **Local Ubuntu Bridge (`data/BridgeScripts.kt` v1.2.0):** Embedded zero-dependency Python 3 server running on `127.0.0.1:8765` inside Termux `proot-distro` Ubuntu. Auto-upgrades on app launch when bridge version changes.
  - `GET /health` — reports Ubuntu, Python, `bridge_version`, installed CLIs (`agy`, `gemini`, `claude`), workspace path.
  - `GET /models` — runs `agy models` dynamically to list available models for the signed-in account.
  - `GET/POST/DELETE /projects` — isolated per-project workspaces under `~/aether_workspaces/<slug>`.
  - `GET/POST /projects/<slug>/rules` — reads/writes per-project `GEMINI.md`.
  - `POST /projects/<slug>/reset` — clears saved `conversation_id` for fresh context.
  - `GET /projects/<slug>/files`, `GET /projects/<slug>/file?path=...`, `GET /projects/<slug>/zip`, `POST /projects/<slug>/upload`.
  - `POST /chat` — streams NDJSON from `agy -p ... --output-format stream-json` with persistent `--conversation <id>`, `--model <slug>`, and `--effort <low|medium|high>`, plus automatic stale-session fallback.
  - `POST /chat/cancel` — terminates the active CLI subprocess for a project.
- **Chat UX (`ui/ChatScreen.kt`):**
  - Tappable **Model & Effort** pill in the TopAppBar for instant switching without leaving Aether.
  - **Stop** button in composer to abort active generation.
  - **Smart File Cards**: single changed file shows **Open** + **Share**; multiple files show **ZIP**.
  - Confirmation dialog before deleting a project.

## Version History
- `1.0.0` (1): Initial release.
- `1.0.1` (2): Fixed `Icons.AutoMirrored.Rounded` casing.
- `1.0.2` (3): Fixed outer Termux `nohup proot-distro` bridge daemon launch + manual command copy.
- `1.1.0` (4): Unified font, English LTR UI, Markdown renderer, single-file `/file` download, permanent `agy` `stream-json` parser.
- `1.2.0` (5): Live in-app Model & Effort picker (`GET /models`), persistent multi-turn `--conversation` context + New Session reset, Stop generation button, direct Open + Share for single files, delete project confirmation, unified 15-slot typography, fixed double Scaffold insets, automatic bridge self-upgrade.
- `1.2.1` (6): Fixed `pkill -f aether_bridge.py` self-terminating the Termux bootstrap script; switched to PID file + bracketed regex; fixed Setup Step 2 permission indicator.
- `1.2.2` (7): Added lightweight 180-byte `termuxFastWakeCommand` with `setsid` so background auto-wake succeeds reliably on app launch; fixed `proot-distro` Ubuntu existence check and escaped `\$(cat ~/aether/bridge.pid)`.
