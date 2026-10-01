package ru.rooyzee.elytrixtrader.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TradeRegistry {

    private final Map<String, TradeConfig> trades = new LinkedHashMap<>();

    public void clear() {
        trades.clear();
    }

    public void register(TradeConfig trade) {
        if (trade != null && trade.id() != null) {
            trades.put(trade.id().toLowerCase(), trade);
        }
    }

    /** Убирает сделку из реестра (нужно при удалении трейда в редакторе). */
    public void unregister(String id) {
        if (id != null) {
            trades.remove(id.toLowerCase());
        }
    }

    public TradeConfig get(String id) {
        return id == null ? null : trades.get(id.toLowerCase());
    }

    public Collection<TradeConfig> all() {
        return trades.values();
    }

    public List<TradeConfig> resolve(List<String> ids) {
        List<TradeConfig> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String id : ids) {
            TradeConfig trade = get(id);
            if (trade == null) {
                continue;
            }
            if (!trade.enabled()) {
                continue;
            }
            result.add(trade);
        }
        return result;
    }

    public int size() {
        return trades.size();
    }
}
