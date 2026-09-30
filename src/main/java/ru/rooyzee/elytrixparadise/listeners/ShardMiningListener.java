package ru.rooyzee.elytrixparadise.listeners;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import ru.rooyzee.elytrixparadise.Main;
import ru.rooyzee.elytrixparadise.shards.ParadiseShard;
import ru.rooyzee.elytrixparadise.sphere.ChainNode;
import ru.rooyzee.elytrixparadise.sphere.SphereManager;
import ru.rooyzee.elytrixparadise.sphere.SphereState;
import ru.rooyzee.elytrixparadise.utils.ColorUtil;

public class ShardMiningListener implements Listener {

    private final Main plugin;

    public ShardMiningListener(Main plugin) {
        this.plugin = plugin;
    }

    private boolean isShardBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        if (type == Material.RED_GLAZED_TERRACOTTA || type == Material.GRAY_GLAZED_TERRACOTTA) {
            return true;
        }
        return plugin.getShardManager().getShard(block.getLocation()) != null;
    }

    /**
     * Предотвращение мгновенного разрушения блоков в креативе при ЛКМ.
     * Урон и добыча на ЛКМ НЕ происходят — добыча строго при полном ломании!
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block block = event.getClickedBlock();
            Player player = event.getPlayer();
            SphereManager sm = plugin.getSphereManager();

            if (player.getGameMode() == GameMode.CREATIVE) {
                if (sm != null && block.getType() == Material.CHAIN && sm.getChainAt(block.getLocation()) != null) {
                    event.setCancelled(true);
                    return;
                }
                if (sm != null && sm.isSphereBlock(block.getLocation())) {
                    event.setCancelled(true);
                    return;
                }
                if (isShardBlock(block)) {
                    event.setCancelled(true);
                    resendShardBlock(block);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockBreakLowest(BlockBreakEvent event) {
        Block block = event.getBlock();
        SphereManager sm = plugin.getSphereManager();

        if (sm != null) {
            if (block.getType() == Material.CHAIN && sm.getChainAt(block.getLocation()) != null) {
                event.setCancelled(true);
                event.setDropItems(false);
                event.setExpToDrop(0);
                return;
            }
            if (sm.getState() == SphereState.FALLEN_MINING && sm.isSphereBlock(block.getLocation())) {
                event.setCancelled(true);
                event.setDropItems(false);
                event.setExpToDrop(0);
                return;
            }
        }

        if (isShardBlock(block)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
        }
    }

    /**
     * ТОЛЬКО полное вскапывание/ломание (BlockBreakEvent) засчитывает добычу и наносит урон!
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        final Block block = event.getBlock();
        final Player player = event.getPlayer();
        SphereManager sm = plugin.getSphereManager();

        // 1. Полное ломание цепи Сердца Рая
        if (sm != null && block.getType() == Material.CHAIN) {
            ChainNode chain = sm.getChainAt(block.getLocation());
            if (chain != null && !chain.isBroken()) {
                event.setCancelled(true);
                event.setDropItems(false);
                event.setExpToDrop(0);
                sm.handleChainBreak(player, block.getLocation());
                return;
            }
        }

        // 2. Полное ломание упавшей сферы Сердца Рая
        if (sm != null && sm.getState() == SphereState.FALLEN_MINING && sm.isSphereBlock(block.getLocation())) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);
            sm.handleFallenSphereBreak(player, block.getLocation());
            return;
        }

        // 3. Полное ломание Осколка Рая
        if (isShardBlock(block)) {
            event.setCancelled(true);
            event.setDropItems(false);
            event.setExpToDrop(0);

            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }

            if (shard != null) {
                plugin.getShardManager().handleShardMined(player, block.getLocation());
            }

            resendShardBlock(block);
        }
    }

    private void resendShardBlock(final Block block) {
        final org.bukkit.Location loc = block.getLocation();
        final ParadiseShard s = plugin.getShardManager().getShard(loc);
        final Material targetMat = (s != null && s.getState() == ParadiseShard.ShardState.COOLDOWN)
                ? Material.GRAY_GLAZED_TERRACOTTA
                : Material.RED_GLAZED_TERRACOTTA;

        block.setType(targetMat, false);
        block.getState().update(true, true);

        for (long delay : new long[]{0L, 1L, 2L, 4L}) {
            Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
                @Override
                public void run() {
                    if (loc.getWorld() != null) {
                        Block current = loc.getBlock();
                        current.setType(targetMat, false);
                        current.getState().update(true, true);
                        for (Player p : loc.getWorld().getPlayers()) {
                            if (p.getLocation().distanceSquared(loc) <= 64 * 64) {
                                p.sendBlockChange(loc, targetMat.createBlockData());
                            }
                        }
                    }
                }
            }, delay);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        SphereManager sm = plugin.getSphereManager();

        if (isShardBlock(block)) {
            ParadiseShard shard = plugin.getShardManager().getShard(block.getLocation());
            if (shard == null) {
                shard = plugin.getShardManager().registerShard(block.getLocation());
            }
            if (shard != null && shard.getState() == ParadiseShard.ShardState.COOLDOWN) {
                String prefix = plugin.getConfigManager().getAdminPrefix();
                player.sendMessage(ColorUtil.colorize(prefix + "&cОсколок находится на перезарядке! &fОсталось: &#F8BEFB"
                        + ColorUtil.formatTimeShort(shard.getCooldownRemaining())));
            }
        }
    }
}
