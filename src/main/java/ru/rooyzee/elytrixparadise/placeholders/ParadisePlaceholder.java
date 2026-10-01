package ru.rooyzee.elytrixparadise.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.sphere.SphereManager;
import ru.rooyzee.elytrixparadise.sphere.SphereState;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

public class ParadisePlaceholder extends PlaceholderExpansion {

    private final Main plugin;
    private final String identifier;

    public ParadisePlaceholder(Main plugin) {
        this(plugin, "elytrixparadise");
    }

    public ParadisePlaceholder(Main plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "rooyzee";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        return handlePlaceholder(params);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return handlePlaceholder(params);
    }

    private String handlePlaceholder(String params) {
        if (params == null) return null;
        String param = params.toLowerCase().replace("-", "_");

        SphereManager sm = plugin.getSphereManager();
        if (sm == null) return "";

        boolean isCooldown = sm.getState() == SphereState.COOLDOWN;
        int cd = sm.getCooldownRemaining();

        // 1. %elytrixparadise_status%
        if (param.equals("status")) {
            if (isCooldown) {
                return ColorUtil.colorize("&cПерезарядка &8(&#F8BEFB" + ColorUtil.formatTimeShort(cd) + "&8)");
            } else if (sm.getState() == SphereState.FALLEN_MINING) {
                return ColorUtil.colorize("&eДобыча Сердца");
            } else if (sm.getState() == SphereState.FALLING) {
                return ColorUtil.colorize("&6Падение Сердца");
            } else if (sm.getState() == SphereState.INTACT) {
                return ColorUtil.colorize("&aАктивен &8(&f6 цепей&8)");
            } else {
                return ColorUtil.colorize("&aАктивен");
            }
        }

        // 2. %elytrixparadise_status_simple%
        if (param.equals("status_simple") || param.equals("active")) {
            if (isCooldown) {
                return ColorUtil.colorize("&cПерезарядка");
            }
            return ColorUtil.colorize("&aАктивен");
        }

        // 3. %elytrixparadise_time%
        if (param.equals("time") || param.equals("time_short") || param.equals("timer")) {
            if (isCooldown) {
                return ColorUtil.formatTimeShort(cd);
            }
            return ColorUtil.colorize("&aИдет сейчас");
        }

        // 4. %elytrixparadise_time_pretty%
        if (param.equals("time_pretty") || param.equals("pretty_time")) {
            if (isCooldown) {
                return ColorUtil.formatTimePretty(cd);
            }
            return ColorUtil.colorize("&aИдет сейчас");
        }

        // 5. %elytrixparadise_time_seconds%
        if (param.equals("time_seconds") || param.equals("seconds")) {
            return String.valueOf(isCooldown ? cd : 0);
        }

        // 6. %elytrixparadise_state%
        if (param.equals("state")) {
            return sm.getState().name();
        }

        // 7. %elytrixparadise_chains_alive%
        if (param.equals("chains_alive") || param.equals("alive_chains")) {
            return String.valueOf(sm.getAliveChainsCount());
        }

        // 8. %elytrixparadise_chains_total%
        if (param.equals("chains_total") || param.equals("total_chains")) {
            return String.valueOf(sm.getChains().size());
        }

        // 9. %elytrixparadise_chains_broken%
        if (param.equals("chains_broken") || param.equals("broken_chains")) {
            return String.valueOf(sm.getBrokenChainsCount());
        }

        // 10. %elytrixparadise_shards_active%
        if (param.equals("shards_active") || param.equals("active_shards")) {
            return String.valueOf(plugin.getShardManager().getActiveShardsCount());
        }

        // 11. %elytrixparadise_shards_total%
        if (param.equals("shards_total") || param.equals("total_shards")) {
            return String.valueOf(plugin.getShardManager().getAllShards().size());
        }

        // 12. %elytrixparadise_sphere_hp%
        if (param.equals("sphere_hp")) {
            return String.valueOf(sm.getCurrentSphereHp());
        }

        // 13. %elytrixparadise_sphere_max_hp%
        if (param.equals("sphere_max_hp")) {
            return String.valueOf(sm.getMaxSphereHp());
        }

        // 14. %elytrixparadise_sphere_phase%
        if (param.equals("sphere_phase") || param.equals("explosions") || param.equals("phase")) {
            return sm.getExplosionCount() + "/" + sm.getMaxExplosions();
        }

        return null;
    }
}
