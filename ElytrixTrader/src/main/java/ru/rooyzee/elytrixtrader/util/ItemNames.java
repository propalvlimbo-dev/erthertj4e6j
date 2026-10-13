package ru.rooyzee.elytrixtrader.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Название предмета для чата/сообщений.
 *
 * Обычные предметы уходят TranslatableComponent'ом — клиент сам
 * переводит их на свой язык. Зелья — особым путём: клиент не всегда
 * переводит ключи potion.effect.*, поэтому имя зелья собирается на
 * сервере по языку игрока (player.getLocale()): у русского клиента
 * будет «Зелье силы II», а не «item.minecraft.potion.effect.strong_strength».
 */
public final class ItemNames {

    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        RU.put("awkward", "Невзрачное зелье");
        RU.put("mundane", "Заурядное зелье");
        RU.put("thick", "Густое зелье");
        RU.put("water", "Бутыль с водой");
        RU.put("night_vision", "Зелье ночного зрения");
        RU.put("invisibility", "Зелье невидимости");
        RU.put("leaping", "Зелье прыгучести");
        RU.put("fire_resistance", "Зелье огнестойкости");
        RU.put("swiftness", "Зелье скорости");
        RU.put("slowness", "Зелье замедления");
        RU.put("turtle_master", "Зелье черепашьей мощи");
        RU.put("water_breathing", "Зелье подводного дыхания");
        RU.put("healing", "Зелье исцеления");
        RU.put("harming", "Зелье вреда");
        RU.put("poison", "Зелье отравления");
        RU.put("regeneration", "Зелье регенерации");
        RU.put("strength", "Зелье силы");
        RU.put("weakness", "Зелье слабости");
        RU.put("luck", "Зелье удачи");
        RU.put("slow_falling", "Зелье медленного падения");
        // Имена enum PotionType не всегда совпадают с ключами эффектов:
        // SPEED → swiftness, INSTANT_HEAL → healing и т.д.
        RU.put("speed", "Зелье скорости");
        RU.put("instant_heal", "Зелье исцеления");
        RU.put("instant_damage", "Зелье вреда");
        RU.put("jump", "Зелье прыгучести");
        RU.put("regen", "Зелье регенерации");

        EN.put("awkward", "Awkward Potion");
        EN.put("mundane", "Mundane Potion");
        EN.put("thick", "Thick Potion");
        EN.put("water", "Water Bottle");
        EN.put("night_vision", "Potion of Night Vision");
        EN.put("invisibility", "Potion of Invisibility");
        EN.put("leaping", "Potion of Leaping");
        EN.put("fire_resistance", "Potion of Fire Resistance");
        EN.put("swiftness", "Potion of Swiftness");
        EN.put("slowness", "Potion of Slowness");
        EN.put("turtle_master", "Potion of the Turtle Master");
        EN.put("water_breathing", "Potion of Water Breathing");
        EN.put("healing", "Potion of Healing");
        EN.put("harming", "Potion of Harming");
        EN.put("poison", "Potion of Poison");
        EN.put("regeneration", "Potion of Regeneration");
        EN.put("strength", "Potion of Strength");
        EN.put("weakness", "Potion of Weakness");
        EN.put("luck", "Potion of Luck");
        EN.put("slow_falling", "Potion of Slow Falling");
        EN.put("speed", "Potion of Swiftness");
        EN.put("instant_heal", "Potion of Healing");
        EN.put("instant_damage", "Potion of Harming");
        EN.put("jump", "Potion of Leaping");
        EN.put("regen", "Potion of Regeneration");
    }

    private ItemNames() {
    }

    public static BaseComponent nameComponent(ItemStack item) {
        return nameComponent(item, null);
    }

    /** С названием предмета + tooltip (HoverEvent:клиент показывает полную info как на hover). */
    public static BaseComponent nameComponentWithHover(ItemStack item, Player viewer) {
        if (item == null || item.getType() == Material.AIR) {
            return new TextComponent("—");
        }
        BaseComponent base = nameComponent(item, viewer == null ? null : viewer.getLocale());
        try {
            // Bungee-chat 1.16.5: HoverEvent принимает hover.content.Content.
            // Класс Item(id, count, nbtJson) — без NMS: nbt собираем вручную
            // из display-имени (если есть) как JSON text-component.
            String nbtJson = craftItemTagJson(item);
            net.md_5.bungee.api.chat.HoverEvent.Action action =
                    net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_ITEM;
            net.md_5.bungee.api.chat.hover.content.Item hoverItem =
                    new net.md_5.bungee.api.chat.hover.content.Item(
                            item.getType().getKey().toString(),
                            Math.max(1, item.getAmount()),
                            net.md_5.bungee.api.chat.ItemTag.ofNbt(nbtJson)
                    );
            net.md_5.bungee.api.chat.HoverEvent hover =
                    new net.md_5.bungee.api.chat.HoverEvent(action, hoverItem);
            base.setHoverEvent(hover);
        } catch (Throwable ignored) {}
        return base;
    }

    /** Сборка JSON-nbt-tag для HoverEvent (только display.Name, без NMS и ComponentSerializer). */
    private static String craftItemTagJson(ItemStack item) {
        try {
            if (item == null || item.getItemMeta() == null) return "{}";
            String display = item.getItemMeta().getDisplayName();
            if (display == null || display.isEmpty()) return "{}";
            // Gson экранирует строку как JSON-литерал; текст-компонент
            // {"text":"..."} оборачиваем в display.Name.
            String text = new com.google.gson.Gson().toJson(display);
            return "{\"display\":{\"Name\":{\"text\":" + text + "}}}";
        } catch (Throwable t) {
            return "{}";
        }
    }

    /** Компонент с названием предмета; lang — язык клиента («ru_ru», «en_us»...). */
    public static BaseComponent nameComponent(ItemStack item, String lang) {
        if (item == null || item.getType() == Material.AIR) {
            return new TextComponent("—");
        }
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return new TextComponent(meta.getDisplayName());
        }
        if (item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION
                || item.getType() == Material.LINGERING_POTION) {
            String potion = potionName(item, lang);
            if (potion != null) {
                return new TextComponent(potion);
            }
        }
        String key = translationKey(item);
        if (key == null) {
            return new TextComponent(prettify(item.getType().name()));
        }
        return new TranslatableComponent(key);
    }

    /** «Зелье силы II», «Взрывное зелье скорости (длительное)» и т.п. */
    private static String potionName(ItemStack item, String lang) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta)) {
            return null;
        }
        PotionData data = ((PotionMeta) meta).getBasePotionData();
        if (data == null || data.getType() == null) {
            return null;
        }
        String type = data.getType().name();
        boolean strong = type.startsWith("STRONG_");
        boolean extended = type.startsWith("LONG_");
        String base = type;
        if (strong) {
            base = type.substring("STRONG_".length());
        } else if (extended) {
            base = type.substring("LONG_".length());
        } else if (data.isExtended()) {
            extended = true;
        } else if (data.isUpgraded()) {
            strong = true;
        }
        base = base.toLowerCase(Locale.ROOT);

        boolean ru = lang == null || lang.toLowerCase(Locale.ROOT).startsWith("ru");
        Map<String, String> map = ru ? RU : EN;
        String name = map.get(base);
        if (name == null) {
            return null;
        }
        if (strong) {
            name = name + " II";
        }
        if (extended) {
            name = ru ? name + " (длительное)" : name + " (extended)";
        }
        Material material = item.getType();
        if (material == Material.SPLASH_POTION) {
            name = ru ? splashRu(name) : "Splash " + name;
        } else if (material == Material.LINGERING_POTION) {
            name = ru ? lingeringRu(name) : "Lingering " + name;
        }
        return name;
    }

    private static String splashRu(String name) {
        if (name.startsWith("Зелье")) {
            return "Взрывное зелье" + name.substring("Зелье".length());
        }
        return "Взрывное: " + name;
    }

    private static String lingeringRu(String name) {
        if (name.startsWith("Зелье")) {
            return "Туманное зелье" + name.substring("Зелье".length());
        }
        return "Туманное: " + name;
    }

    /** Ключ перевода vanilla для НЕ-зелий (item.minecraft.* / block.minecraft.*). */
    private static String translationKey(ItemStack item) {
        Material type = item.getType();
        if (type == Material.TIPPED_ARROW) {
            ItemMeta meta = item.getItemMeta();
            if (meta instanceof PotionMeta) {
                PotionData data = ((PotionMeta) meta).getBasePotionData();
                if (data != null && data.getType() != null) {
                    return "item.minecraft.tipped_arrow.effect."
                            + data.getType().name().toLowerCase(Locale.ROOT);
                }
            }
            return null;
        }
        String key = type.name().toLowerCase(Locale.ROOT);
        return (type.isBlock() ? "block.minecraft." : "item.minecraft.") + key;
    }

    private static String prettify(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "—";
        }
        String clean = raw.toUpperCase(Locale.ROOT);
        if (clean.startsWith("LONG_")) {
            clean = clean.substring(5);
        } else if (clean.startsWith("STRONG_")) {
            clean = clean.substring(7);
        }
        String[] parts = clean.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.length() == 0 ? clean : builder.toString();
    }
}
