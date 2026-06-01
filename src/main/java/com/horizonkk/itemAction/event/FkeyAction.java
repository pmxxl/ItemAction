package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.api.ChargeAPI;
import com.horizonkk.itemAction.manager.ActionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class FkeyAction implements Listener {
    private final ItemAction plugin;
    private final ActionManager actionManager;
    private final ChargeAPI chargeManager;

    public FkeyAction(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
        this.chargeManager = plugin.getChargeManager();
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        // 取消默认的副手切换行为
        event.setCancelled(true);

        // 【优化#1】空值检查置顶
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem == null || mainHandItem.getType() == Material.AIR) {
            return;
        }

        // 【优化#2】物品ID只计算一次
        String itemId = actionManager.getItemIdFromItem(mainHandItem);

        // 【优化#3】有效配置只解析一次
        Map<String, Object> config = actionManager.getEffectiveConfig(mainHandItem, itemId);
        if (config == null) {
            return;
        }

        // 【优化#4】直接从已解析的config判断动作
        if (!ActionManager.configHasAction(config, "F")) {
            return;
        }

        // 权限检查
        if (ActionManager.hasPermissionRestriction(config, player)) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // 读条中则阻止重复触发
        if (chargeManager.isPlayerCharging(playerId)) {
            return;
        }

        // 读条时间
        int chargeTime = ActionManager.getChargeTime(config);
        if (chargeTime > 0) {
            chargeManager.startCharging(player, itemId, chargeTime, "F");
        } else {
            executeFAction(player, itemId, mainHandItem, config);
        }
    }

    /**
     * 执行F键动作（使用已解析的配置）
     */
    public void executeFAction(Player player, String itemId, ItemStack itemInHand, Map<String, Object> itemConfig) {
        if (itemConfig != null) {
            executeConfiguredCommands(player, itemConfig, itemId);
            handleItemDeduction(player, itemInHand, itemConfig);
            sendCompletionMessage(player, itemId, itemInHand);
        }
    }

    /**
     * 执行F键动作（自动解析配置，用于外部调用如ChargeManager）
     */
    public void executeFAction(Player player, String itemId, ItemStack itemInHand) {
        Map<String, Object> config = actionManager.getEffectiveConfig(itemInHand, itemId);
        executeFAction(player, itemId, itemInHand, config);
    }

    private void executeConfiguredCommands(Player player, Map<String, Object> itemConfig, String itemId) {
        Object commandObj = itemConfig.get("command");
        if (commandObj == null) return;

        if (commandObj instanceof String) {
            String command = ((String) commandObj).replace("%itemid%", itemId);
            executeSingleCommand(player, command);
        } else if (commandObj instanceof Iterable) {
            for (Object cmdObj : (Iterable<?>) commandObj) {
                if (cmdObj instanceof String) {
                    String command = ((String) cmdObj).replace("%itemid%", itemId);
                    executeSingleCommand(player, command);
                }
            }
        }
    }

    private void executeSingleCommand(Player player, String command) {
        String processedCommand = command
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());

        if (processedCommand.endsWith(" as op")) {
            executeAsOp(player, processedCommand.substring(0, processedCommand.length() - 6));
        } else if (processedCommand.endsWith(" as console")) {
            executeAsConsole(processedCommand.substring(0, processedCommand.length() - 11));
        } else {
            player.performCommand(processedCommand);
        }
    }

    private void executeAsOp(Player player, String command) {
        boolean wasOp = player.isOp();
        try {
            player.setOp(true);
            player.performCommand(command);
        } finally {
            if (!wasOp) {
                player.setOp(false);
            }
        }
    }

    private void executeAsConsole(String command) {
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
    }

    private void handleItemDeduction(Player player, ItemStack itemInHand, Map<String, Object> itemConfig) {
        Object amountObj = itemConfig.get("amount");
        if (amountObj instanceof Number) {
            int amountToDeduct = ((Number) amountObj).intValue();
            if (amountToDeduct == 1 && itemInHand != null && itemInHand.getAmount() > 0) {
                int currentAmount = itemInHand.getAmount();
                if (currentAmount > 1) {
                    itemInHand.setAmount(currentAmount - 1);
                } else {
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
            }
        }
    }

    private void sendCompletionMessage(Player player, String itemId, ItemStack item) {
        String message = actionManager.getActionMessage(itemId);
        if (message != null && !message.isEmpty()) {
            String itemName = actionManager.getItemDisplayName(item);
            String processed = ChatColor.translateAlternateColorCodes('&', message)
                    .replace("%player%", player.getName())
                    .replace("{name}", itemName);
            player.sendMessage(processed);
        }
    }

    // 委托给共享读条管理器
    public int getChargeProgressPercent(UUID playerId) {
        return chargeManager.getChargeProgressPercent(playerId);
    }

    public boolean isPlayerCharging(UUID playerId) {
        return chargeManager.isPlayerCharging(playerId);
    }
}
