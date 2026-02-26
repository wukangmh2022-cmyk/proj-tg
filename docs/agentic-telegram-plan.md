# Agentic Telegram plan (light retrieval)

## Implemented now

- Retrieval: `rg/grep` keyword scan from local channel exports
- Extraction: entry/exit signal + symbol + price
- Pairing: entry/exit matching per symbol
- Metrics: win rate, cumulative return, max drawdown
- Output: markdown table + LLM prompt block

## Data assumptions

- Channel export line includes at least one of signal keywords (`buy/sell/入场/出场/建仓/止盈/止损` etc.)
- Message line contains parsable date (`YYYY-MM-DD`) for window filtering
- Price exists in message (otherwise that trade cannot be priced)

## For real-world verification (next)

- Add exchange historical API verification (Binance/Bybit/OKX) for messages missing explicit exit prices
- Add timezone normalization and symbol mapping (e.g., BTC -> BTCUSDT)
- Add optional provider adapters:
  - openindex: external retrieval/lookup command integration
  - openviking: external retrieval/lookup command integration

## Why this approach first

- Fast and deterministic (easy to audit)
- Works with noisy real channel text before heavy RAG is introduced
- Good foundation for an agentic workflow with explicit evidence lines
