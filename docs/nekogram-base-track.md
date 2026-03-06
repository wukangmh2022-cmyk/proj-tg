# Nekogram base track

Last updated: 2026-03-06

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

## Next steps

1. Replace clipboard-based prompt export with direct LLM provider settings + request execution.
2. Add a desktop handoff action that creates a durable task instead of a prompt.
3. Extend the helper from "loaded window only" to "fetch more history" for chosen channels.
4. Add claim verification and market-price enrichment as optional sub-agents.
