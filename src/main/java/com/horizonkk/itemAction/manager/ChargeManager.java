package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.api.ChargeAPI;
import com.horizonkk.itemAction.event.FkeyAction;
import com.horizonkk.itemAction.event.RightKeyAction;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChargeManager implements ChargeAPI {
    private final ItemAction plugin;
    private final ActionManager actionManager;

    // 配置：每4tick更新一次（0.2秒）
    private static final int UPDATE_INTERVAL = 4;
    private static final double PROGRESS_INCREMENT = 0.2; // 每次增加0.2秒

    // 存储玩家的读条任务
    private final Map<UUID, BukkitTask> chargeTasks = new HashMap<>();
    // 存储玩家的读条进度（改为double以支持小数）
    private final Map<UUID, Double> chargeProgress = new HashMap<>();
    // 存储玩家的目标读条时间
    private final Map<UUID, Integer> chargeTargets = new HashMap<>();
    // 存储玩家读条开始时的物品ID
    private final Map<UUID, String> chargeItemIds = new HashMap<>();
    // 存储读条类型（F键或右键）
    private final Map<UUID, String> chargeTypes = new HashMap<>();
    // 存储读条时是否允许移动
    private final Map<UUID, Boolean> chargeMoveAllowed = new HashMap<>();
    public ChargeManager(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
    }

    /**
     * 开始读条（通用方法）
     */
    public void startCharging(Player player, String itemId, int targetTime, String chargeType) {
        UUID playerId = player.getUniqueId();

        // 如果已经在读条，取消之前的任务
        if (chargeTasks.containsKey(playerId)) {
            cancelCharging(playerId);
        }

        // 获取move配置（支持Action.yml和group.yml）
        boolean moveAllowed = isMoveAllowed(player);

        // 设置目标时间和初始进度
        chargeTargets.put(playerId, targetTime);
        chargeProgress.put(playerId, 0.0); // 初始化为0.0
        chargeItemIds.put(playerId, itemId); // 记录开始时的物品ID
        chargeTypes.put(playerId, chargeType); // 记录读条类型
        chargeMoveAllowed.put(playerId, moveAllowed); // 记录是否允许移动

        // 开始读条任务 - 每4tick更新一次
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 检查物品是否发生变化
                if (!isItemUnchanged(playerId)) {
                    // 物品已变更，取消读条
                    cancelCharging(playerId);
                    return;
                }

                double currentProgress = chargeProgress.getOrDefault(playerId, 0.0);
                int target = chargeTargets.getOrDefault(playerId, targetTime);

                // 每次增加0.2秒的进度
                currentProgress += PROGRESS_INCREMENT;
                chargeProgress.put(playerId, currentProgress);

                // 显示进度条
                showChargeProgress(player, currentProgress, target);

                // 检查是否完成读条
                if (currentProgress >= target) {
                    // 读条完成，执行动作
                    completeCharging(player, itemId, chargeType);
                }
            }
        }.runTaskTimer(plugin, 0L, UPDATE_INTERVAL); // 每4tick执行一次

        chargeTasks.put(playerId, task);
    }

    /**
     * 检查读条期间物品是否发生变化
     */
    private boolean isItemUnchanged(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) return false;

        String originalItemId = chargeItemIds.get(playerId);
        if (originalItemId == null) return false;

        ItemStack currentItem = player.getInventory().getItemInMainHand();
        if (currentItem == null || currentItem.getType() == Material.AIR) return false;

        String currentItemId = actionManager.getItemIdFromItem(currentItem);
        return originalItemId.equals(currentItemId);
    }

    /**
     * 显示读条进度
     */
    private void showChargeProgress(Player player, double progress, int target) {
        // 计算百分比进度（1-100）
        int percent = Math.min(100, (int) ((progress / target) * 100));

        StringBuilder progressBar = new StringBuilder(ChatColor.GREEN + "[");
        int filledBars = (int) ((double) percent / 100 * 20); // 20个格子的进度条

        for (int i = 0; i < 20; i++) {
            if (i < filledBars) {
                progressBar.append("|");
            } else {
                progressBar.append("-");
            }
        }
        // progressBar.append(ChatColor.GREEN + "] " + percent + "%");

        // player.sendMessage(progressBar.toString());
    }

    /**
     * 读条完成
     */
    private void completeCharging(Player player, String itemId, String chargeType) {
        UUID playerId = player.getUniqueId();

        cancelCharging(playerId);

        // 根据读条类型执行相应的动作
        if ("F".equals(chargeType)) {
            // 执行F键动作
            FkeyAction fkeyAction = plugin.getFkeyAction();
            if (fkeyAction != null) {
                ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                fkeyAction.executeFAction(player, itemId, mainHandItem);
            }
        } else if ("RIGHT".equals(chargeType)) {
            // 执行右键动作
            RightKeyAction rightKeyAction = plugin.getRightKeyAction();
            if (rightKeyAction != null) {
                ItemStack mainHandItem = player.getInventory().getItemInMainHand();
                rightKeyAction.executeRightAction(player, itemId, mainHandItem);
            }
        }
    }

    /**
     * 取消读条
     */
    public void cancelCharging(UUID playerId) {
        BukkitTask task = chargeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        chargeProgress.remove(playerId);
        chargeTargets.remove(playerId);
        chargeItemIds.remove(playerId);
        chargeTypes.remove(playerId);
        chargeMoveAllowed.remove(playerId);
    }

    /**
     * 获取玩家当前读条进度百分比
     * @param playerId 玩家UUID
     * @return 读条进度百分比(0-100)，如果没有读条则返回-1
     */
    public int getChargeProgressPercent(UUID playerId) {
        if (!chargeTasks.containsKey(playerId)) {
            return -1; // 没有读条
        }

        double currentProgress = chargeProgress.getOrDefault(playerId, 0.0);
        int target = chargeTargets.getOrDefault(playerId, 1);

        if (target <= 0) return 0;

        return Math.min(100, (int) ((currentProgress / target) * 100));
    }

    /**
     * 检查玩家是否正在读条
     * @param playerId 玩家UUID
     * @return 是否正在读条
     */
    public boolean isPlayerCharging(UUID playerId) {
        return chargeTasks.containsKey(playerId);
    }

    /**
     * 获取玩家当前读条类型
     * @param playerId 玩家UUID
     * @return 读条类型("F"或"RIGHT")，如果没有读条则返回null
     */
    public String getChargeType(UUID playerId) {
        return chargeTypes.get(playerId);
    }

    /**
     * 清理所有读条数据（用于插件关闭时）
     */
    public void cleanup() {
        for (UUID playerId : chargeTasks.keySet()) {
            cancelCharging(playerId);
        }
    }

    /**
     * 检查玩家读条时是否允许移动
     * @param playerId 玩家UUID
     * @return 是否允许移动，如果没有读条则返回true
     */
    public boolean isMoveAllowedForPlayer(UUID playerId) {
        Boolean allowed = chargeMoveAllowed.get(playerId);
        return allowed != null ? allowed : true;
    }

    private boolean isMoveAllowed(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        // 【优化】使用已解析config的静态方法，避免GroupManager也重复查询
        String itemId = actionManager.getItemIdFromItem(item);
        Map<String, Object> config = actionManager.getEffectiveConfig(item, itemId);
        return ActionManager.isMoveAllowed(config);
    }

}
