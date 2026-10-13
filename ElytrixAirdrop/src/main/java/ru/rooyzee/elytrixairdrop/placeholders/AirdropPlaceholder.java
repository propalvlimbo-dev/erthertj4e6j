package ru.rooyzee.elytrixairdrop.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixairdrop.Main;
import ru.rooyzee.elytrixairdrop.airdrops.ActiveAirdrop;
import ru.rooyzee.elytrixairdrop.utils.ColorUtil;

public class AirdropPlaceholder extends PlaceholderExpansion {

    private final Main plugin;

    public AirdropPlaceholder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "elytrixairdrop";
    }

    @Override
    public String getAuthor() {
        return "rooyzee";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        ActiveAirdrop a = plugin.getAirdropManager().getActive();

        if (a != null) {
            // Активен
            String typeId = a.getType().getId();
            String display = ColorUtil.colorize(plugin.getConfigManager().getConfig()
                    .getString("airdrops." + typeId + ".display-name", typeId));
            return ColorUtil.colorize(
                    plugin.getConfigManager().getMessages()
                            .getString("placeholder.active", "&aактивен: {type}")
                            .replace("{type}", display)
            );
        }

        // Не активен — время до следующего спавна
        long nextSec = plugin.getAirdropManager().getNextSpawnSeconds();

        String timeText;
        if (nextSec <= 0) {
            timeText = ColorUtil.colorize("&aскоро");
        } else {
            timeText = plugin.getAirdropManager().formatTime(nextSec);
        }

        return ColorUtil.colorize(
                plugin.getConfigManager().getMessages()
                        .getString("placeholder.no-active", "&fдо спавна: {time}")
                        .replace("{time}", timeText)
        );
    }
}