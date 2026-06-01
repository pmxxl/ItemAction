package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.manager.ActionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;

public class HandTick implements Listener {
    private final ItemAction plugin;
    private final ActionManager actionManager;

    public HandTick(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
        startHandCheckTask();
    }

    // 定时检查玩家手持物品
    private void startHandCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    checkPlayerHand(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // 每秒检查一次
    }

    private void checkPlayerHand(Player player) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand != null) {
            String itemId = actionManager.getItemIdFromItem(itemInHand);
            // 检查配置文件中是否有此物品（Action.yml或group.yml）
            if (actionManager.hasConfigForItem(itemInHand)) {
                // 获取有效配置
                Map<String, Object> effectiveConfig = actionManager.getEffectiveConfig(itemInHand);
                if (effectiveConfig != null) {
                    // 保留变量功能，但不显示消息
                    // 变量功能通过PlaceholderAPI提供
                }
            }
        }
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        // 当玩家切换物品栏时立即检查
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            checkPlayerHand(event.getPlayer());
        }, 1L);
    }
}
