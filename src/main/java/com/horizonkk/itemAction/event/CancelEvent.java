package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.manager.ChargeManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class CancelEvent implements Listener {
    private final ItemAction plugin;
    private final ChargeManager chargeManager; // 新增共享读条管理器

    public CancelEvent(ItemAction plugin) {
        this.plugin = plugin;
        this.chargeManager = plugin.getChargeManager(); // 获取共享读条管理器
    }

    /**
     * 监听物品切换事件，用于重置读条
     */
    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 如果正在读条，取消读条（使用共享管理器）
        if (chargeManager.isPlayerCharging(playerId)) {
            chargeManager.cancelCharging(playerId);
            // player.sendMessage(ChatColor.RED + "读条已取消");
        }
    }

    /**
     * 监听物品丢弃事件，用于取消读条
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // 如果正在读条，检查是否影响当前读条（使用共享管理器）
        if (chargeManager.isPlayerCharging(playerId)) {
            // 获取玩家当前主手物品
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();

            // 如果主手物品与丢弃物品相同，或者主手变为空，则取消读条
            if (mainHandItem == null || mainHandItem.getType() == Material.AIR ||
                    mainHandItem.isSimilar(droppedItem)) {
                chargeManager.cancelCharging(playerId);
                // player.sendMessage(ChatColor.RED + "物品已丢弃，读条取消");
            }
        }
    }

    /**
     * 监听物品点击事件（包括背包操作），用于取消读条
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        // 如果正在读条，取消读条（使用共享管理器）
        if (chargeManager.isPlayerCharging(playerId)) {
            chargeManager.cancelCharging(playerId);
            // player.sendMessage(ChatColor.RED + "读条期间不能操作背包");
        }
    }

    /**
     * 监听玩家退出事件，清理读条数据
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 使用共享管理器清理读条数据
        if (chargeManager.isPlayerCharging(playerId)) {
            chargeManager.cancelCharging(playerId);
        }
    }

    /**
     * 监听玩家移动事件，如果配置不允许移动则取消读条
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 如果正在读条，检查是否允许移动
        if (chargeManager.isPlayerCharging(playerId)) {
            // 检查该读条是否允许移动
            if (!chargeManager.isMoveAllowedForPlayer(playerId)) {
                // 检查是否有实际的位置移动（不仅仅是视角转动）
                if (event.getFrom().getX() != event.getTo().getX() ||
                        event.getFrom().getY() != event.getTo().getY() ||
                        event.getFrom().getZ() != event.getTo().getZ()) {
                    // 玩家移动了位置，取消读条
                    chargeManager.cancelCharging(playerId);
                    // player.sendMessage(ChatColor.RED + "移动已取消读条");
                }
            }
        }
    }
}
