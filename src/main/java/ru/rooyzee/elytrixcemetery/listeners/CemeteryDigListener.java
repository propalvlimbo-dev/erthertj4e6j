package ru.rooyzee.elytrixcemetery.listeners;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixcemetery.Main;
import ru.rooyzee.elytrixcemetery.graves.Grave;
import ru.rooyzee.elytrixcemetery.utils.ColorUtil;

public class CemeteryDigListener implements Listener {

    private final Main plugin;

    public CemeteryDigListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Grave grave = plugin.getGraveManager().getGraveAt(block.getLocation());
        if (grave == null) {
            grave = plugin.getGraveManager().getGraveByChest(block.getLocation());
        }
        if (grave == null) return;

        Player player = event.getPlayer();

        if (grave.isDug()) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Разрешаем открыть сундук раскопанной могилы
                event.setCancelled(true);
                grave.openChestFor(player);
            }
            return;
        }

        event.setCancelled(true);
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !hand.getType().name().endsWith("_SHOVEL")) {
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cЧтобы раскопать могилу, возьмите в руку лопату!"));
            return;
        }

        if (grave.dig(plugin, player)) {
            player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aВы успешно раскопали могилу! Заберите сокровища из сундука."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Grave grave = plugin.getGraveManager().getGraveAt(block.getLocation());
        if (grave == null) {
            grave = plugin.getGraveManager().getGraveByChest(block.getLocation());
        }
        if (grave != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            if (grave.isDug()) {
                grave.openChestFor(player);
                return;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || !hand.getType().name().endsWith("_SHOVEL")) {
                player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &cЧтобы раскопать могилу, используйте лопату!"));
                return;
            }
            if (grave.dig(plugin, player)) {
                player.sendMessage(ColorUtil.colorize("&f☁ &#F8BEFBᴇʟʏᴛʀɪx &7» &aВы успешно раскопали могилу! Заберите сокровища из сундука."));
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        plugin.getGhostController().handleDeath(event);
    }
}
