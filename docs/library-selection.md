# Telegram library comparison (mobile + Rust)

## Recommended

- TDLib + Rust wrapper (`tdlib-rs`)

Reason:

- Official Telegram client library for cross-platform clients.
- Rust keeps custom modules lower-risk (memory/thread safety).
- Better long-term compatibility for a full client than Bot-only libraries.

## Not selected

- `teloxide`: excellent for Bot API, not for full Telegram user client API.
- `grammers`: pure Rust MTProto option, but ecosystem direction appears less stable recently; use only if you want pure-Rust MTProto without TDLib.
