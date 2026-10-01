package ru.rooyzee.elytrixtrader.gui;

import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtrader.Main;
import ru.rooyzee.elytrixtrader.model.TradeConfig;
import ru.rooyzee.elytrixtrader.model.TraderInstance;

import java.util.List;

// BMenu отключён: см. подробности в README. Используем встроенное меню.
public class BmenuSupport {

    public BmenuSupport(Main plugin) {}

    public boolean available() { return false; }

    public boolean open(Player player, TraderInstance instance, List<TradeConfig> trades) {
        return false;
    }
}
