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
import java.util.Locale;
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

        // 权限检查 —— permission: 拥有该权限时阻止
        if (ActionManager.hasPermissionRestriction(config, player)) {
            return;
        }

        // 无权限检查 —— nopermission: 没有该权限时阻止（静默，不发送聊天消息，nopermission-text仅用于PlaceholderAPI的actionbar显示）
        if (ActionManager.hasNopermissionRestriction(config, player)) {
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
        if (itemConfig == null) return;

        // 执行前重新检查nopermission（读条期间权限可能发生变化，静默阻止）
        if (ActionManager.hasNopermissionRestriction(itemConfig, player)) {
            return;
        }

        boolean commandExecuted = executeConfiguredCommands(player, itemConfig, itemId);
        handleItemDeduction(player, itemInHand, itemConfig);
        sendCompletionMessage(player, itemId, itemInHand, itemConfig);
        if (commandExecuted && shouldCallLingQiAbsorbEvent(itemConfig)) {
            callLingQiAbsorbEvent(player, itemId, itemConfig);
        }
        tryStartLoop(player, itemId, itemConfig);
    }

    /**
     * 执行F键动作（自动解析配置，用于外部调用如ChargeManager）
     */
    public void executeFAction(Player player, String itemId, ItemStack itemInHand) {
        Map<String, Object> config = actionManager.getEffectiveConfig(itemInHand, itemId);
        executeFAction(player, itemId, itemInHand, config);
    }

    private void tryStartLoop(Player player, String itemId, Map<String, Object> completedConfig) {
        if (!ActionManager.isLoopEnabled(completedConfig)) {
            return;
        }

        if (getConfiguredDeductionAmount(completedConfig) != 1) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (chargeManager.isPlayerCharging(playerId)) {
            return;
        }

        ItemStack nextItem = player.getInventory().getItemInMainHand();
        if (nextItem == null || nextItem.getType() == Material.AIR) {
            return;
        }

        String nextItemId = actionManager.getItemIdFromItem(nextItem);
        if (!itemId.equals(nextItemId)) {
            return;
        }

        Map<String, Object> nextConfig = actionManager.getEffectiveConfig(nextItem, nextItemId);
        if (nextConfig == null
                || !ActionManager.isLoopEnabled(nextConfig)
                || !ActionManager.configHasAction(nextConfig, "F")
                || ActionManager.hasPermissionRestriction(nextConfig, player)
                || ActionManager.hasNopermissionRestriction(nextConfig, player)
                || getConfiguredDeductionAmount(nextConfig) != 1) {
            return;
        }

        int chargeTime = ActionManager.getChargeTime(nextConfig);
        if (chargeTime <= 0) {
            return;
        }

        chargeManager.startCharging(player, nextItemId, chargeTime, "F");
    }

    private boolean executeConfiguredCommands(Player player, Map<String, Object> itemConfig, String itemId) {
        Object commandObj = itemConfig.get("command");
        if (commandObj == null) return false;

        if (commandObj instanceof String) {
            String command = ((String) commandObj).replace("%itemid%", itemId);
            executeSingleCommand(player, command);
            return true;
        } else if (commandObj instanceof Iterable) {
            boolean executed = false;
            for (Object cmdObj : (Iterable<?>) commandObj) {
                if (cmdObj instanceof String) {
                    String command = ((String) cmdObj).replace("%itemid%", itemId);
                    executeSingleCommand(player, command);
                    executed = true;
                }
            }
            return executed;
        }
        return false;
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

    private boolean shouldCallLingQiAbsorbEvent(Map<String, Object> itemConfig) {
        Object progressAction = firstConfiguredValue(itemConfig,
                "progress-action",
                "progressAction",
                "dao-progress",
                "daoProgress");
        if (progressAction != null) {
            String action = String.valueOf(progressAction);
            return "lingqi_absorb".equalsIgnoreCase(action)
                    || "spirit_stone_absorb".equalsIgnoreCase(action);
        }

        return commandContainsLingQi(itemConfig.get("command"));
    }

    private Object firstConfiguredValue(Map<String, Object> itemConfig, String... keys) {
        for (String key : keys) {
            Object value = itemConfig.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private boolean commandContainsLingQi(Object commandObj) {
        if (commandObj instanceof String) {
            return containsLingQiToken((String) commandObj);
        }
        if (commandObj instanceof Iterable) {
            for (Object cmdObj : (Iterable<?>) commandObj) {
                if (cmdObj instanceof String && containsLingQiToken((String) cmdObj)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsLingQiToken(String command) {
        String normalized = " " + command.toLowerCase(Locale.ROOT).replace('\t', ' ') + " ";
        return normalized.contains(" lingqi ");
    }

    private void callLingQiAbsorbEvent(Player player, String itemId, Map<String, Object> itemConfig) {
        plugin.getServer().getPluginManager().callEvent(
                new LingQiAbsorbEvent(player, itemId, getConsumedAmount(itemConfig)));
    }

    private int getConsumedAmount(Map<String, Object> itemConfig) {
        Object amountObj = itemConfig.get("amount");
        if (amountObj instanceof Number) {
            return Math.max(0, ((Number) amountObj).intValue());
        }
        return 1;
    }

    private void handleItemDeduction(Player player, ItemStack itemInHand, Map<String, Object> itemConfig) {
        int amountToDeduct = getConfiguredDeductionAmount(itemConfig);
        if (amountToDeduct == 1 && itemInHand != null && itemInHand.getAmount() > 0) {
            int currentAmount = itemInHand.getAmount();
            if (currentAmount > 1) {
                itemInHand.setAmount(currentAmount - 1);
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        }
    }

    private int getConfiguredDeductionAmount(Map<String, Object> itemConfig) {
        Object amountObj = itemConfig.get("amount");
        if (amountObj instanceof Number) {
            return ((Number) amountObj).intValue();
        }
        return 0;
    }

    private void sendCompletionMessage(Player player, String itemId, ItemStack item, Map<String, Object> itemConfig) {
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
