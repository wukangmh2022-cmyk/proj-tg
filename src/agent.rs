use std::collections::{HashMap, VecDeque};
use std::path::Path;
use std::process::Command;

use crate::date::{extract_first_date, now_utc_date, SimpleDate};

const ENTRY_KEYWORDS: &[&str] = &[
    "buy", "entry", "long", "开多", "入场", "建仓", "买入", "加仓",
];
const EXIT_KEYWORDS: &[&str] = &[
    "sell",
    "exit",
    "close",
    "take profit",
    "stop loss",
    "平仓",
    "止盈",
    "止损",
    "卖出",
    "出场",
];

#[derive(Debug, Clone)]
pub struct UserIntent {
    pub raw_request: String,
    pub window_days: i64,
    pub include_drawdown: bool,
    pub include_cumulative_return: bool,
}

impl UserIntent {
    pub fn parse(raw_request: &str) -> Self {
        let req = raw_request.to_lowercase();
        let window_days =
            if req.contains("2年") || req.contains("两年") || req.contains("two years") {
                730
            } else if req.contains("1年") || req.contains("一年") || req.contains("1 year") {
                365
            } else if req.contains("半年") {
                180
            } else {
                730
            };

        let include_drawdown = req.contains("回撤") || req.contains("drawdown");
        let include_cumulative_return =
            req.contains("累计收益") || req.contains("cumulative") || req.contains("收益");

        Self {
            raw_request: raw_request.to_string(),
            window_days,
            include_drawdown,
            include_cumulative_return,
        }
    }

    pub fn cutoff_date(&self) -> Result<SimpleDate, String> {
        let today = now_utc_date()?;
        Ok(today.add_days(-self.window_days))
    }
}

#[derive(Debug, Clone)]
pub struct MessageHit {
    pub file: String,
    pub line: usize,
    pub date: Option<SimpleDate>,
    pub text: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SignalSide {
    Entry,
    Exit,
}

#[derive(Debug, Clone)]
pub struct TradeSignal {
    pub symbol: String,
    pub side: SignalSide,
    pub price: Option<f64>,
    pub date: Option<SimpleDate>,
    pub raw: String,
}

#[derive(Debug, Clone)]
pub struct ClosedTrade {
    pub symbol: String,
    pub entry_date: Option<SimpleDate>,
    pub entry_price: Option<f64>,
    pub exit_date: Option<SimpleDate>,
    pub exit_price: Option<f64>,
    pub return_pct: Option<f64>,
}

#[derive(Debug, Clone)]
pub struct TradeStats {
    pub total_closed: usize,
    pub priced_closed: usize,
    pub win_rate_pct: Option<f64>,
    pub cumulative_return_pct: Option<f64>,
    pub max_drawdown_pct: Option<f64>,
}

#[derive(Debug, Clone)]
pub struct AgentReport {
    pub intent: UserIntent,
    pub cutoff: SimpleDate,
    pub hits: Vec<MessageHit>,
    pub signals: Vec<TradeSignal>,
    pub closed_trades: Vec<ClosedTrade>,
    pub stats: TradeStats,
}

pub fn run_agent(channel_path: &str, user_request: &str) -> Result<AgentReport, String> {
    let intent = UserIntent::parse(user_request);
    let cutoff = intent.cutoff_date()?;

    let keywords = build_keywords();
    let mut hits = grep_hits(channel_path, &keywords)?;

    hits.retain(|hit| match hit.date {
        Some(date) => date >= cutoff,
        None => true,
    });

    let mut signals = Vec::new();
    for hit in &hits {
        if let Some(signal) = extract_signal(hit) {
            signals.push(signal);
        }
    }

    signals.sort_by(|a, b| a.date.cmp(&b.date));
    let closed_trades = close_trades(&signals);
    let stats = compute_stats(&closed_trades);

    Ok(AgentReport {
        intent,
        cutoff,
        hits,
        signals,
        closed_trades,
        stats,
    })
}

pub fn render_markdown_report(report: &AgentReport) -> String {
    let mut out = String::new();

    out.push_str("# glocalVision Agentic Report\n\n");
    out.push_str(&format!("- Request: {}\n", report.intent.raw_request));
    out.push_str(&format!(
        "- Time Window: last {} days (from {})\n",
        report.intent.window_days,
        report.cutoff.to_yyyy_mm_dd()
    ));
    out.push_str(&format!("- Raw Hits: {}\n", report.hits.len()));
    out.push_str(&format!("- Extracted Signals: {}\n", report.signals.len()));
    out.push_str(&format!(
        "- Closed Trades: {}\n\n",
        report.stats.total_closed
    ));

    out.push_str("## Performance Summary\n\n");
    out.push_str(&format!(
        "- Priced Closed Trades: {}\n",
        report.stats.priced_closed
    ));

    if report.intent.include_cumulative_return {
        match report.stats.cumulative_return_pct {
            Some(v) => out.push_str(&format!("- Cumulative Return: {:.2}%\n", v)),
            None => out.push_str("- Cumulative Return: N/A (missing price pairs)\n"),
        }
    }

    match report.stats.win_rate_pct {
        Some(v) => out.push_str(&format!("- Win Rate: {:.2}%\n", v)),
        None => out.push_str("- Win Rate: N/A\n"),
    }

    if report.intent.include_drawdown {
        match report.stats.max_drawdown_pct {
            Some(v) => out.push_str(&format!("- Max Drawdown: {:.2}%\n", v)),
            None => out.push_str("- Max Drawdown: N/A (insufficient priced trades)\n"),
        }
    }

    out.push_str("\n## Closed Trades Table\n\n");
    out.push_str("| # | Symbol | Entry Date | Entry Price | Exit Date | Exit Price | Return % |\n");
    out.push_str("|---|---|---|---:|---|---:|---:|\n");

    for (idx, t) in report.closed_trades.iter().enumerate() {
        let entry_date = t
            .entry_date
            .map(|d| d.to_yyyy_mm_dd())
            .unwrap_or_else(|| "N/A".to_string());
        let exit_date = t
            .exit_date
            .map(|d| d.to_yyyy_mm_dd())
            .unwrap_or_else(|| "N/A".to_string());

        let entry_price = t
            .entry_price
            .map(|v| format!("{:.6}", v))
            .unwrap_or_else(|| "N/A".to_string());
        let exit_price = t
            .exit_price
            .map(|v| format!("{:.6}", v))
            .unwrap_or_else(|| "N/A".to_string());
        let return_pct = t
            .return_pct
            .map(|v| format!("{:.2}", v))
            .unwrap_or_else(|| "N/A".to_string());

        out.push_str(&format!(
            "| {} | {} | {} | {} | {} | {} | {} |\n",
            idx + 1,
            t.symbol,
            entry_date,
            entry_price,
            exit_date,
            exit_price,
            return_pct
        ));
    }

    out.push_str("\n## Prompt Block (for LLM final reasoning)\n\n");
    out.push_str("```text\n");
    out.push_str(&build_prompt_block(report));
    out.push_str("\n```\n");

    out
}

fn build_prompt_block(report: &AgentReport) -> String {
    let mut p = String::new();
    p.push_str("你是交易审计助手。请根据以下频道信号数据做分析，不要补造不存在的数据。\n");
    p.push_str(&format!("用户需求: {}\n", report.intent.raw_request));
    p.push_str(&format!("统计窗口: 最近{}天\n", report.intent.window_days));
    p.push_str(&format!("命中消息数: {}\n", report.hits.len()));
    p.push_str(&format!("闭合交易数: {}\n", report.stats.total_closed));

    if let Some(v) = report.stats.cumulative_return_pct {
        p.push_str(&format!("累计收益: {:.2}%\n", v));
    }
    if let Some(v) = report.stats.max_drawdown_pct {
        p.push_str(&format!("最大回撤: {:.2}%\n", v));
    }

    p.push_str("\n证据样本(最多10条):\n");
    for signal in report.signals.iter().take(10) {
        p.push_str("- ");
        p.push_str(&signal.raw);
        p.push('\n');
    }

    p.push_str("\n请输出:\n");
    p.push_str("1) 该KOL信号风格与频率\n");
    p.push_str("2) 历史表现(胜率/累计收益/回撤)\n");
    p.push_str("3) 数据缺口与真实性风险\n");
    p.push_str("4) 表格化结论\n");
    p
}

fn build_keywords() -> Vec<String> {
    ENTRY_KEYWORDS
        .iter()
        .chain(EXIT_KEYWORDS.iter())
        .map(|s| s.to_string())
        .collect()
}

fn grep_hits(path: &str, keywords: &[String]) -> Result<Vec<MessageHit>, String> {
    if !Path::new(path).exists() {
        return Err(format!("channel data path not found: {path}"));
    }

    let pattern = keywords.join("|");

    let rg = Command::new("rg")
        .arg("-n")
        .arg("-i")
        .arg("--no-heading")
        .arg("-e")
        .arg(&pattern)
        .arg(path)
        .output();

    let output = match rg {
        Ok(ok) => ok,
        Err(_) => {
            return grep_hits_fallback(path, &pattern);
        }
    };

    if !output.status.success() && output.stdout.is_empty() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!("rg failed: {}", stderr.trim()));
    }

    Ok(parse_grep_output(
        &String::from_utf8_lossy(&output.stdout),
        path,
    ))
}

fn grep_hits_fallback(path: &str, pattern: &str) -> Result<Vec<MessageHit>, String> {
    let output = Command::new("grep")
        .arg("-R")
        .arg("-n")
        .arg("-i")
        .arg("-E")
        .arg(pattern)
        .arg(path)
        .output()
        .map_err(|e| format!("grep fallback failed: {e}"))?;

    if !output.status.success() && output.stdout.is_empty() {
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(format!("grep failed: {}", stderr.trim()));
    }

    Ok(parse_grep_output(
        &String::from_utf8_lossy(&output.stdout),
        path,
    ))
}

fn parse_grep_output(raw: &str, fallback_file: &str) -> Vec<MessageHit> {
    let mut hits = Vec::new();

    for line in raw.lines() {
        let Some(first_colon) = line.find(':') else {
            continue;
        };

        let head = line[..first_colon].trim();
        let tail = line[first_colon + 1..].trim();

        // Single-file rg output: `line_no:text...`
        if let Ok(line_no) = head.parse::<usize>() {
            hits.push(MessageHit {
                file: fallback_file.to_string(),
                line: line_no,
                date: extract_first_date(tail),
                text: tail.to_string(),
            });
            continue;
        }

        // Multi-file output: `file_path:line_no:text...`
        let Some(second_colon_rel) = tail.find(':') else {
            continue;
        };
        let line_part = tail[..second_colon_rel].trim();
        let text_part = tail[second_colon_rel + 1..].trim();

        if let Ok(line_no) = line_part.parse::<usize>() {
            hits.push(MessageHit {
                file: head.to_string(),
                line: line_no,
                date: extract_first_date(text_part),
                text: text_part.to_string(),
            });
        }
    }

    hits
}

fn extract_signal(hit: &MessageHit) -> Option<TradeSignal> {
    let lower = hit.text.to_lowercase();

    let side = if contains_any(&lower, ENTRY_KEYWORDS) {
        Some(SignalSide::Entry)
    } else if contains_any(&lower, EXIT_KEYWORDS) {
        Some(SignalSide::Exit)
    } else {
        None
    }?;

    let symbol = extract_symbol(&hit.text)?;
    let price = extract_price(&hit.text);

    Some(TradeSignal {
        symbol,
        side,
        price,
        date: hit.date,
        raw: format!("{}:{} {}", hit.file, hit.line, hit.text),
    })
}

fn contains_any(text: &str, patterns: &[&str]) -> bool {
    patterns.iter().any(|pattern| text.contains(pattern))
}

fn extract_symbol(text: &str) -> Option<String> {
    let tokens = split_tokens(text);

    for token in &tokens {
        let upper = token.to_uppercase();
        if is_pair_like(&upper) {
            return Some(upper);
        }
    }

    for token in split_tokens(text) {
        let upper = token.to_uppercase();
        if is_symbol_like(&upper) {
            return Some(upper);
        }
    }
    None
}

fn split_tokens(text: &str) -> Vec<String> {
    let mut tokens = Vec::new();
    let mut current = String::new();

    for ch in text.chars() {
        if ch.is_ascii_alphanumeric() || ch == '_' {
            current.push(ch);
        } else if !current.is_empty() {
            tokens.push(current.clone());
            current.clear();
        }
    }

    if !current.is_empty() {
        tokens.push(current);
    }

    tokens
}

fn is_symbol_like(token: &str) -> bool {
    if token.len() < 3 || token.len() > 16 {
        return false;
    }

    if token.chars().all(|c| c.is_ascii_digit()) {
        return false;
    }

    let ignored = [
        "BUY", "SELL", "ENTRY", "EXIT", "LONG", "SHORT", "STOP", "LOSS", "TAKE", "PROFIT", "KOL",
    ];

    if ignored.contains(&token) {
        return false;
    }

    token
        .chars()
        .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit() || c == '_')
}

fn is_pair_like(token: &str) -> bool {
    let suffixes = ["USDT", "USD", "BTC", "ETH", "PERP"];
    suffixes.iter().any(|suffix| {
        token.len() > suffix.len() + 1
            && token.ends_with(suffix)
            && token
                .chars()
                .all(|c| c.is_ascii_uppercase() || c.is_ascii_digit())
    })
}

fn extract_price(text: &str) -> Option<f64> {
    let mut candidates = Vec::new();

    for token in split_tokens(text) {
        if token.len() < 2 {
            continue;
        }

        if token.chars().all(|c| c.is_ascii_digit()) {
            if let Ok(v) = token.parse::<u32>() {
                if (1900..=2100).contains(&v) {
                    continue;
                }
                if token.len() <= 2 && v <= 60 {
                    // likely date/time fragments such as 01, 12, 20
                    continue;
                }
            }
        }

        if let Ok(value) = token.parse::<f64>() {
            if value > 0.0 && value < 10_000_000.0 {
                candidates.push(value);
            }
        }
    }

    candidates.last().copied()
}

fn close_trades(signals: &[TradeSignal]) -> Vec<ClosedTrade> {
    let mut open_by_symbol: HashMap<String, VecDeque<&TradeSignal>> = HashMap::new();
    let mut closed = Vec::new();

    for signal in signals {
        match signal.side {
            SignalSide::Entry => {
                open_by_symbol
                    .entry(signal.symbol.clone())
                    .or_default()
                    .push_back(signal);
            }
            SignalSide::Exit => {
                if let Some(queue) = open_by_symbol.get_mut(&signal.symbol) {
                    if let Some(entry) = queue.pop_front() {
                        let return_pct = match (entry.price, signal.price) {
                            (Some(in_price), Some(out_price)) if in_price > 0.0 => {
                                Some(((out_price - in_price) / in_price) * 100.0)
                            }
                            _ => None,
                        };

                        closed.push(ClosedTrade {
                            symbol: signal.symbol.clone(),
                            entry_date: entry.date,
                            entry_price: entry.price,
                            exit_date: signal.date,
                            exit_price: signal.price,
                            return_pct,
                        });
                    }
                }
            }
        }
    }

    closed
}

fn compute_stats(closed: &[ClosedTrade]) -> TradeStats {
    let priced: Vec<f64> = closed.iter().filter_map(|t| t.return_pct).collect();

    let win_rate_pct = if priced.is_empty() {
        None
    } else {
        let wins = priced.iter().filter(|v| **v > 0.0).count();
        Some((wins as f64 / priced.len() as f64) * 100.0)
    };

    let cumulative_return_pct = if priced.is_empty() {
        None
    } else {
        let mut equity = 1.0f64;
        for r in &priced {
            equity *= 1.0 + (r / 100.0);
        }
        Some((equity - 1.0) * 100.0)
    };

    let max_drawdown_pct = if priced.is_empty() {
        None
    } else {
        let mut equity = 1.0f64;
        let mut peak = 1.0f64;
        let mut max_dd = 0.0f64;

        for r in &priced {
            equity *= 1.0 + (r / 100.0);
            if equity > peak {
                peak = equity;
            }
            let drawdown = (peak - equity) / peak;
            if drawdown > max_dd {
                max_dd = drawdown;
            }
        }

        Some(max_dd * 100.0)
    };

    TradeStats {
        total_closed: closed.len(),
        priced_closed: priced.len(),
        win_rate_pct,
        cumulative_return_pct,
        max_drawdown_pct,
    }
}

#[cfg(test)]
mod tests {
    use super::{close_trades, compute_stats, SignalSide, TradeSignal};

    #[test]
    fn computes_cumulative_and_drawdown() {
        let signals = vec![
            TradeSignal {
                symbol: "BTCUSDT".to_string(),
                side: SignalSide::Entry,
                price: Some(100.0),
                date: None,
                raw: String::new(),
            },
            TradeSignal {
                symbol: "BTCUSDT".to_string(),
                side: SignalSide::Exit,
                price: Some(110.0),
                date: None,
                raw: String::new(),
            },
            TradeSignal {
                symbol: "ETHUSDT".to_string(),
                side: SignalSide::Entry,
                price: Some(100.0),
                date: None,
                raw: String::new(),
            },
            TradeSignal {
                symbol: "ETHUSDT".to_string(),
                side: SignalSide::Exit,
                price: Some(80.0),
                date: None,
                raw: String::new(),
            },
        ];

        let closed = close_trades(&signals);
        let stats = compute_stats(&closed);

        assert_eq!(stats.total_closed, 2);
        assert_eq!(stats.priced_closed, 2);

        let cum = stats.cumulative_return_pct.expect("cum");
        assert!((cum + 12.0).abs() < 0.001);

        let dd = stats.max_drawdown_pct.expect("dd");
        assert!((dd - 20.0).abs() < 0.001);
    }
}
