# Nekogram base track

Last updated: 2026-03-08

## Decision

`glocalVision` should stop treating the custom `android/` prototype as the real Telegram client path.

The fast path to a usable product is:

- base app: `Nekogram`
- integration style: small upstream overlay patches
- first differentiator: add a persistent `AI` entry in the real chat/channel screen

## Why Nekogram

- Already has production-grade Telegram login, session persistence, sync, chat list, and chat UI.
- Easier to modify for product experiments than continuing a from-scratch shell.
- `ChatActivity` provides a clean integration point for a channel-scoped AI action.

## First patch in this repo

Patch file:

- [0001-glocalvision-ai-entry.patch](/Users/pippo/Downloads/proj-tg/patches/nekogram/0001-glocalvision-ai-entry.patch)
- [0002-login-first.patch](/Users/pippo/Downloads/proj-tg/patches/nekogram/0002-login-first.patch)
- [0003-startup-safe-boot.patch](/Users/pippo/Downloads/proj-tg/patches/nekogram/0003-startup-safe-boot.patch)
- [0004-minimal-cold-boot.patch](/Users/pippo/Downloads/proj-tg/patches/nekogram/0004-minimal-cold-boot.patch)

Helper script:

- [apply_nekogram_patch.sh](/Users/pippo/Downloads/proj-tg/scripts/apply_nekogram_patch.sh)

Validated upstream commit:

- `Nekogram` commit `40caf3b2`

Validated behavior of this patch:

- Adds a top action-bar `AI` button to normal channel/group/topic chats.
- Hooks the button inside `ChatActivity`.
- Captures the currently loaded chat window on device.
- Builds three prompt variants:
  - channel analysis
  - trading signal extraction
  - desktop follow-up task
- Shows a preview dialog and copies the full prompt to clipboard.
- Skips the intro mascot page and opens `LoginActivity` directly for non-activated users, to avoid startup stalls reported on test devices.
- Defers `postInitApplication()` by one UI loop turn and adds a startup fallback that force-attaches login/main fragment if initial stack is still empty.
- Temporarily disables optional Nekogram cold-start work such as analytics, launcher fixups, push bootstrap, and billing startup until boot stability is confirmed.

This is intentionally narrow. It proves the real-client integration point before wiring direct LLM calls.

## Apply flow

```bash
git clone https://github.com/Nekogram/Nekogram.git
cd Nekogram
git checkout 40caf3b2
/Users/pippo/Downloads/proj-tg/scripts/apply_nekogram_patch.sh "$(pwd)"
```

Manual alternative:

```bash
git apply /Users/pippo/Downloads/proj-tg/patches/nekogram/0001-glocalvision-ai-entry.patch
```

CI note:

- This repo's Android Actions workflow builds against a cloned `Nekogram` checkout, applies the patch, and injects a stub `google-services.json` only for CI debug builds.
- The workflow also generates a CI-safe `tw.nekomimi.nekogram.Extra` so the upstream local-only config requirement does not block debug APK builds.

## Important scope boundary

Current patch does not yet:

- call a configured LLM provider directly
- send long-running work to the desktop service
- fetch deeper history beyond the currently loaded window
- verify claims on the web

Current patch does:

- place `AI` inside the real Telegram reading flow
- expose prompt generation from live in-app message data
- establish the exact code locations for the next iterations

## Upstream touch points

The patch modifies these upstream files:

- `TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java`
- `TMessagesProj/src/main/java/org/telegram/ui/GlocalVisionAiHelper.java`
- `TMessagesProj/src/main/res/values/strings.xml`
- `TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java`
- `TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java`

## Next steps

1. Replace clipboard-based prompt export with direct LLM provider settings + request execution.
2. Add a desktop handoff action that creates a durable task instead of a prompt.
3. Extend the helper from "loaded window only" to "fetch more history" for chosen channels.
4. Add claim verification and market-price enrichment as optional sub-agents.
