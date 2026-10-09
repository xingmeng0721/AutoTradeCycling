package com.liuyue.autoTradeCycling.common;

/**
 * 服务端批量搜索每 tick 的重掷强度，客户端与服务端共用。档位越高刷得越快，占用服务端 tick 也越多。
 */
public enum SearchSpeed {

    CAUTIOUS("保守", 3_000_000L, 150),
    BALANCED("均衡", 8_000_000L, 400),
    AGGRESSIVE("激进", 20_000_000L, 800),
    EXTREME("极速", 40_000_000L, 1_600),
    MAX("最快", 0L, 20_000);

    public static final SearchSpeed DEFAULT_SPEED = BALANCED;

    private final String label;
    private final long timeBudgetNanos;
    private final int maxAttemptsPerTick;

    SearchSpeed(String label, long timeBudgetNanos, int maxAttemptsPerTick) {
        this.label = label;
        this.timeBudgetNanos = timeBudgetNanos;
        this.maxAttemptsPerTick = maxAttemptsPerTick;
    }

    public String label() {
        return label;
    }

    public long timeBudgetNanos() {
        return timeBudgetNanos;
    }

    public int maxAttemptsPerTick() {
        return maxAttemptsPerTick;
    }

    public boolean unlimitedTime() {
        return timeBudgetNanos <= 0L;
    }

    public SearchSpeed next() {
        SearchSpeed[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static SearchSpeed byName(String name, SearchSpeed fallback) {
        for (SearchSpeed speed : values()) {
            if (speed.name().equals(name)) return speed;
        }
        return fallback;
    }
}
