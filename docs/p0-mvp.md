# glocalVision P0 MVP (Android-only)

Last Updated: 2026-02-26

## P0 Scope (must-have)

1. Single-screen agentic analysis flow
- User opens a current channel page
- Channel title row exposes a single `AI` button
- User enters a natural-language extraction request for the current channel
- App analyzes the current channel snapshot and returns a structured report

2. Lightweight retrieval and extraction
- No heavy RAG
- Hybrid extraction:
- local evidence prefilter (keyword + regex + token parsing)
- optional LLM extraction over the filtered evidence pack

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

6. Mobile role boundary (explicit)
- Mobile focuses on interactive agentic analysis and report reading
- Long-running monitoring/scheduling is deferred to desktop service phases

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

- Mobile manual channel/group browsing UI (Phase 1)
- Desktop mac service for 24h monitoring and scheduled jobs (Phase 2)
- Multi-group persistent watch tasks (desktop)
- LLM semantic trigger rules (desktop + mobile review)
- Daily scheduled reports pushed to mobile
- openindex/openviking adapters
- Exchange price verification for missing prices
