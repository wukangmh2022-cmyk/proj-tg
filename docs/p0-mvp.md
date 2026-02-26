# glocalVision P0 MVP (Android-only)

Last Updated: 2026-02-26

## P0 Scope (must-have)

1. Single-screen agentic analysis flow
- User enters a natural-language request
- User pastes channel/group messages (plain text)
- App returns structured report

2. Lightweight retrieval and extraction
- No heavy RAG
- Rule-based extraction (keyword + regex + token parsing)

3. Trading signal stats
- Entry/exit signal detection
- Trade pairing (FIFO by symbol)
- Metrics: win rate, cumulative return, max drawdown

4. Evidence-first output
- Summary + closed trades table + prompt block
- Keep evidence lines for audit

5. Android CI build
- GitHub Actions builds debug APK
- Artifact upload for testing

## Implemented in this repository

- Android app module: `android/`
- Main UI: `android/app/src/main/res/layout/activity_main.xml`
- MVP logic:
  - `android/app/src/main/java/com/glocalvision/app/MainActivity.java`
  - `android/app/src/main/java/com/glocalvision/app/SignalAnalyzer.java`
- CI workflow: `.github/workflows/android-build.yml`

## Reference alignment with proj1

The CI setup explicitly follows your reference project style:

- JDK: `zulu` / `21`
- Gradle cache enabled via `actions/setup-java`
- Debug keystore generation step
- Build command: `./gradlew assembleDebug`
- APK artifact upload

## Out of P0 (next phases)

- Live Telegram incremental sync via TDLib
- Multi-group persistent watch tasks
- LLM semantic trigger rules
- Daily scheduled reports
- openindex/openviking adapters
- Exchange price verification for missing prices
