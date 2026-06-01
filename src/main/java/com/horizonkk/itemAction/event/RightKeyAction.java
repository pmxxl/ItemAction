package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.api.ChargeAPI;
import com.horizonkk.itemAction.manager.ActionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RightKeyAction implements Listener {
    private final ItemAction plugin;
    private final ActionManager actionManager;
    private final ChargeAPI chargeManager; // 新增共享读条管理器

    public RightKeyAction(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
        this.chargeManager = plugin.getChargeManager(); // 获取共享读条管理器
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // 只处理右键点击空气或方块的事件
        if (!event.getAction().name().contains("RIGHT_CLICK")) {
            return;
        }
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        // 检测的位置应该是在主手快捷栏（获取玩家当前手持的物品）
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        // 获取物品ID
        String itemId = actionManager.getItemIdFromItem(mainHandItem);
        if (!actionManager.canPerformAction(mainHandItem, "right")) {
            // 如果不是配置了Right动作的物品（含Action.yml和group.yml），则不触发逻辑，直接返回
            return;
        }
        event.setCancelled(true);
        // 检测主手快捷栏物品
        if (mainHandItem != null && mainHandItem.getType() != Material.AIR) {
            // 获取物品ID
            // 检查物品是否有配置（Action.yml或group.yml）
            boolean hasConfig = actionManager.hasConfigForItem(mainHandItem);
            if (hasConfig) {
                // 检查是否配置了右键动作
                boolean canPerformRight = actionManager.canPerformAction(mainHandItem, "RIGHT");
                if (canPerformRight) {
                    // 检查权限限制（支持group的动态权限）
                    if (hasPermissionRestriction(mainHandItem, player)) {
                        // 玩家拥有权限，无法使用该物品
                        // player.sendMessage(ChatColor.RED + "您已拥有该物品的权限，无法再次使用");
                        return;
                    }
                    // 获取读条时间配置
                    int chargeTime = getChargeTime(mainHandItem);
                    // 检查是否正在读条
                    if (chargeManager.isPlayerCharging(playerId)) {
                        // 玩家正在读条中，提示正在读条
                        // player.sendMessage(ChatColor.YELLOW + "正在读条中...");
                        return; // 直接返回，不执行任何操作
                    } else {
                        if (chargeTime > 0) {
                            // 开始读条（使用共享管理器）
                            chargeManager.startCharging(player, itemId, chargeTime, "RIGHT");
                        } else {
                            // 立即执行动作
                            executeRightAction(player, itemId, mainHandItem);
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查物品是否有权限限制
     * @param item 物品堆
     * @param player 玩家
     * @return 如果玩家拥有权限则返回true，否则返回false
     */
    private boolean hasPermissionRestriction(ItemStack item, Player player) {
        Map<String, Object> itemConfig = actionManager.getEffectiveConfig(item);
        if (itemConfig != null && itemConfig.containsKey("permission")) {
            String permission = (String) itemConfig.get("permission");
            return player.hasPermission(permission);
        }
        return false;
    }

    /**
     * 执行右键动作
     */
    public void executeRightAction(Player player, String itemId, ItemStack itemInHand) {
        // 获取物品的有效配置（优先Action.yml，其次group.yml）
        Map<String, Object> itemConfig = actionManager.getEffectiveConfig(itemInHand);
        if (itemConfig != null) {
            // 执行配置中的命令（支持group的%itemid%占位符）
            executeConfiguredCommands(player, itemConfig, itemId);
            // 处理物品扣除
            handleItemDeduction(player, itemInHand, itemConfig);
            // 发送完成消息
            sendCompletionMessage(player, itemId, itemInHand);
        }

        // 这里可以添加其他的右键动作逻辑
    }

    /**
     * 执行配置文件中的命令
     */
    private void executeConfiguredCommands(Player player, Map<String, Object> itemConfig) {
        executeConfiguredCommands(player, itemConfig, null);
    }

    /**
     * 执行配置文件中的命令（支持%itemid%占位符）
     */
    private void executeConfiguredCommands(Player player, Map<String, Object> itemConfig, String itemId) {
        // 检查是否有command配置
        if (itemConfig.containsKey("command")) {
            Object commandObj = itemConfig.get("command");

            if (commandObj instanceof String) {
                String command = (String) commandObj;
                // 替换group的%itemid%占位符
                if (itemId != null) {
                    command = command.replace("%itemid%", itemId);
                }
                executeSingleCommand(player, command);
            } else if (commandObj instanceof Iterable) {
                // 支持多个命令
                for (Object cmdObj : (Iterable<?>) commandObj) {
                    if (cmdObj instanceof String) {
                        String command = (String) cmdObj;
                        // 替换group的%itemid%占位符
                        if (itemId != null) {
                            command = command.replace("%itemid%", itemId);
                        }
                        executeSingleCommand(player, command);
                    }
                }
            }
        }
    }

    /**
     * 执行单个命令
     */
    private void executeSingleCommand(Player player, String command) {
        // 替换占位符
        String processedCommand = command
                .replace("%player%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString());

        // 检查执行方式
        if (processedCommand.endsWith(" as op")) {
            // OP身份执行
            String actualCommand = processedCommand.substring(0, processedCommand.length() - 6);
            executeAsOp(player, actualCommand);
        } else if (processedCommand.endsWith(" as console")) {
            // 控制台执行
            String actualCommand = processedCommand.substring(0, processedCommand.length() - 11);
            executeAsConsole(actualCommand);
        } else {
            // 普通玩家执行
            player.performCommand(processedCommand);
        }
    }

    /**
     * 以OP身份执行命令
     */
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

    /**
     * 以控制台身份执行命令
     */
    private void executeAsConsole(String command) {
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
    }

    /**
     * 处理物品扣除
     */
    private void handleItemDeduction(Player player, ItemStack itemInHand, Map<String, Object> itemConfig) {
        // 检查是否有amount配置
        if (itemConfig.containsKey("amount")) {
            Object amountObj = itemConfig.get("amount");

            int amountToDeduct = 0;
            if (amountObj instanceof Number) {
                amountToDeduct = ((Number) amountObj).intValue();
            }

            // 如果amount为1，则扣除当前物品
            if (amountToDeduct == 1 && itemInHand != null && itemInHand.getAmount() > 0) {
                int currentAmount = itemInHand.getAmount();
                if (currentAmount > 1) {
                    // 如果数量大于1，减少1个
                    itemInHand.setAmount(currentAmount - 1);
                } else {
                    // 如果只有1个，设置为空气（删除物品）
                    player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
                }
            }
            // 如果amount为其他数值，可以在这里扩展处理
        }
    }

    /**
     * 发送使用完成后的消息
     */
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

    /**
     * 获取物品的读条时间配置（支持Action.yml和group.yml）
     */
    private int getChargeTime(ItemStack item) {
        Map<String, Object> config = actionManager.getEffectiveConfig(item);

        if (config != null && config.containsKey("time")) {
            Object timeObj = config.get("time");

            if (timeObj instanceof Number) {
                int timeValue = ((Number) timeObj).intValue();
                return timeValue;
            }
        }
        return 0; // 默认为0秒
    }

    // 保留 getChargeProgressPercent 和 isPlayerCharging 方法，但委托给共享管理器
    public int getChargeProgressPercent(UUID playerId) {
        return chargeManager.getChargeProgressPercent(playerId);
    }

    public boolean isPlayerCharging(UUID playerId) {
        return chargeManager.isPlayerCharging(playerId);
    }

}
