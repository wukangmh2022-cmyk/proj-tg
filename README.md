# glocalVision

`glocalVision` is a custom Telegram client foundation in Rust.

## Android P0 MVP

Android-native MVP is now available under `android/` (Android-only scope).

- Channel page pattern: current channel title with a single `AI` action button
- Agent technique: local evidence prefilter + optional OpenAI-compatible LLM extraction
- Login profile screen (API ID / API Hash / phone local config)
- LLM source config (base URL / API key / model) + endpoint test (`/v1/models`)
- Manual channel message view with keyword filtering
- Input: user query + channel messages (plain text)
- Process: signal extraction (entry/exit), trade pairing, stats
- Output: markdown-like report with cumulative return, drawdown, and prompt block
- Positioning: mobile-first agentic interaction; long-running monitoring is planned for desktop service phases

Build APK locally (if Android SDK is installed):

```bash
cd android
./gradlew assembleDebug
```

GitHub Actions build:

- Workflow: `.github/workflows/android-build.yml`
- Output artifact: `glocalvision-app-debug` (`app-debug.apk`)
- CI settings intentionally aligned with your reference project `proj1`:
  - `actions/setup-java@v4` with `distribution: zulu`, `java-version: 21`
  - debug keystore generation
  - `./gradlew assembleDebug`

## Selected Library

- Core: [TDLib](https://github.com/tdlib/td)
- Rust wrapper: [tdlib-rs](https://github.com/FedericoBruzzone/tdlib-rs)

Why this choice:

- TDLib is Telegram's official cross-platform client library and explicitly supports Android/iOS-class targets.
- Rust wrapper keeps core logic in a safer language while still using official Telegram client primitives.

## Quick Start

1. Create config:

```bash
cargo run -- init
```

2. Set your Telegram credentials in `glocalvision.toml` (`api_id`, `api_hash`).

3. Run starter in TDLib mode:

```bash
cargo run --features tdlib-download -- run
```

Alternative modes:

- `tdlib-local` for locally built TDLib
- `tdlib-pkg-config` for pkg-config linking

When network is available, add the Rust wrapper and link mode in `Cargo.toml`:

```toml
[dependencies]
tdlib = { package = "tdlib-rs", version = "1.2", optional = true, default-features = false }

[features]
tdlib-download = ["dep:tdlib", "tdlib/download-tdlib"]
tdlib-local = ["dep:tdlib", "tdlib/local-tdlib"]
tdlib-pkg-config = ["dep:tdlib", "tdlib/pkg-config"]
```

## Agentic Telegram MVP (grep-first)

This project includes a lightweight agent command for your scenario:

- Parse user request (e.g., "collect entry/exit signals in last 2 years")
- Retrieve signal lines by `rg/grep` from channel exports
- Extract entry/exit signals and pair trades
- Compute win rate, cumulative return, and max drawdown
- Output markdown table + an LLM-ready prompt block

Run:

```bash
cargo run -- agent <channel_data_path> "<user request>"
```

Example:

```bash
cargo run -- agent examples/sample_channel.txt "XXX投资频道下，帮我搜集2年内入场出场信号，统计该KOL的历史数据和累计收益、过程回撤整理为表格给我"
```

## Current Scope

- App bootstrap (`src/main.rs`)
- Config template/loader (`src/config.rs`)
- Telegram backend abstraction (`src/telegram.rs`)
- Agent pipeline (`src/agent.rs` + `src/date.rs`)
- Android P0 MVP app (`android/`)
- Full requirements backlog (`docs/glocalvision-requirements.md`)

## Next Customization (when you define product features)

- TDLib real message pull and channel snapshots
- openindex/openviking retriever adapter (optional provider)
- price verification via exchange API (for missing explicit entry/exit prices)
- mobile bridge layer (Flutter/React Native/Swift/Kotlin)
