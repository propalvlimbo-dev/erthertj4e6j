package ru.rooyzee.elytrixtrader.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtrader.model.IconConfig;
import ru.rooyzee.elytrixtrader.util.Cfg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GuiConfig {

    private final String title;
    private final int size;
    private final IconConfig filler;
    private final int startSlot;
    private final int endSlot;
    private final List<Integer> tradeSlots;
    private final Map<String, IconConfig> buttons;
    private final List<Integer> allowedAmounts;
    private final int defaultAmount;
    private final String tradeDisplayName;
    private final List<String> tradeLore;
    private final boolean overrideTradeLore;
    private final boolean tradeGlow;
    private final List<Decoration> decorations;

    public GuiConfig(YamlConfiguration config) {
        title = Cfg.str(config, "title", "&#F8BEFBТорговец &8» &#F8BEFB{trader}");
        size = Cfg.clamp(Cfg.num(config, "size", 54), 9, 54);
        filler = IconConfig.from(config == null ? null : config.getConfigurationSection("filler"), Material.GRAY_STAINED_GLASS_PANE,
                1, " ", new ArrayList<>());
        ConfigurationSection area = config == null ? null : config.getConfigurationSection("trade-area");
        int computedEnd = size - 10;
        startSlot = Cfg.clamp(Cfg.num(area, "start-slot", 18), 0, Math.max(0, size - 1));
        endSlot = Cfg.clamp(Cfg.num(area, "end-slot", computedEnd), startSlot, size - 1);
        tradeSlots = new ArrayList<>();
        if (area != null && area.contains("slots")) {
            for (Object value : area.getList("slots", new ArrayList<>())) {
                if (value instanceof Number) {
                    int slot = ((Number) value).intValue();
                    if (slot >= 0 && slot < size && !tradeSlots.contains(slot)) {
                        tradeSlots.add(slot);
                    }
                }
            }
        }
        buttons = new LinkedHashMap<>();
        ConfigurationSection section = config == null ? null : config.getConfigurationSection("buttons");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                buttons.put(key.toLowerCase(), IconConfig.from(section.getConfigurationSection(key), Material.STONE));
            }
        }
        decorations = new ArrayList<>();
        ConfigurationSection deco = config == null ? null : config.getConfigurationSection("decorations");
        if (deco != null) {
            for (String key : deco.getKeys(false)) {
                ConfigurationSection entry = deco.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                IconConfig icon = IconConfig.from(entry, Material.BLACK_STAINED_GLASS_PANE);
                List<Integer> slots = new ArrayList<>();
                for (Object value : entry.getList("slots", new ArrayList<>())) {
                    if (value instanceof Number) {
                        int slot = ((Number) value).intValue();
                        if (slot >= 0 && slot < 54 && !slots.contains(slot)) {
                            slots.add(slot);
                        }
                    }
                }
                decorations.add(new Decoration(icon, slots));
            }
        }
        allowedAmounts = new ArrayList<>();
        ConfigurationSection amount = config == null ? null : config.getConfigurationSection("amount");
        for (Integer value : readAmounts(amount)) {
            if (value != null && value > 0 && !allowedAmounts.contains(value)) {
                allowedAmounts.add(value);
            }
        }
        if (allowedAmounts.isEmpty()) {
            allowedAmounts.add(1);
            allowedAmounts.add(5);
            allowedAmounts.add(16);
            allowedAmounts.add(32);
            allowedAmounts.add(64);
        }
        int defAmount = Cfg.num(amount, "default", 1);
        if (!allowedAmounts.contains(defAmount)) {
            defAmount = allowedAmounts.get(0);
        }
        defaultAmount = defAmount;
        ConfigurationSection icons = config == null ? null : config.getConfigurationSection("icons");
        ConfigurationSection trade = icons == null ? null : icons.getConfigurationSection("trade");
        tradeDisplayName = Cfg.str(trade, "display-name", "&#F8BEFB&l{trade}");
        tradeLore = Cfg.list(trade, "lore");
        overrideTradeLore = Cfg.bool(trade, "override-trade-lore", true);
        tradeGlow = Cfg.bool(trade, "glow", false);
    }

    private static List<Integer> readAmounts(ConfigurationSection section) {
        List<Integer> result = new ArrayList<>();
        if (section == null || !section.contains("allowed")) {
            return result;
        }
        for (Object entry : section.getList("allowed", new ArrayList<>())) {
            if (entry instanceof Number) {
                result.add(((Number) entry).intValue());
            } else if (entry instanceof String) {
                try {
                    result.add(Integer.parseInt(((String) entry).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    public String title() {
        return title;
    }

    public int size() {
        return size;
    }

    public IconConfig filler() {
        return filler;
    }

    public int startSlot() {
        return startSlot;
    }

    public int endSlot() {
        return endSlot;
    }

    public int pageSize() {
        return tradeSlots.isEmpty() ? Math.max(1, endSlot - startSlot + 1) : tradeSlots.size();
    }

    public List<Integer> tradeSlots() {
        return tradeSlots;
    }

    public Map<String, IconConfig> buttons() {
        return buttons;
    }

    public IconConfig button(String key) {
        return buttons.get(key.toLowerCase());
    }

    public int buttonSlot(String key, int def) {
        IconConfig config = button(key);
        if (config == null || config.slot() < 0 || config.slot() >= size) {
            return -1;
        }
        return config.slot();
    }

    public boolean hasButton(String key) {
        return button(key) != null;
    }

    public List<Integer> allowedAmounts() {
        return allowedAmounts;
    }

    public int defaultAmount() {
        return defaultAmount;
    }

    public int nextAmount(int current, int step) {
        int index = allowedAmounts.indexOf(current);
        if (index < 0) {
            index = 0;
        }
        int target = Math.max(0, Math.min(allowedAmounts.size() - 1, index + step));
        return allowedAmounts.get(target);
    }

    public int bulkAmount() {
        return allowedAmounts.size() > 1 ? allowedAmounts.get(1) : 5;
    }

    public String tradeDisplayName() {
        return tradeDisplayName;
    }

    public List<String> tradeLore() {
        return tradeLore;
    }

    public boolean overrideTradeLore() {
        return overrideTradeLore;
    }

    public boolean tradeGlow() {
        return tradeGlow;
    }

    public List<Decoration> decorations() {
        return decorations;
    }

    public static final class Decoration {
        private final IconConfig icon;
        private final List<Integer> slots;

        Decoration(IconConfig icon, List<Integer> slots) {
            this.icon = icon;
            this.slots = slots;
        }

        public IconConfig icon() {
            return icon;
        }

        public List<Integer> slots() {
            return slots;
        }
    }

    public IconConfig safeButton(String key, Material fallbackMaterial, int fallbackSlot, String fallbackName, List<String> fallbackLore) {
        IconConfig stored = button(key);
        if (stored != null) {
            return stored;
        }
        return new IconConfig(fallbackMaterial, 1, false, 0, fallbackName, fallbackLore, null, null, fallbackSlot);
    }

    public boolean amountEnabled() {
        return allowedAmounts.size() > 1;
    }
}
