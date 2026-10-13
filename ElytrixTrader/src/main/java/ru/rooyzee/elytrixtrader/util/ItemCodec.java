package ru.rooyzee.elytrixtrader.util;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Точная сериализация ItemStack в YAML и обратно.
 *
 * Зачем это нужно:
 *   Bukkit-овский ItemStack.serialize() (то, что делает section.set(path, itemStack))
 *   сохраняет мета «как умеет сам Bukkit»: зелья пишутся одним полем potion-type
 *   с LEGACY-названием (minecraft:long_night_vision), которое на серверах 1.20.2+
 *   уже не разбирается, часть мета (атрибуты, текстуры головы, содержимое шалкера)
 *   теряется вовсе. В итоге «Зелье лечения II 8:00» после перезагрузки превращается
 *   в обычную бутылку.
 *
 * Здесь предмет разбирается на явные поля (тип, количество, имя, лор, зачарования,
 * флаги, CustomModelData, прочность, зелье со всеми эффектами, цвет кожи, узоры
 * знамени, фейерверк, книга, атрибуты, содержимое контейнеров) — каждое поле
 * пишется/читается напрямую и не зависит от версии сервера.
 *
 * Дополнительно для «экзотических» мета сохраняется bukkit-meta — снапшот
 * Bukkit-сериализации, который применяется ПЕРЕД явными полями, поэтому
 * ничего не теряется, а явные поля всегда перекрывают устаревшие данные.
 *
 * Чтение обратно совместимо со старыми форматами:
 *   1. новый формат (подсекции 0,1,2 ... с явными полями);
 *   2. Bukkit-мапы с «==» (org.bukkit.inventory.ItemStack);
 *   3. строки YAML/base64 из самых старых версий редактора.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    /* ════════════════════════════════════════════════════════════════════════
       YAML
       ════════════════════════════════════════════════════════════════════════ */

    /**
     * Читает YAML-файл так, чтобы старые файлы редактора (с Bukkit-маркерами «==»)
     * не роняли загрузку: если Bukkit не смог десериализовать такие блоки,
     * маркеры вырезаются и файл парсится как обычные мапы — их разбирает
     * {@link #fromLegacyMap(Map)}.
     */
    public static YamlConfiguration loadYaml(java.io.File file) {
        String text = readText(file);
        if (text == null) {
            return new YamlConfiguration();
        }
        return parseYaml(text);
    }

    public static YamlConfiguration parseYaml(String text) {
        if (text == null) {
            return new YamlConfiguration();
        }
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(text);
            if (!hasSerializationMarkers(text)) {
                return configuration;
            }
        } catch (Throwable ignored) {
            // Bukkit не смог десериализовать блоки с «==» — ниже уберём маркеры
        }
        // В файле есть Bukkit-маркеры («==»): их десериализация зависит от версии сервера
        // (именно так терялись зелья), поэтому разбираем такие блоки сами.
        YamlConfiguration plain = new YamlConfiguration();
        try {
            plain.loadFromString(stripSerializationMarkers(text));
        } catch (Throwable throwable) {
            throw new IllegalArgumentException("Не удалось прочитать YAML: " + throwable, throwable);
        }
        return plain;
    }

    private static boolean hasSerializationMarkers(String text) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("==:") || trimmed.startsWith("- ==:")) {
                return true;
            }
        }
        return false;
    }

    private static String readText(java.io.File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static String stripSerializationMarkers(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("==:")) {
                // маркер первой строкой блока — строку можно просто выбросить
                continue;
            }
            int marker = line.indexOf("==:");
            if (marker > 0) {
                String prefix = line.substring(0, marker);
                String head = prefix.trim();
                if (!head.isEmpty() && head.chars().allMatch(character -> character == '-')) {
                    // «- ==: PotionEffect»: оставляем тире, блок продолжится ниже
                    builder.append(prefix).append('\n');
                    continue;
                }
            }
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    /* ════════════════════════════════════════════════════════════════════════
       Списки предметов
       ════════════════════════════════════════════════════════════════════════ */

    /** Записывает список предметов в подсекцию {@code key} (0, 1, 2 ...). */
    public static void writeList(ConfigurationSection parent, String key, List<ItemStack> items) {
        if (parent == null) {
            return;
        }
        parent.set(key, null);
        List<ItemStack> clean = clean(items);
        if (clean.isEmpty()) {
            return;
        }
        ConfigurationSection section = parent.createSection(key);
        for (int index = 0; index < clean.size(); index++) {
            write(section.createSection(String.valueOf(index)), clean.get(index));
        }
        section.set("count", clean.size());
    }

    /** Читает список предметов, понимая и новый, и все старые форматы. */
    public static List<ItemStack> readList(ConfigurationSection parent, String key) {
        List<ItemStack> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section != null) {
            for (String child : orderedKeys(section)) {
                if (child.equals("count")) {
                    continue;
                }
                ItemStack item = coerce(section.get(child), section.getConfigurationSection(child));
                if (item != null) {
                    result.add(item);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        // Старые форматы: обычный список (ItemStack / Map / строка YAML)
        List<?> raw = parent.getList(key);
        if (raw != null) {
            for (Object value : raw) {
                ItemStack item = coerce(value, null);
                if (item != null) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    /** Одиночный предмет в подсекции {@code key} (например, точная цена предметом). */
    public static void writeSingle(ConfigurationSection parent, String key, ItemStack item) {
        if (parent == null) {
            return;
        }
        parent.set(key, null);
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        write(parent.createSection(key), item);
    }

    /** Читает одиночный предмет из подсекции {@code key} (или null). */
    public static ItemStack readSingle(ConfigurationSection parent, String key) {
        if (parent == null) {
            return null;
        }
        ConfigurationSection section = parent.getConfigurationSection(key);
        if (section != null) {
            return read(section);
        }
        return coerce(parent.get(key), null);
    }

    /* ════════════════════════════════════════════════════════════════════════
       Один предмет
       ════════════════════════════════════════════════════════════════════════ */

    /** Кладёт предмет в секцию в разобранном виде. */
    public static void write(ConfigurationSection section, ItemStack item) {
        if (section == null || item == null || item.getType() == Material.AIR) {
            return;
        }
        section.set("type", item.getType().name());
        section.set("amount", Math.max(1, item.getAmount()));
        if (!item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        writeMeta(section, meta);
    }

    /** Собирает предмет из секции; null, если типа нет или это AIR. */
    public static ItemStack read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Material material = material(section.getString("type", null));
        if (material == null || material == Material.AIR) {
            return null;
        }
        int amount = Math.max(1, section.getInt("amount", 1));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = readMeta(section, item);
        if (meta != null) {
            try {
                item.setItemMeta(meta);
            } catch (Throwable ignored) {
                // мета не подходит к материалу — оставляем предмет как есть
            }
        }
        return item;
    }

    /* ───────────────────────── мета ───────────────────────── */

    private static void writeMeta(ConfigurationSection section, ItemMeta meta) {
        if (meta.hasDisplayName()) {
            section.set("display-name", meta.getDisplayName());
        }
        if (meta.hasLocalizedName()) {
            section.set("localized-name", meta.getLocalizedName());
        }
        if (meta.hasLore()) {
            section.set("lore", new ArrayList<>(meta.getLore()));
        }

        List<String> enchantments = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
            enchantments.add(enchantmentKey(entry.getKey()) + ":" + entry.getValue());
        }
        if (!enchantments.isEmpty()) {
            section.set("enchantments", enchantments);
        }
        if (meta instanceof EnchantmentStorageMeta) {
            List<String> stored = new ArrayList<>();
            for (Map.Entry<Enchantment, Integer> entry : ((EnchantmentStorageMeta) meta).getStoredEnchants().entrySet()) {
                stored.add(enchantmentKey(entry.getKey()) + ":" + entry.getValue());
            }
            if (!stored.isEmpty()) {
                section.set("stored-enchantments", stored);
            }
        }

        Set<ItemFlag> flags = meta.getItemFlags();
        if (flags != null && !flags.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (ItemFlag flag : flags) {
                names.add(flag.name());
            }
            section.set("item-flags", names);
        }
        if (meta.isUnbreakable()) {
            section.set("unbreakable", true);
        }
        if (meta.hasCustomModelData()) {
            section.set("custom-model-data", meta.getCustomModelData());
        }
        if (meta instanceof Damageable && ((Damageable) meta).hasDamage()) {
            section.set("damage", ((Damageable) meta).getDamage());
        }
        if (meta.hasAttributeModifiers()) {
            List<String> modifiers = new ArrayList<>();
            for (Attribute attribute : Attribute.values()) {
                Collection<AttributeModifier> list = meta.getAttributeModifiers(attribute);
                if (list == null) {
                    continue;
                }
                for (AttributeModifier modifier : list) {
                    if (modifier == null) {
                        continue;
                    }
                    modifiers.add(attribute.name() + "|" + modifier.getOperation().name() + "|"
                            + (modifier.getSlot() == null ? "" : modifier.getSlot().name()) + "|"
                            + modifier.getName() + "|" + modifierUniqueId(modifier) + "|" + modifier.getAmount());
                }
            }
            if (!modifiers.isEmpty()) {
                section.set("attribute-modifiers", modifiers);
            }
        }

        if (meta instanceof PotionMeta) {
            writePotion(section, (PotionMeta) meta);
        }
        if (meta instanceof LeatherArmorMeta) {
            Color color = ((LeatherArmorMeta) meta).getColor();
            if (color != null) {
                section.set("leather-color", color.asRGB());
            }
        }
        if (meta instanceof SkullMeta) {
            String owner = ((SkullMeta) meta).getOwner();
            if (owner != null && !owner.trim().isEmpty()) {
                section.set("skull-owner", owner.trim());
            }
        }
        if (meta instanceof BannerMeta) {
            List<String> patterns = new ArrayList<>();
            for (Pattern pattern : ((BannerMeta) meta).getPatterns()) {
                if (pattern == null || pattern.getPattern() == null || pattern.getColor() == null) {
                    continue;
                }
                patterns.add(pattern.getPattern().name() + ":" + pattern.getColor().name());
            }
            if (!patterns.isEmpty()) {
                section.set("banner-patterns", patterns);
            }
        }
        if (meta instanceof FireworkMeta) {
            writeFirework(section, (FireworkMeta) meta);
        }
        if (meta instanceof BookMeta) {
            BookMeta book = (BookMeta) meta;
            if (book.hasTitle()) {
                section.set("book-title", book.getTitle());
            }
            if (book.hasAuthor()) {
                section.set("book-author", book.getAuthor());
            }
            if (book.hasPages()) {
                section.set("book-pages", new ArrayList<>(book.getPages()));
            }
        }
        writeSpawnEgg(section, meta);
        writeContainer(section, meta);

        // Страховка: для «экзотических» мета и для всего, что мы не умеем описать
        // своими полями (PDC, кастомный NBT от сторонних плагинов), храним снапшот.
        if (!isPlainMeta(meta) || hasUnhandledMeta(meta)) {
            String snapshot = bukkitMetaSnapshot(meta);
            if (snapshot != null) {
                section.set("bukkit-meta", snapshot);
            }
        }
    }

    private static ItemMeta readMeta(ConfigurationSection section, ItemStack item) {
        ItemMeta meta = bukkitMetaFromSnapshot(section.getString("bukkit-meta", null), item);
        if (meta == null) {
            meta = item.getItemMeta();
        }
        if (meta == null) {
            return null;
        }

        if (section.contains("display-name")) {
            meta.setDisplayName(section.getString("display-name", ""));
        }
        if (section.contains("localized-name")) {
            meta.setLocalizedName(section.getString("localized-name", ""));
        }
        List<String> lore = stringList(section, "lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore);
        }

        for (String entry : stringList(section, "enchantments")) {
            String[] parts = splitEnchantment(entry);
            Enchantment enchantment = enchantment(parts[0]);
            if (enchantment == null) {
                continue;
            }
            try {
                meta.addEnchant(enchantment, parseLevel(parts), true);
            } catch (Throwable ignored) {
            }
        }
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta storage = (EnchantmentStorageMeta) meta;
            for (String entry : stringList(section, "stored-enchantments")) {
                String[] parts = splitEnchantment(entry);
                Enchantment enchantment = enchantment(parts[0]);
                if (enchantment == null) {
                    continue;
                }
                try {
                    storage.addStoredEnchant(enchantment, parseLevel(parts), true);
                } catch (Throwable ignored) {
                }
            }
        }

        List<ItemFlag> flags = new ArrayList<>();
        for (String name : stringList(section, "item-flags")) {
            try {
                flags.add(ItemFlag.valueOf(name.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!flags.isEmpty()) {
            meta.addItemFlags(flags.toArray(new ItemFlag[0]));
        }
        if (section.contains("unbreakable")) {
            meta.setUnbreakable(section.getBoolean("unbreakable", false));
        }
        if (section.contains("custom-model-data")) {
            int model = section.getInt("custom-model-data", 0);
            if (model > 0) {
                meta.setCustomModelData(model);
            }
        }
        if (section.contains("damage") && meta instanceof Damageable) {
            ((Damageable) meta).setDamage(Math.max(0, section.getInt("damage", 0)));
        }
        readAttributes(section, meta);

        if (meta instanceof PotionMeta) {
            readPotion(section, (PotionMeta) meta);
        }
        if (meta instanceof LeatherArmorMeta && section.contains("leather-color")) {
            ((LeatherArmorMeta) meta).setColor(Color.fromRGB(section.getInt("leather-color", 0)));
        }
        if (meta instanceof SkullMeta && section.contains("skull-owner")) {
            applySkullOwner((SkullMeta) meta, section.getString("skull-owner", ""));
        }
        if (meta instanceof BannerMeta) {
            List<Pattern> patterns = new ArrayList<>();
            for (String entry : stringList(section, "banner-patterns")) {
                String[] parts = entry.split(":", 2);
                if (parts.length < 2) {
                    continue;
                }
                PatternType type = enumValue(PatternType.class, parts[0]);
                DyeColor color = enumValue(DyeColor.class, parts[1]);
                if (type != null && color != null) {
                    patterns.add(new Pattern(color, type));
                }
            }
            if (!patterns.isEmpty()) {
                ((BannerMeta) meta).setPatterns(patterns);
            }
        }
        if (meta instanceof FireworkMeta) {
            readFirework(section, (FireworkMeta) meta);
        }
        if (meta instanceof BookMeta) {
            BookMeta book = (BookMeta) meta;
            if (section.contains("book-title")) {
                book.setTitle(section.getString("book-title", null));
            }
            if (section.contains("book-author")) {
                book.setAuthor(section.getString("book-author", null));
            }
            List<String> pages = stringList(section, "book-pages");
            if (!pages.isEmpty()) {
                book.setPages(pages);
            }
        }
        readSpawnEgg(section, meta);
        readContainer(section, meta);
        return meta;
    }

    /* ───────────────────────── зелья ───────────────────────── */

    private static void writePotion(ConfigurationSection section, PotionMeta meta) {
        PotionType type = null;
        boolean extended = false;
        boolean upgraded = false;
        Object data = invoke(meta, "getBasePotionData");
        if (data != null) {
            Object rawType = invoke(data, "getType");
            if (rawType instanceof PotionType) {
                type = (PotionType) rawType;
            }
            Object isExtended = invoke(data, "isExtended");
            Object isUpgraded = invoke(data, "isUpgraded");
            extended = Boolean.TRUE.equals(isExtended);
            upgraded = Boolean.TRUE.equals(isUpgraded);
        }
        if (type == null) {
            Object rawType = invoke(meta, "getBasePotionType");
            if (rawType instanceof PotionType) {
                type = (PotionType) rawType;
            }
        }
        if (type != null) {
            String name = type.name().toUpperCase(Locale.ROOT);
            // На старых серверах LONG_/STRONG_ уже внутри PotionType — флаги дублируем явно.
            if (name.startsWith("LONG_")) {
                extended = true;
            } else if (name.startsWith("STRONG_")) {
                upgraded = true;
            }
            section.set("potion-type", name);
        }
        if (extended) {
            section.set("potion-extended", true);
        }
        if (upgraded) {
            section.set("potion-upgraded", true);
        }

        List<PotionEffect> effects = meta.getCustomEffects();
        if (effects != null && !effects.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (PotionEffect effect : effects) {
                if (effect == null || effect.getType() == null) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", effect.getType().getName());
                entry.put("amplifier", effect.getAmplifier());
                entry.put("duration", effect.getDuration());
                entry.put("ambient", effect.isAmbient());
                entry.put("particles", effect.hasParticles());
                entry.put("icon", effect.hasIcon());
                list.add(entry);
            }
            if (!list.isEmpty()) {
                section.set("potion-effects", list);
            }
        }
        Color color = meta.getColor();
        if (color != null) {
            section.set("potion-color", color.asRGB());
        }
    }

    private static void readPotion(ConfigurationSection section, PotionMeta meta) {
        String rawType = section.getString("potion-type", null);
        boolean extended = section.getBoolean("potion-extended", false);
        boolean upgraded = section.getBoolean("potion-upgraded", false);
        if (rawType != null && !rawType.trim().isEmpty()) {
            PotionType full = resolvePotionType(rawType.trim(), extended, upgraded);
            if (full != null) {
                applyPotionType(meta, full, extended, upgraded);
            }
        }

        List<?> rawEffects = section.getList("potion-effects");
        if (rawEffects != null) {
            List<PotionEffect> parsed = new ArrayList<>();
            for (Object value : rawEffects) {
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) value;
                PotionEffectType type = PotionEffectType.getByName(String.valueOf(map.get("type")));
                if (type == null) {
                    continue;
                }
                int amplifier = integer(map.get("amplifier"), 0);
                int duration = integer(map.get("duration"), 200);
                boolean ambient = bool(map.get("ambient"), false);
                boolean particles = bool(map.get("particles"), true);
                boolean icon = bool(map.get("icon"), true);
                PotionEffect effect = potionEffect(type, duration, amplifier, ambient, particles, icon);
                if (effect != null) {
                    parsed.add(effect);
                }
            }
            if (!parsed.isEmpty()) {
                meta.clearCustomEffects();
                for (PotionEffect effect : parsed) {
                    meta.addCustomEffect(effect, true);
                }
            }
        }
        if (section.contains("potion-color")) {
            meta.setColor(Color.fromRGB(section.getInt("potion-color", 0)));
        }
    }

    /**
     * Ставит базовый тип зелья так, как умеет текущий сервер:
     * на 1.20.2+ есть setBasePotionType(PotionType), до этого — setBasePotionData(PotionData).
     */
    private static void applyPotionType(PotionMeta meta, PotionType type, boolean extended, boolean upgraded) {
        try {
            Method setter = findMethod(meta.getClass(), "setBasePotionType");
            if (setter != null) {
                setter.invoke(meta, type);
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            PotionType base = stripVariant(type);
            PotionData data = new PotionData(base, extended, upgraded);
            meta.setBasePotionData(data);
        } catch (Throwable ignored) {
            try {
                Object data = invokeConstructor("org.bukkit.potion.PotionData",
                        new Class<?>[]{PotionType.class, boolean.class, boolean.class},
                        type, extended, upgraded);
                if (data != null) {
                    Method setter = findMethod(meta.getClass(), "setBasePotionData");
                    if (setter != null) {
                        setter.invoke(meta, data);
                    }
                }
            } catch (Throwable ignoredToo) {
            }
        }
    }

    /** LONG_NIGHT_VISION -> NIGHT_VISION, STRONG_HEALING -> HEALING (если такой вариант есть). */
    private static PotionType stripVariant(PotionType type) {
        String name = type.name().toUpperCase(Locale.ROOT);
        String base = name.startsWith("LONG_") ? name.substring(5)
                : name.startsWith("STRONG_") ? name.substring(7) : null;
        if (base == null) {
            return type;
        }
        PotionType stripped = potionTypeOrNull(base);
        return stripped == null ? type : stripped;
    }

    /** Ищет тип зелья по имени, учитывая расхождения имён между версиями сервера. */
    private static PotionType resolvePotionType(String name, boolean extended, boolean upgraded) {
        String clean = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        PotionType exact = potionTypeOrNull(clean);
        if (exact != null) {
            return exact;
        }
        String base = clean.startsWith("LONG_") ? clean.substring(5)
                : clean.startsWith("STRONG_") ? clean.substring(7) : clean;
        String variant = (upgraded ? "STRONG_" : "") + (!upgraded && extended ? "LONG_" : "") + base;
        PotionType rebuilt = potionTypeOrNull(variant);
        if (rebuilt != null) {
            return rebuilt;
        }
        return potionTypeOrNull(base);
    }

    private static PotionType potionTypeOrNull(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return PotionType.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static PotionEffect potionEffect(PotionEffectType type, int duration, int amplifier,
                                             boolean ambient, boolean particles, boolean icon) {
        try {
            return new PotionEffect(type, duration, amplifier, ambient, particles, icon);
        } catch (Throwable ignored) {
        }
        try {
            return new PotionEffect(type, duration, amplifier, ambient, particles);
        } catch (Throwable ignored) {
        }
        try {
            return new PotionEffect(type, duration, amplifier, ambient);
        } catch (Throwable ignored) {
        }
        return null;
    }

    /* ───────────────────────── фейерверки ───────────────────────── */

    private static void writeFirework(ConfigurationSection section, FireworkMeta meta) {
        section.set("firework-power", meta.getPower());
        List<FireworkEffect> effects = meta.getEffects();
        if (effects == null || effects.isEmpty()) {
            return;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (FireworkEffect effect : effects) {
            if (effect == null || effect.getType() == null) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("shape", effect.getType().name());
            entry.put("flicker", effect.hasFlicker());
            entry.put("trail", effect.hasTrail());
            entry.put("colors", colors(effect.getColors()));
            entry.put("fade-colors", colors(effect.getFadeColors()));
            list.add(entry);
        }
        if (!list.isEmpty()) {
            section.set("firework-effects", list);
        }
    }

    private static void readFirework(ConfigurationSection section, FireworkMeta meta) {
        if (section.contains("firework-power")) {
            meta.setPower(Math.max(0, Math.min(127, section.getInt("firework-power", 1))));
        }
        List<?> raw = section.getList("firework-effects");
        if (raw == null) {
            return;
        }
        List<FireworkEffect> effects = new ArrayList<>();
        for (Object value : raw) {
            if (!(value instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) value;
            FireworkEffect.Type shape = enumValue(FireworkEffect.Type.class, String.valueOf(map.get("shape")));
            if (shape == null) {
                continue;
            }
            FireworkEffect.Builder builder = FireworkEffect.builder().with(shape);
            if (bool(map.get("flicker"), false)) {
                builder.withFlicker();
            }
            if (bool(map.get("trail"), false)) {
                builder.withTrail();
            }
            builder.withColor(parseColors(map.get("colors")));
            List<Color> fade = parseColors(map.get("fade-colors"));
            if (!fade.isEmpty()) {
                builder.withFade(fade);
            }
            effects.add(builder.build());
        }
        if (!effects.isEmpty()) {
            meta.clearEffects();
            meta.addEffects(effects);
        }
    }

    private static List<Integer> colors(List<Color> colors) {
        List<Integer> result = new ArrayList<>();
        if (colors == null) {
            return result;
        }
        for (Color color : colors) {
            if (color != null) {
                result.add(color.asRGB());
            }
        }
        return result;
    }

    private static List<Color> parseColors(Object raw) {
        List<Color> result = new ArrayList<>();
        if (!(raw instanceof Collection)) {
            return result;
        }
        for (Object value : (Collection<?>) raw) {
            int rgb = integer(value, -1);
            if (rgb >= 0) {
                result.add(Color.fromRGB(rgb));
            }
        }
        return result;
    }

    /* ─────────────── яйца призыва / контейнеры (шалкер и т.п.) ─────────────── */

    private static void writeSpawnEgg(ConfigurationSection section, ItemMeta meta) {
        if (!meta.getClass().getSimpleName().contains("SpawnEgg")) {
            return;
        }
        Object type = invoke(meta, "getSpawnedType");
        if (type != null) {
            section.set("spawn-egg-type", String.valueOf(type));
        }
    }

    private static void readSpawnEgg(ConfigurationSection section, ItemMeta meta) {
        String name = section.getString("spawn-egg-type", null);
        if (name == null || name.trim().isEmpty() || !meta.getClass().getSimpleName().contains("SpawnEgg")) {
            return;
        }
        try {
            Object type = Enum.valueOf(org.bukkit.entity.EntityType.class, name.trim().toUpperCase(Locale.ROOT));
            Method setter = findMethod(meta.getClass(), "setSpawnedType");
            if (setter != null) {
                setter.invoke(meta, type);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void writeContainer(ConfigurationSection section, ItemMeta meta) {
        if (!(meta instanceof BlockStateMeta)) {
            return;
        }
        try {
            BlockState state = ((BlockStateMeta) meta).getBlockState();
            if (!(state instanceof Container)) {
                return;
            }
            Inventory inventory = ((Container) state).getInventory();
            if (inventory == null) {
                return;
            }
            ItemStack[] contents = inventory.getContents();
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack content : contents) {
                items.add(content == null ? null : content.clone());
            }
            if (clean(items).isEmpty()) {
                return;
            }
            ConfigurationSection holder = section.createSection("container-items");
            for (int index = 0; index < items.size(); index++) {
                ItemStack content = items.get(index);
                if (content == null || content.getType() == Material.AIR) {
                    continue;
                }
                ConfigurationSection slot = holder.createSection(String.valueOf(index));
                write(slot, content);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readContainer(ConfigurationSection section, ItemMeta meta) {
        if (!(meta instanceof BlockStateMeta)) {
            return;
        }
        ConfigurationSection holder = section.getConfigurationSection("container-items");
        if (holder == null) {
            return;
        }
        try {
            BlockStateMeta blockStateMeta = (BlockStateMeta) meta;
            BlockState state = blockStateMeta.getBlockState();
            if (!(state instanceof Container)) {
                return;
            }
            Inventory inventory = ((Container) state).getInventory();
            if (inventory == null) {
                return;
            }
            for (String key : orderedKeys(holder)) {
                ItemStack content = coerce(holder.get(key), holder.getConfigurationSection(key));
                if (content == null) {
                    continue;
                }
                int slot = integer(key, -1);
                if (slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, content);
                }
            }
            blockStateMeta.setBlockState(state);
        } catch (Throwable ignored) {
        }
    }

    /* ───────────────────────── атрибуты ───────────────────────── */

    private static Object modifierUniqueId(AttributeModifier modifier) {
        try {
            return modifier.getUniqueId();
        } catch (Throwable ignored) {
            return UUID.randomUUID();
        }
    }

    /**
     * Владелец черепа. Если профиль уже есть (его принёс bukkit-meta, а там
     * может лежать текстура головы), не затираем его поиском по нику.
     */
    @SuppressWarnings("deprecation")
    private static void applySkullOwner(SkullMeta meta, String name) {
        if (name == null || name.trim().isEmpty() || "null".equals(name.trim())) {
            return;
        }
        try {
            if (meta.hasOwner()) {
                return;
            }
        } catch (Throwable ignored) {
        }
        try {
            org.bukkit.OfflinePlayer owner = org.bukkit.Bukkit.getOfflinePlayer(name.trim());
            if (owner != null) {
                meta.setOwningPlayer(owner);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void readAttributes(ConfigurationSection section, ItemMeta meta) {
        List<String> raw = stringList(section, "attribute-modifiers");
        if (raw.isEmpty()) {
            return;
        }
        try {
            clearAttributeModifiers(meta);
            for (String line : raw) {
                String[] parts = line.split("\\|", -1);
                if (parts.length < 6) {
                    continue;
                }
                Attribute attribute = enumValue(Attribute.class, parts[0]);
                AttributeModifier.Operation operation = enumValue(AttributeModifier.Operation.class, parts[1]);
                if (attribute == null || operation == null) {
                    continue;
                }
                EquipmentSlot slot = parts[2].isEmpty() ? null : enumValue(EquipmentSlot.class, parts[2]);
                UUID id;
                try {
                    id = UUID.fromString(parts[4]);
                } catch (Throwable exception) {
                    id = UUID.randomUUID();
                }
                double amount = parseDoubleSafe(parts[5]);
                AttributeModifier modifier = new AttributeModifier(id, parts[3], amount, operation, slot);
                meta.addAttributeModifier(attribute, modifier);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Снимает все модификаторы: свои поля файла важнее того, что принёс снапшот. */
    private static void clearAttributeModifiers(ItemMeta meta) {
        if (!meta.hasAttributeModifiers()) {
            return;
        }
        for (Attribute attribute : Attribute.values()) {
            Collection<AttributeModifier> existing = meta.getAttributeModifiers(attribute);
            if (existing == null || existing.isEmpty()) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(existing)) {
                meta.removeAttributeModifier(attribute, modifier);
            }
        }
    }

    /* ───────────────────────── Bukkit-снапшот ───────────────────────── */

    /** Ключи Bukkit-сериализации, которые мы и так переносим своими полями. */
    private static final Set<String> HANDLED_META_KEYS = new HashSet<>(Arrays.asList(
            "==", "meta-type", "display-name", "localizedName", "lore", "enchants",
            "stored-enchants", "ItemFlags", "Unbreakable", "custom-model-data", "damage",
            "potion-type", "custom-effects", "color", "custom-color", "skull-owner",
            "title", "author", "pages", "patterns", "power", "firework-effects",
            "spawned-type", "items", "entities", "map-color", "scaling"));

    /**
     * Есть ли в мета то, что мы своими полями не описываем: PersistentDataContainer
     * и прочий NBT от сторонних плагинов (Bukkit кладёт его в ключ internal).
     * Без снапшота такие предметы теряли свои данные — например, стакнутые
     * зелья из плагинов на стаки приходили в магазин обычным «Potion».
     */
    private static boolean hasUnhandledMeta(ItemMeta meta) {
        try {
            for (String key : meta.serialize().keySet()) {
                if (!HANDLED_META_KEYS.contains(key)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // если сериализация не удалась — снапшот тоже не получится, не мешаем
        }
        return false;
    }

    private static boolean isPlainMeta(ItemMeta meta) {
        // CraftMetaItem — «пустая» мета, у неё нет полей кроме тех, что мы пишем явно.
        return "CraftMetaItem".equals(meta.getClass().getSimpleName());
    }

    private static String bukkitMetaSnapshot(ItemMeta meta) {
        try {
            YamlConfiguration temp = new YamlConfiguration();
            temp.set("meta", meta);
            String yaml = temp.saveToString();
            if (yaml == null || yaml.trim().isEmpty()) {
                return null;
            }
            return java.util.Base64.getEncoder().encodeToString(
                    yaml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemMeta bukkitMetaFromSnapshot(String encoded, ItemStack item) {
        if (encoded == null || encoded.trim().isEmpty()) {
            return null;
        }
        try {
            String yaml = new String(java.util.Base64.getDecoder().decode(encoded.trim()),
                    java.nio.charset.StandardCharsets.UTF_8);
            YamlConfiguration temp = new YamlConfiguration();
            temp.loadFromString(yaml);
            Object value = temp.get("meta");
            if (value instanceof ItemMeta) {
                return (ItemMeta) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /* ════════════════════════════════════════════════════════════════════════
       Утилиты
       ════════════════════════════════════════════════════════════════════════ */

    /** Отбрасывает null/AIR. */
    public static List<ItemStack> clean(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>();
        if (items == null) {
            return result;
        }
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                result.add(item);
            }
        }
        return result;
    }

    /** Глубокий клон списка (чтобы чужие ItemStack не «протекли» в модель). */
    public static List<ItemStack> copy(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : clean(items)) {
            result.add(item.clone());
        }
        return result;
    }

    /**
     * Превращает что угодно из старого конфига в ItemStack:
     * уже готовый ItemStack, Bukkit-мапу с «==», строку YAML или подсекцию нового формата.
     */
    private static ItemStack coerce(Object value, ConfigurationSection section) {
        if (section != null) {
            if (section.contains("type") && section.contains("meta")) {
                // старая Bukkit-раскладка: type/amount + вложенная meta
                ItemStack legacy = fromLegacyMap(sectionMap(section));
                if (legacy != null) {
                    return legacy;
                }
            }
            return read(section);
        }
        if (value instanceof ItemStack) {
            ItemStack item = (ItemStack) value;
            return item.getType() == Material.AIR ? null : item.clone();
        }
        if (value instanceof Map) {
            Map<String, Object> map = castMap(value);
            ItemStack legacy = fromLegacyMap(map);
            if (legacy != null) {
                return legacy;
            }
            try {
                ItemStack item = ItemStack.deserialize(new LinkedHashMap<>(map));
                if (item != null && item.getType() != Material.AIR) {
                    return item;
                }
            } catch (Throwable ignored) {
            }
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) {
                return null;
            }
            for (String key : new String[]{"item", "i", "itemstack"}) {
                try {
                    YamlConfiguration temp = new YamlConfiguration();
                    temp.loadFromString(raw);
                    ItemStack item = temp.getItemStack(key);
                    if (item != null && item.getType() != Material.AIR) {
                        return item;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    /** Секцию — в обычную Map (вложенные секции становятся вложенными Map). */
    private static Map<String, Object> sectionMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            result.put(key, plainValue(section.get(key)));
        }
        return result;
    }

    /** Секции и списки секций — в обычные Map/List (иначе вложенное не разберётся). */
    private static Object plainValue(Object value) {
        if (value instanceof ConfigurationSection) {
            return sectionMap((ConfigurationSection) value);
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object element : (List<?>) value) {
                result.add(plainValue(element));
            }
            return result;
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), plainValue(entry.getValue()));
            }
            return result;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /* ───────────────────────── старый формат Bukkit ─────────────────────────
       Файлы, записанные прошлой версией редактора через section.set(path, itemStack),
       выглядят как {type: POTION, amount: 2, meta: {potion-type: ..., enchants: {...}}}.
       Разбираем такую мапу сами — независимо от версии сервера. */

    /** ItemMeta.isEmpty() есть не во всех версиях — проверяем через serialize(). */
    private static boolean metaIsEmpty(ItemMeta meta) {
        try {
            for (String key : meta.serialize().keySet()) {
                if ("==".equals(key) || "meta-type".equals(key)) {
                    continue;
                }
                return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ItemStack fromLegacyMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Material material = material(String.valueOf(map.get("type")));
        if (material == null || material == Material.AIR) {
            return null;
        }
        ItemStack item = new ItemStack(material, Math.max(1, integer(map.get("amount"), 1)));
        Object rawMeta = map.get("meta");
        if (!(rawMeta instanceof Map)) {
            return item;
        }
        Map<String, Object> meta = castMap(rawMeta);
        ItemStack bukkit = bukkitDeserialize(map, material);
        if (bukkit != null) {
            // Bukkit сам разобрал свои поля (имя, лор, зачарования, internal-NBT) —
            // переписываем только то, что между версиями сервера читается неверно: зелья.
            ItemMeta target = bukkit.getItemMeta();
            if (target instanceof PotionMeta) {
                applyLegacyPotion((PotionMeta) target, meta);
            }
            if (target != null) {
                try {
                    item.setItemMeta(target);
                } catch (Throwable ignored) {
                }
            }
            return item;
        }
        // Bukkit не смог — разбираем всё сами
        applyLegacyMeta(item, item.getItemMeta(), meta);
        return item;
    }

    /**
     * Пытается собрать предмет средствами Bukkit; null, если не вышло.
     * Bukkit-версия ItemStack.deserialize часто вообще не переносит мета —
     * такой предмет нам не нужен, иначе потеряем имя и зачарования.
     */
    private static ItemStack bukkitDeserialize(Map<String, Object> map, Material material) {
        try {
            ItemStack bukkit = ItemStack.deserialize(new LinkedHashMap<>(map));
            if (bukkit != null && bukkit.getType() == material
                    && bukkit.hasItemMeta() && bukkit.getItemMeta() != null
                    && !metaIsEmpty(bukkit.getItemMeta())) {
                return bukkit;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void applyLegacyMeta(ItemStack item, ItemMeta target, Map<String, Object> meta) {
        if (target == null) {
            return;
        }
        Object displayName = meta.get("display-name");
        if (displayName != null) {
            target.setDisplayName(legacyComponent(String.valueOf(displayName)));
        }
        Object localizedName = meta.get("localizedName");
        if (localizedName != null) {
            try {
                target.setLocalizedName(legacyComponent(String.valueOf(localizedName)));
            } catch (Throwable ignored) {
            }
        }
        List<String> lore = new ArrayList<>();
        for (String line : legacyStrings(meta.get("lore"))) {
            lore.add(legacyComponent(line));
        }
        if (!lore.isEmpty()) {
            target.setLore(lore);
        }
        if (meta.get("enchants") instanceof Map) {
            for (Map.Entry<String, Object> entry : castMap(meta.get("enchants")).entrySet()) {
                Enchantment enchantment = enchantment(entry.getKey());
                if (enchantment != null) {
                    try {
                        target.addEnchant(enchantment, Math.max(1, integer(entry.getValue(), 1)), true);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        if (target instanceof EnchantmentStorageMeta && meta.get("stored-enchants") instanceof Map) {
            EnchantmentStorageMeta storage = (EnchantmentStorageMeta) target;
            for (Map.Entry<String, Object> entry : castMap(meta.get("stored-enchants")).entrySet()) {
                Enchantment enchantment = enchantment(entry.getKey());
                if (enchantment != null) {
                    try {
                        storage.addStoredEnchant(enchantment, Math.max(1, integer(entry.getValue(), 1)), true);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
        List<ItemFlag> flags = new ArrayList<>();
        for (String name : legacyStrings(meta.get("ItemFlags"))) {
            ItemFlag flag = enumValue(ItemFlag.class, name);
            if (flag != null) {
                flags.add(flag);
            }
        }
        if (!flags.isEmpty()) {
            target.addItemFlags(flags.toArray(new ItemFlag[0]));
        }
        if (Boolean.TRUE.equals(meta.get("Unbreakable"))) {
            target.setUnbreakable(true);
        }
        if (meta.get("custom-model-data") != null) {
            int model = integer(meta.get("custom-model-data"), 0);
            if (model > 0) {
                target.setCustomModelData(model);
            }
        }
        if (meta.get("damage") != null && target instanceof Damageable) {
            ((Damageable) target).setDamage(Math.max(0, integer(meta.get("damage"), 0)));
        }
        if (target instanceof PotionMeta) {
            applyLegacyPotion((PotionMeta) target, meta);
        }
        if (target instanceof LeatherArmorMeta && meta.get("color") != null) {
            ((LeatherArmorMeta) target).setColor(Color.fromRGB(integer(meta.get("color"), 0)));
        }
        if (target instanceof SkullMeta && meta.get("skull-owner") != null) {
            Object owner = meta.get("skull-owner");
            String name = owner instanceof Map ? String.valueOf(castMap(owner).get("name")) : String.valueOf(owner);
            applySkullOwner((SkullMeta) target, name);
        }
        if (target instanceof BookMeta) {
            BookMeta book = (BookMeta) target;
            if (meta.get("title") != null) {
                book.setTitle(legacyComponent(String.valueOf(meta.get("title"))));
            }
            if (meta.get("author") != null) {
                book.setAuthor(legacyComponent(String.valueOf(meta.get("author"))));
            }
            List<String> pages = new ArrayList<>();
            for (String page : legacyStrings(meta.get("pages"))) {
                pages.add(legacyComponent(page));
            }
            if (!pages.isEmpty()) {
                book.setPages(pages);
            }
        }
        if (target instanceof BannerMeta && meta.get("patterns") instanceof Collection) {
            List<Pattern> patterns = new ArrayList<>();
            for (Object value : (Collection<?>) meta.get("patterns")) {
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<String, Object> pattern = castMap(value);
                PatternType type = enumValue(PatternType.class, String.valueOf(pattern.get("pattern")));
                DyeColor color = enumValue(DyeColor.class, String.valueOf(pattern.get("color")));
                if (type != null && color != null) {
                    patterns.add(new Pattern(color, type));
                }
            }
            if (!patterns.isEmpty()) {
                ((BannerMeta) target).setPatterns(patterns);
            }
        }
        try {
            item.setItemMeta(target);
        } catch (Throwable ignored) {
        }
    }

    private static void applyLegacyPotion(PotionMeta meta, Map<String, Object> raw) {
        Object type = raw.get("potion-type");
        if (type != null) {
            String name = String.valueOf(type).trim();
            if (name.contains(":")) {
                name = name.substring(name.indexOf(':') + 1);
            }
            name = name.toUpperCase(Locale.ROOT);
            boolean extended = name.startsWith("LONG_");
            boolean upgraded = name.startsWith("STRONG_");
            PotionType resolved = resolvePotionType(name, extended, upgraded);
            if (resolved != null) {
                applyPotionType(meta, resolved, extended, upgraded);
            }
        }
        if (raw.get("custom-effects") instanceof Collection) {
            List<PotionEffect> effects = new ArrayList<>();
            for (Object value : (Collection<?>) raw.get("custom-effects")) {
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<String, Object> effect = castMap(value);
                Object name = effect.get("effect") != null ? effect.get("effect") : effect.get("type");
                PotionEffectType effectType = effectType(name);
                if (effectType == null) {
                    continue;
                }
                Object particles = effect.get("has-particles") != null
                        ? effect.get("has-particles") : effect.get("particles");
                Object icon = effect.get("has-icon") != null ? effect.get("has-icon") : effect.get("icon");
                PotionEffect parsed = potionEffect(effectType,
                        integer(effect.get("duration"), 200),
                        integer(effect.get("amplifier"), 0),
                        bool(effect.get("ambient"), false),
                        bool(particles, true),
                        bool(icon, true));
                if (parsed != null) {
                    effects.add(parsed);
                }
            }
            if (!effects.isEmpty()) {
                meta.clearCustomEffects();
                for (PotionEffect effect : effects) {
                    meta.addCustomEffect(effect, true);
                }
            }
        }
        if (raw.get("color") != null) {
            meta.setColor(Color.fromRGB(integer(raw.get("color"), 0)));
        }
    }

    /** Bukkit пишет эффект числовым id (effect: 10) — умеем и так. */
    private static PotionEffectType effectType(Object raw) {
        if (raw == null) {
            return null;
        }
        String name = String.valueOf(raw).trim();
        PotionEffectType byName = PotionEffectType.getByName(name);
        if (byName != null) {
            return byName;
        }
        try {
            Method getter = PotionEffectType.class.getMethod("getById", int.class);
            Object type = getter.invoke(null, Integer.parseInt(name));
            if (type instanceof PotionEffectType) {
                return (PotionEffectType) type;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * В Bukkit-сериализации имя и лор лежат JSON-компонентами
     * ({"extra":[{"color":"gold","text":"Меч"}],"text":""}) —
     * превращаем обратно в привычный §-текст, чтобы файл читался человеком.
     */
    private static String legacyComponent(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (!text.startsWith("{")) {
            return value;
        }
        StringBuilder builder = new StringBuilder();
        java.util.regex.Matcher objects = COMPONENT_OBJECT.matcher(text);
        boolean found = false;
        while (objects.find()) {
            found = true;
            appendComponent(builder, objects.group());
        }
        if (!found) {
            java.util.regex.Matcher single = TEXT_FRAGMENT.matcher(text);
            while (single.find()) {
                builder.append(unescapeJson(single.group(1)));
            }
            return builder.length() > 0 ? builder.toString() : value;
        }
        // корневой "text" идёт после массива extra
        int tail = text.lastIndexOf(']');
        if (tail >= 0 && tail < text.length() - 1) {
            java.util.regex.Matcher root = TEXT_FRAGMENT.matcher(text.substring(tail));
            if (root.find()) {
                builder.append(unescapeJson(root.group(1)));
            }
        }
        return builder.length() > 0 ? builder.toString() : value;
    }

    private static void appendComponent(StringBuilder builder, String object) {
        java.util.regex.Matcher textMatcher = TEXT_FRAGMENT.matcher(object);
        if (!textMatcher.find()) {
            return;
        }
        String content = unescapeJson(textMatcher.group(1));
        if (content.isEmpty()) {
            return;
        }
        java.util.regex.Matcher color = COLOR_VALUE.matcher(object);
        if (color.find()) {
            builder.append(colorCode(color.group(1)));
        }
        if (boolValue(object, "obfuscated")) {
            builder.append('\u00a7').append('k');
        }
        if (boolValue(object, "bold")) {
            builder.append('\u00a7').append('l');
        }
        if (boolValue(object, "strikethrough")) {
            builder.append('\u00a7').append('m');
        }
        if (boolValue(object, "underlined")) {
            builder.append('\u00a7').append('n');
        }
        if (boolValue(object, "italic")) {
            builder.append('\u00a7').append('o');
        }
        builder.append(content);
    }

    private static final java.util.regex.Pattern COMPONENT_OBJECT =
            java.util.regex.Pattern.compile("\\{[^{}]*\\}");
    private static final java.util.regex.Pattern COLOR_VALUE =
            java.util.regex.Pattern.compile("\"color\"\\s*:\\s*\"([^\"]+)\"");

    private static boolean boolValue(String object, String key) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(object);
        return matcher.find() && "true".equals(matcher.group(1));
    }

    private static String colorCode(String color) {
        if (color == null) {
            return "";
        }
        String name = color.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("#") && name.length() == 7) {
            StringBuilder builder = new StringBuilder("\u00a7x");
            for (int index = 1; index < name.length(); index++) {
                builder.append('\u00a7').append(name.charAt(index));
            }
            return builder.toString();
        }
        switch (name) {
            case "black": return "\u00a70";
            case "dark_blue": return "\u00a71";
            case "dark_green": return "\u00a72";
            case "dark_aqua": return "\u00a73";
            case "dark_red": return "\u00a74";
            case "dark_purple": return "\u00a75";
            case "gold": return "\u00a76";
            case "gray": return "\u00a77";
            case "grey": return "\u00a77";
            case "dark_gray": return "\u00a78";
            case "blue": return "\u00a79";
            case "green": return "\u00a7a";
            case "aqua": return "\u00a7b";
            case "red": return "\u00a7c";
            case "light_purple": return "\u00a7d";
            case "yellow": return "\u00a7e";
            case "white": return "\u00a7f";
            default: return "";
        }
    }

    private static final java.util.regex.Pattern TEXT_FRAGMENT =
            java.util.regex.Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static String unescapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\' || index + 1 >= value.length()) {
                builder.append(current);
                continue;
            }
            char next = value.charAt(++index);
            switch (next) {
                case 'n':
                    builder.append('\n');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                case 'r':
                    break;
                case 'u':
                    if (index + 4 < value.length()) {
                        try {
                            builder.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                            index += 4;
                        } catch (NumberFormatException exception) {
                            builder.append(next);
                        }
                    }
                    break;
                default:
                    builder.append(next);
                    break;
            }
        }
        return builder.toString();
    }

    private static List<String> legacyStrings(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Collection) {
            for (Object entry : (Collection<?>) value) {
                if (entry != null) {
                    result.add(String.valueOf(entry));
                }
            }
        }
        return result;
    }

    /** Ключи секции: сначала числа по возрастанию (0, 1, 2 ...), потом остальные. */
    private static List<String> orderedKeys(ConfigurationSection section) {
        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> {
            int left = integer(a, Integer.MAX_VALUE);
            int right = integer(b, Integer.MAX_VALUE);
            if (left != right) {
                return Integer.compare(left, right);
            }
            return a.compareTo(b);
        });
        return keys;
    }

    private static List<String> stringList(ConfigurationSection section, String path) {
        List<String> result = new ArrayList<>();
        List<?> raw = section.getList(path);
        if (raw == null) {
            return result;
        }
        for (Object value : raw) {
            if (value != null) {
                result.add(String.valueOf(value));
            }
        }
        return result;
    }

    private static Material material(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String enchantmentKey(Enchantment enchantment) {
        try {
            if (enchantment.getKey() != null) {
                return enchantment.getKey().toString();
            }
        } catch (Throwable ignored) {
        }
        return enchantment.getName();
    }

    /**
     * «minecraft:sharpness:7» -> [minecraft:sharpness, 7]
     * «SHARPNESS=5»           -> [SHARPNESS, 5]
     * «sharpness»             -> [sharpness, 1]
     */
    private static String[] splitEnchantment(String entry) {
        if (entry == null || entry.trim().isEmpty()) {
            return new String[]{"", "1"};
        }
        String clean = entry.trim();
        int cut = Math.max(clean.lastIndexOf(':'), clean.lastIndexOf('='));
        if (cut > 0 && cut < clean.length() - 1) {
            String level = clean.substring(cut + 1).trim();
            if (!level.isEmpty() && level.chars().allMatch(Character::isDigit)) {
                return new String[]{clean.substring(0, cut).trim(), level};
            }
        }
        return new String[]{clean, "1"};
    }

    private static int parseLevel(String[] parts) {
        return Math.max(1, integer(parts.length > 1 ? parts[1] : "1", 1));
    }

    private static Enchantment enchantment(String key) {
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        String clean = key.trim().toLowerCase(Locale.ROOT);
        try {
            NamespacedKey namespaced = clean.contains(":")
                    ? NamespacedKey.fromString(clean)
                    : NamespacedKey.minecraft(clean);
            if (namespaced != null) {
                Enchantment byKey = Enchantment.getByKey(namespaced);
                if (byKey != null) {
                    return byKey;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            @SuppressWarnings("deprecation")
            Enchantment byName = Enchantment.getByName(clean.toUpperCase(Locale.ROOT));
            if (byName != null) {
                return byName;
            }
        } catch (Throwable ignored) {
        }
        for (Enchantment candidate : new LinkedHashSet<>(Arrays.asList(Enchantment.values()))) {
            if (candidate != null && (candidate.getName().equalsIgnoreCase(clean)
                    || (candidate.getKey() != null && candidate.getKey().getKey().equalsIgnoreCase(clean)))) {
                return candidate;
            }
        }
        return null;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static int integer(Object value, int def) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private static boolean bool(Object value, boolean def) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return def;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    private static double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Throwable exception) {
            return 0.0D;
        }
    }

    /* ───────────────────────── рефлексия ───────────────────────── */

    private static Object invoke(Object target, String method) {
        try {
            Method found = findMethod(target.getClass(), method);
            return found == null ? null : found.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static Object invokeConstructor(String className, Class<?>[] signature, Object... args) {
        try {
            Constructor<?> constructor = Class.forName(className).getDeclaredConstructor(signature);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
