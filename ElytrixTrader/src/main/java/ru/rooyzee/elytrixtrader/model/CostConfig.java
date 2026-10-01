package ru.rooyzee.elytrixtrader.model;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixtrader.util.Cfg;
import ru.rooyzee.elytrixtrader.util.ColorUtil;

import java.util.Locale;

public class CostConfig {

    public enum Type {
        EXPERIENCE_LEVELS,
        EXPERIENCE_POINTS,
        VAULT,
        COINS,
        ITEM
    }

    private final Type type;
    private final double amount;
    private final Material material;
    private final String placeholder;
    /**
     * Точный предмет-оплата (с мета: зелье, зачарованная книга, переименованный предмет).
     * Если задан — засчитываются только полностью такие же предметы (ItemStack.isSimilar),
     * а не «любой предмет того же материала».
     */
    private final ItemStack rawItem;

    public CostConfig(Type type, double amount, Material material, String placeholder) {
        this(type, amount, material, placeholder, null);
    }

    public CostConfig(Type type, double amount, Material material, String placeholder, ItemStack rawItem) {
        this.type = type;
        this.amount = amount;
        this.material = material;
        this.placeholder = placeholder;
        this.rawItem = rawItem == null || rawItem.getType() == Material.AIR ? null : rawItem.clone();
    }

    public static CostConfig from(ConfigurationSection section) {
        String rawType = Cfg.str(section, "type", "experience_levels").trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        Type type;
        switch (rawType) {
            case "EXP_LEVELS":
            case "EXP":
            case "XP":
            case "LEVELS":
            case "EXPERIENCE":
                type = Type.EXPERIENCE_LEVELS;
                break;
            case "EXP_POINTS":
            case "XP_POINTS":
            case "EXPERIENCE_POINTS":
                type = Type.EXPERIENCE_POINTS;
                break;
            case "MONEY":
            case "ECONOMY":
            case "BALANCE":
                type = Type.VAULT;
                break;
            case "REALS":
            case "POINTS":
            case "KOINS":
                type = Type.COINS;
                break;
            case "ITEM":
            case "BLOCK":
                type = Type.ITEM;
                break;
            default:
                Type parsed;
                try {
                    parsed = Type.valueOf(rawType);
                } catch (IllegalArgumentException exception) {
                    parsed = Type.EXPERIENCE_LEVELS;
                }
                type = parsed;
                break;
        }
        double amount = Cfg.dbl(section, "amount", 1.0D);
        Material material = null;
        ItemStack rawItem = null;
        if (type == Type.ITEM || section != null && section.contains("material")) {
            material = ColorUtil.material(Cfg.str(section, "material", "DIAMOND"), Material.DIAMOND);
        }
        // Точный предмет-оплата (можно задать в конфиге подсекцией item:)
        if (section != null) {
            rawItem = ru.rooyzee.elytrixtrader.util.ItemCodec.readSingle(section, "item");
            if (rawItem != null) {
                material = rawItem.getType();
                if (!section.contains("amount")) {
                    amount = Math.max(1, rawItem.getAmount());
                }
            }
        }
        return new CostConfig(type, Math.max(0.0D, amount), material,
                Cfg.str(section, "placeholder", "%playerpoints_points%"), rawItem);
    }

    public Type type() {
        return type;
    }

    public Material material() {
        return material;
    }

    /** Точный предмет-оплата или null, если цена задана только материалом. */
    public ItemStack rawItem() {
        return rawItem == null ? null : rawItem.clone();
    }

    /** true, если цена требует конкретного предмета (с мета), а не просто материала. */
    public boolean exactItem() {
        return rawItem != null;
    }

    /** Человекочитаемое имя предмета для сообщений и меню. */
    public String itemName() {
        if (rawItem != null && rawItem.hasItemMeta() && rawItem.getItemMeta().hasDisplayName()) {
            return ru.rooyzee.elytrixtrader.util.ColorUtil.plain(rawItem.getItemMeta().getDisplayName());
        }
        return material == null ? "предмета" : ru.rooyzee.elytrixtrader.util.Text.capitalize(material.name());
    }

    public String placeholder() {
        return placeholder;
    }

    public double amountFor(int multiply) {
        double base = type == Type.ITEM ? Math.max(1.0D, amount) : amount;
        double total = base * Math.max(1, multiply);
        return type == Type.EXPERIENCE_LEVELS || type == Type.ITEM ? Math.rint(total) : total;
    }

    public boolean isExperience() {
        return type == Type.EXPERIENCE_LEVELS || type == Type.EXPERIENCE_POINTS;
    }

    public String label() {
        switch (type) {
            case EXPERIENCE_LEVELS:
                return "опыта";
            case EXPERIENCE_POINTS:
                return "очков опыта";
            case VAULT:
                return "коинов";
            case COINS:
                return "коинов";
            default:
                return itemName();
        }
    }

    public String unit() {
        switch (type) {
            case EXPERIENCE_LEVELS:
                return "ур.";
            case EXPERIENCE_POINTS:
                return "очк.";
            case VAULT:
            case COINS:
                return "коинов";
            default:
                return exactItem() || material == null ? "шт."
                        : ru.rooyzee.elytrixtrader.util.Text.capitalize(material.name());
        }
    }
}
