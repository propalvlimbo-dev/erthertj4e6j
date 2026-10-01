package ru.rooyzee.elytrixtrader.currency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.CostConfig;
import ru.rooyzee.elytrixtrader.util.Numbers;
import ru.rooyzee.elytrixtrader.util.Text;

public class CurrencyService {

    private final Main plugin;
    private Economy economy;
    private String coinsSpendCommand;
    private String coinsPlaceholder;

    public CurrencyService(Main plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        economy = null;
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (provider != null) {
                economy = provider.getProvider();
            }
        }
        coinsPlaceholder = plugin.config().coinsPlaceholder();
        coinsSpendCommand = plugin.config().coinsSpendCommand();
        if (economy == null && plugin.config().debug()) {
        }
    }

    public boolean hasEconomy() {
        return economy != null;
    }

    public double coins(Player player) {
        String raw = Text.applyPapi(coinsPlaceholder, player);
        String digits = raw == null ? "" : raw.replaceAll("[^0-9.,\\-]", "");
        return Numbers.parseDouble(digits, 0.0D);
    }

    public double balance(Player player, CostConfig.Type type) {
        switch (type) {
            case EXPERIENCE_LEVELS:
                return player.getLevel();
            case EXPERIENCE_POINTS:
                return totalExperience(player);
            case VAULT:
                return economy == null ? 0.0D : economy.getBalance(player);
            case COINS:
                return economy != null && coinsPlaceholder == null ? economy.getBalance(player) : coins(player);
            default:
                return Integer.MAX_VALUE;
        }
    }

    public int countItem(Player player, Material material) {
        if (material == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() != material) {
                continue;
            }
            if (item.getEnchantments() != null && !item.getEnchantments().isEmpty()) {
                continue;
            }
            total += item.getAmount();
        }
        return total;
    }

    /**
     * Сколько у игрока предметов под эту цену.
     * Если цена задана точным предметом (зелье, зачарованная книга, переименованный предмет),
     * засчитываются только полностью такие же стаки (ItemStack.isSimilar).
     */
    public int countItem(Player player, CostConfig cost) {
        if (player == null || cost == null) {
            return 0;
        }
        if (cost.exactItem()) {
            return countItem(player, cost.rawItem());
        }
        return countItem(player, cost.material());
    }

    public int countItem(Player player, ItemStack template) {
        if (player == null || template == null || template.getType() == Material.AIR) {
            return 0;
        }
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (item.isSimilar(template)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    /** Снимает предметы под эту цену: точные — только идентичные, обычные — по материалу. */
    public void removeItem(Player player, CostConfig cost, int amount) {
        if (player == null || cost == null || amount <= 0) {
            return;
        }
        if (cost.exactItem()) {
            removeItem(player, cost.rawItem(), amount);
            return;
        }
        removeItem(player, cost.material(), amount);
    }

    public void removeItem(Player player, ItemStack template, int amount) {
        if (player == null || template == null || template.getType() == Material.AIR || amount <= 0) {
            return;
        }
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && left > 0; index++) {
            ItemStack item = contents[index];
            if (item == null || item.getType() == Material.AIR || !item.isSimilar(template)) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            left -= take;
            if (item.getAmount() - take <= 0) {
                player.getInventory().setItem(index, null);
            } else {
                item.setAmount(item.getAmount() - take);
                player.getInventory().setItem(index, item);
            }
        }
        player.updateInventory();
    }

    public void removeItem(Player player, Material material, int amount) {
        int left = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int index = 0; index < contents.length && left > 0; index++) {
            ItemStack item = contents[index];
            if (item == null || item.getType() != material) {
                continue;
            }
            if (item.getEnchantments() != null && !item.getEnchantments().isEmpty()) {
                continue;
            }
            int take = Math.min(left, item.getAmount());
            left -= take;
            if (item.getAmount() - take <= 0) {
                player.getInventory().setItem(index, null);
            } else {
                item.setAmount(item.getAmount() - take);
                player.getInventory().setItem(index, item);
            }
        }
        player.updateInventory();
    }

    public boolean canAfford(Player player, CostConfig cost, double amount) {
        switch (cost.type()) {
            case ITEM:
                return countItem(player, cost) >= (int) amount;
            default:
                return balance(player, cost.type()) + 0.001D >= amount;
        }
    }

    public void pay(Player player, CostConfig cost, double amount) {
        switch (cost.type()) {
            case EXPERIENCE_LEVELS:
                player.setLevel(Math.max(0, player.getLevel() - (int) amount));
                break;
            case EXPERIENCE_POINTS:
                setTotalExperience(player, totalExperience(player) - (int) Math.ceil(amount));
                break;
            case VAULT:
                if (economy != null) {
                    economy.withdrawPlayer(player, amount);
                }
                break;
            case COINS:
                if (economy != null && coinsSpendCommand == null) {
                    economy.withdrawPlayer(player, amount);
                } else if (coinsSpendCommand != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), coinsSpendCommand
                            .replace("{player}", player.getName())
                            .replace("%player%", player.getName())
                            .replace("{amount}", String.valueOf((long) Math.ceil(amount)))
                            .replace("%amount%", String.valueOf((long) Math.ceil(amount))));
                }
                break;
            case ITEM:
                removeItem(player, cost, (int) amount);
                break;
            default:
                break;
        }
    }

    public void reward(Player player, CostConfig.Type type, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        switch (type) {
            case EXPERIENCE_LEVELS:
                player.giveExpLevels((int) amount);
                break;
            case EXPERIENCE_POINTS:
                setTotalExperience(player, totalExperience(player) + (int) amount);
                break;
            case VAULT:
            case COINS:
                if (economy != null) {
                    economy.depositPlayer(player, amount);
                }
                break;
            default:
                break;
        }
    }

    public String format(Player player, CostConfig cost, double amount) {
        String value;
        switch (cost.type()) {
            case EXPERIENCE_LEVELS:
                value = Numbers.amount((long) amount) + " ур.";
                break;
            case EXPERIENCE_POINTS:
                value = Numbers.amount((long) Math.ceil(amount)) + " очк.";
                break;
            case VAULT:
                value = economy == null ? Numbers.amount(amount) : economy.format(amount);
                break;
            case COINS:
                value = Numbers.amount(amount) + " коинов";
                break;
            case ITEM:
            default:
                value = Numbers.amount((long) amount) + " " + cost.itemName();
                break;
        }
        return value;
    }

    public String balanceText(Player player, CostConfig cost) {
        if (cost == null) {
            return "-";
        }
        switch (cost.type()) {
            case ITEM:
                int count = countItem(player, cost);
                return count + " " + cost.itemName();
            case VAULT:
                return economy == null ? "-" : economy.format(balance(player, cost.type()));
            case EXPERIENCE_LEVELS:
            case EXPERIENCE_POINTS:
            case COINS:
                return format(player, cost, balance(player, cost.type()));
            default:
                return Numbers.amount((long) balance(player, cost.type()));
        }
    }

    public void give(Player player, ItemStack item) {
        if (item == null) {
            return;
        }
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    public int totalExperience(Player player) {
        if (player == null) {
            return 0;
        }
        int level = player.getLevel();
        long total;
        if (level <= 16) {
            total = (long) level * level + 6L * level;
        } else if (level <= 31) {
            total = (long) (2.5D * level * level - 40.5D * level + 360.0D);
        } else {
            total = (long) (4.5D * level * level - 162.5D * level + 2220.0D);
        }
        return (int) (total + Math.round(player.getExp() * player.getExpToLevel()));
    }

    public void setTotalExperience(Player player, int totalExp) {
        if (player == null) {
            return;
        }
        int exp = Math.max(0, totalExp);
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0F);
        if (exp <= 0) {
            return;
        }
        int level = 0;
        while (true) {
            int nextLevelExp;
            if (level <= 15) {
                nextLevelExp = 2 * level + 7;
            } else if (level <= 30) {
                nextLevelExp = 5 * level - 38;
            } else {
                nextLevelExp = 9 * level - 158;
            }
            if (exp >= nextLevelExp) {
                exp -= nextLevelExp;
                level++;
            } else {
                break;
            }
        }
        player.setLevel(level);
        int expToNext;
        if (level <= 15) {
            expToNext = 2 * level + 7;
        } else if (level <= 30) {
            expToNext = 5 * level - 38;
        } else {
            expToNext = 9 * level - 158;
        }
        player.setExp((float) exp / (float) expToNext);
    }

    public Economy vault() {
        return economy;
    }

    public boolean accepts(CostConfig.Type type) {
        if (type == CostConfig.Type.VAULT) {
            return economy != null;
        }
        if (type == CostConfig.Type.COINS) {
            return economy != null || coinsSpendCommand != null || coinsPlaceholder != null;
        }
        return type != null;
    }

    public String currencyName(CostConfig.Type type) {
        switch (type) {
            case EXPERIENCE_LEVELS:
                return "опыт";
            case EXPERIENCE_POINTS:
                return "очки опыта";
            case VAULT:
                return "баланс";
            case COINS:
                return "коинов";
            default:
                return "предметы";
        }
    }

}
