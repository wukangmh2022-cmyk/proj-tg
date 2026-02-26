package com.glocalvision.app;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SignalAnalyzer {

    private static final String[] ENTRY_KEYWORDS = new String[]{
            "buy", "entry", "long", "入场", "建仓", "买入", "加仓", "开多"
    };

    private static final String[] EXIT_KEYWORDS = new String[]{
            "sell", "exit", "close", "take profit", "stop loss", "出场", "止盈", "止损", "卖出", "平仓"
    };

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4})[-/](\\d{2})[-/](\\d{2})");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d+(?:\\.\\d+)?$");

    private SignalAnalyzer() {
    }

    public static String analyze(String rawMessages, String userRequest) {
        String safeMessages = rawMessages == null ? "" : rawMessages;
        String safeRequest = userRequest == null ? "" : userRequest;

        int windowDays = parseWindowDays(safeRequest);
        long cutoffMillis = utcMidnightDaysAgo(windowDays);

        String[] lines = safeMessages.split("\\r?\\n");
        List<MessageHit> hits = new ArrayList<>();
        List<TradeSignal> signals = new ArrayList<>();

        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            if (line == null) {
                continue;
            }
            String text = line.trim();
            if (text.isEmpty()) {
                continue;
            }

            SignalSide side = detectSide(text);
            if (side == null) {
                continue;
            }

            ParsedDate parsedDate = extractDate(text);
            if (parsedDate != null && parsedDate.utcMillis < cutoffMillis) {
                continue;
            }

            hits.add(new MessageHit(lineNo, text, parsedDate));

            String symbol = extractSymbol(text);
            if (symbol == null) {
                continue;
            }
            Double price = extractPrice(text);

            TradeSignal signal = new TradeSignal();
            signal.symbol = symbol;
            signal.side = side;
            signal.price = price;
            signal.date = parsedDate;
            signal.raw = "line " + lineNo + ": " + text;
            signals.add(signal);
        }

        Collections.sort(signals, new Comparator<TradeSignal>() {
            @Override
            public int compare(TradeSignal left, TradeSignal right) {
                long l = left.date == null ? Long.MIN_VALUE : left.date.utcMillis;
                long r = right.date == null ? Long.MIN_VALUE : right.date.utcMillis;
                return Long.compare(l, r);
            }
        });

        List<ClosedTrade> closedTrades = pairTrades(signals);
        Stats stats = computeStats(closedTrades);

        return renderMarkdown(safeRequest, windowDays, cutoffMillis, hits, signals, closedTrades, stats);
    }

    private static int parseWindowDays(String request) {
        String lower = request.toLowerCase(Locale.ROOT);
        if (lower.contains("2年") || lower.contains("两年") || lower.contains("two years")) {
            return 730;
        }
        if (lower.contains("1年") || lower.contains("一年") || lower.contains("1 year")) {
            return 365;
        }
        if (lower.contains("半年")) {
            return 180;
        }
        return 730;
    }

    private static long utcMidnightDaysAgo(int daysAgo) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DATE, -daysAgo);
        return cal.getTimeInMillis();
    }

    private static SignalSide detectSide(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, ENTRY_KEYWORDS)) {
            return SignalSide.ENTRY;
        }
        if (containsAny(lower, EXIT_KEYWORDS)) {
            return SignalSide.EXIT;
        }
        return null;
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static ParsedDate extractDate(String text) {
        Matcher m = DATE_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }

        try {
            int y = Integer.parseInt(m.group(1));
            int mo = Integer.parseInt(m.group(2));
            int d = Integer.parseInt(m.group(3));

            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            cal.setLenient(false);
            cal.set(Calendar.YEAR, y);
            cal.set(Calendar.MONTH, mo - 1);
            cal.set(Calendar.DAY_OF_MONTH, d);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long millis = cal.getTimeInMillis();

            ParsedDate date = new ParsedDate();
            date.year = y;
            date.month = mo;
            date.day = d;
            date.utcMillis = millis;
            return date;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractSymbol(String text) {
        String[] tokens = text.split("[^A-Za-z0-9_]+");
        for (String token : tokens) {
            String upper = token.toUpperCase(Locale.ROOT);
            if (isPairLike(upper)) {
                return upper;
            }
        }

        for (String token : tokens) {
            String upper = token.toUpperCase(Locale.ROOT);
            if (isSymbolLike(upper)) {
                return upper;
            }
        }
        return null;
    }

    private static boolean isPairLike(String token) {
        if (token.length() < 6 || token.length() > 16) {
            return false;
        }

        String[] suffixes = new String[]{"USDT", "USD", "BTC", "ETH", "PERP"};
        for (String suffix : suffixes) {
            if (token.endsWith(suffix) && token.length() > suffix.length() + 1) {
                return token.matches("[A-Z0-9_]+") && !token.matches("\\d+");
            }
        }
        return false;
    }

    private static boolean isSymbolLike(String token) {
        if (token.length() < 3 || token.length() > 16) {
            return false;
        }
        if (token.matches("\\d+")) {
            return false;
        }

        Set<String> ignored = new HashSet<>();
        Collections.addAll(ignored,
                "BUY", "SELL", "ENTRY", "EXIT", "LONG", "SHORT", "STOP", "LOSS", "TAKE", "PROFIT",
                "KOL", "AND", "THE", "FOR", "WITH", "LINE");

        if (ignored.contains(token)) {
            return false;
        }

        return token.matches("[A-Z0-9_]+") && token.chars().anyMatch(Character::isLetter);
    }

    private static Double extractPrice(String text) {
        String[] tokens = text.split("[^0-9.]+");
        List<Double> candidates = new ArrayList<>();

        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            if (!NUMBER_PATTERN.matcher(token).matches()) {
                continue;
            }
            if (token.chars().filter(c -> c == '.').count() > 1) {
                continue;
            }

            try {
                if (!token.contains(".")) {
                    int raw = Integer.parseInt(token);
                    if (raw >= 1900 && raw <= 2100) {
                        continue;
                    }
                    if (token.length() <= 2 && raw <= 60) {
                        continue;
                    }
                }

                double v = Double.parseDouble(token);
                if (v > 0.0 && v < 10_000_000.0) {
                    candidates.add(v);
                }
            } catch (NumberFormatException ignored) {
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        return candidates.get(candidates.size() - 1);
    }

    private static List<ClosedTrade> pairTrades(List<TradeSignal> signals) {
        Map<String, ArrayDeque<TradeSignal>> open = new HashMap<>();
        List<ClosedTrade> closed = new ArrayList<>();

        for (TradeSignal signal : signals) {
            if (signal.side == SignalSide.ENTRY) {
                open.computeIfAbsent(signal.symbol, k -> new ArrayDeque<>()).addLast(signal);
                continue;
            }

            ArrayDeque<TradeSignal> queue = open.get(signal.symbol);
            if (queue == null || queue.isEmpty()) {
                continue;
            }

            TradeSignal entry = queue.removeFirst();
            ClosedTrade trade = new ClosedTrade();
            trade.symbol = signal.symbol;
            trade.entryDate = entry.date;
            trade.entryPrice = entry.price;
            trade.exitDate = signal.date;
            trade.exitPrice = signal.price;

            if (entry.price != null && signal.price != null && entry.price > 0.0) {
                trade.returnPct = ((signal.price - entry.price) / entry.price) * 100.0;
            }

            closed.add(trade);
        }

        return closed;
    }

    private static Stats computeStats(List<ClosedTrade> trades) {
        List<Double> pricedReturns = new ArrayList<>();
        int wins = 0;

        for (ClosedTrade trade : trades) {
            if (trade.returnPct != null) {
                pricedReturns.add(trade.returnPct);
                if (trade.returnPct > 0.0) {
                    wins++;
                }
            }
        }

        Stats stats = new Stats();
        stats.closedTrades = trades.size();
        stats.pricedTrades = pricedReturns.size();

        if (pricedReturns.isEmpty()) {
            return stats;
        }

        stats.winRatePct = (wins * 100.0) / pricedReturns.size();

        double equity = 1.0;
        for (double ret : pricedReturns) {
            equity *= (1.0 + ret / 100.0);
        }
        stats.cumulativeReturnPct = (equity - 1.0) * 100.0;

        double peak = 1.0;
        double current = 1.0;
        double maxDd = 0.0;
        for (double ret : pricedReturns) {
            current *= (1.0 + ret / 100.0);
            if (current > peak) {
                peak = current;
            }
            double dd = (peak - current) / peak;
            if (dd > maxDd) {
                maxDd = dd;
            }
        }
        stats.maxDrawdownPct = maxDd * 100.0;

        return stats;
    }

    private static String renderMarkdown(
            String request,
            int windowDays,
            long cutoffMillis,
            List<MessageHit> hits,
            List<TradeSignal> signals,
            List<ClosedTrade> trades,
            Stats stats
    ) {
        StringBuilder out = new StringBuilder();

        out.append("# glocalVision Agentic Report\\n\\n");
        out.append("- Request: ").append(request).append("\\n");
        out.append("- Time Window: last ").append(windowDays).append(" days (from ")
                .append(formatDate(cutoffMillis)).append(")\\n");
        out.append("- Raw Hits: ").append(hits.size()).append("\\n");
        out.append("- Extracted Signals: ").append(signals.size()).append("\\n");
        out.append("- Closed Trades: ").append(trades.size()).append("\\n\\n");

        out.append("## Performance Summary\\n\\n");
        out.append("- Priced Closed Trades: ").append(stats.pricedTrades).append("\\n");
        out.append("- Cumulative Return: ").append(formatPercent(stats.cumulativeReturnPct)).append("\\n");
        out.append("- Win Rate: ").append(formatPercent(stats.winRatePct)).append("\\n");
        out.append("- Max Drawdown: ").append(formatPercent(stats.maxDrawdownPct)).append("\\n\\n");

        out.append("## Closed Trades Table\\n\\n");
        out.append("| # | Symbol | Entry Date | Entry Price | Exit Date | Exit Price | Return % |\\n");
        out.append("|---|---|---|---:|---|---:|---:|\\n");

        for (int i = 0; i < trades.size(); i++) {
            ClosedTrade t = trades.get(i);
            out.append("| ")
                    .append(i + 1).append(" | ")
                    .append(t.symbol).append(" | ")
                    .append(formatDate(t.entryDate)).append(" | ")
                    .append(formatDouble(t.entryPrice)).append(" | ")
                    .append(formatDate(t.exitDate)).append(" | ")
                    .append(formatDouble(t.exitPrice)).append(" | ")
                    .append(formatPercent(t.returnPct)).append(" |\\n");
        }

        out.append("\\n## Prompt Block (for LLM final reasoning)\\n\\n");
        out.append("```text\\n");
        out.append("你是交易审计助手。请根据以下频道信号数据做分析，不要补造不存在的数据。\\n");
        out.append("用户需求: ").append(request).append("\\n");
        out.append("统计窗口: 最近").append(windowDays).append("天\\n");
        out.append("命中消息数: ").append(hits.size()).append("\\n");
        out.append("闭合交易数: ").append(trades.size()).append("\\n");
        out.append("累计收益: ").append(formatPercent(stats.cumulativeReturnPct)).append("\\n");
        out.append("最大回撤: ").append(formatPercent(stats.maxDrawdownPct)).append("\\n\\n");
        out.append("证据样本(最多10条):\\n");

        int limit = Math.min(10, signals.size());
        for (int i = 0; i < limit; i++) {
            out.append("- ").append(signals.get(i).raw).append("\\n");
        }

        out.append("\\n请输出:\\n");
        out.append("1) 该KOL信号风格与频率\\n");
        out.append("2) 历史表现(胜率/累计收益/回撤)\\n");
        out.append("3) 数据缺口与真实性风险\\n");
        out.append("4) 表格化结论\\n");
        out.append("```\\n");

        return out.toString();
    }

    private static String formatPercent(Double v) {
        if (v == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%.2f%%", v);
    }

    private static String formatDouble(Double v) {
        if (v == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%.6f", v);
    }

    private static String formatDate(ParsedDate date) {
        if (date == null) {
            return "N/A";
        }
        return String.format(Locale.US, "%04d-%02d-%02d", date.year, date.month, date.day);
    }

    private static String formatDate(long utcMillis) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(utcMillis);
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
    }

    private enum SignalSide {
        ENTRY,
        EXIT
    }

    private static final class ParsedDate {
        int year;
        int month;
        int day;
        long utcMillis;
    }

    private static final class MessageHit {
        final int lineNo;
        final String text;
        final ParsedDate date;

        MessageHit(int lineNo, String text, ParsedDate date) {
            this.lineNo = lineNo;
            this.text = text;
            this.date = date;
        }
    }

    private static final class TradeSignal {
        String symbol;
        SignalSide side;
        Double price;
        ParsedDate date;
        String raw;
    }

    private static final class ClosedTrade {
        String symbol;
        ParsedDate entryDate;
        Double entryPrice;
        ParsedDate exitDate;
        Double exitPrice;
        Double returnPct;
    }

    private static final class Stats {
        int closedTrades;
        int pricedTrades;
        Double cumulativeReturnPct;
        Double winRatePct;
        Double maxDrawdownPct;
    }
}
