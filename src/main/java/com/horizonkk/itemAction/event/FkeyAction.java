package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.manager.ActionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FkeyAction implements Listener {

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

    public FkeyAction(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
        plugin.getLogger().info("FkeyAction事件监听器已初始化");
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // 按下切换副手键后 取消该事件
        event.setCancelled(true);

        // 检测的位置应该是在主手快捷栏（获取玩家当前手持的物品）
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        plugin.getLogger().info("=== F键事件触发 ===");
        plugin.getLogger().info("玩家: " + player.getName() + " (" + playerId + ")");
        plugin.getLogger().info("主手快捷栏物品: " + (mainHandItem != null ? mainHandItem.getType().name() : "空手"));

        // 检测主手快捷栏物品
        if (mainHandItem != null && mainHandItem.getType() != Material.AIR) {
            plugin.getLogger().info("主手快捷栏有物品，开始检测");

            // 获取物品ID
            String itemId = actionManager.getItemIdFromItem(mainHandItem);
            plugin.getLogger().info("获取到物品ID: " + itemId);

            // 检查物品是否有配置
            boolean hasConfig = actionManager.hasItemConfig(itemId);
            plugin.getLogger().info("物品是否有配置: " + hasConfig);

            if (hasConfig) {
                plugin.getLogger().info("找到物品配置，检查F键动作权限");

                // 检查是否配置了F键动作
                boolean canPerformF = actionManager.canPerformAction(itemId, "F");
                plugin.getLogger().info("是否允许F键操作: " + canPerformF);

                if (canPerformF) {
                    plugin.getLogger().info("允许F键操作，获取读条时间配置");

                    // 获取读条时间配置
                    int chargeTime = getChargeTime(itemId);
                    plugin.getLogger().info("读条时间配置: " + chargeTime + "秒");

                    // 检查是否正在读条
                    if (chargeTasks.containsKey(playerId)) {
                        plugin.getLogger().info("玩家正在读条中，提示正在读条");
                        player.sendMessage(ChatColor.YELLOW + "正在读条中...");
                        return; // 直接返回，不执行任何操作
                    } else {
                        if (chargeTime > 0) {
                            plugin.getLogger().info("开始读条流程");
                            // 开始读条
                            startCharging(player, itemId, chargeTime);
                        } else {
                            plugin.getLogger().info("立即执行F键动作（无读条）");
                            // 立即执行动作
                            executeFAction(player, itemId);
                        }
                    }
                } else {
                    plugin.getLogger().warning("物品不允许F键操作");
                    player.sendMessage(ChatColor.RED + "该物品不支持F键操作");
                }
            } else {
                plugin.getLogger().warning("未找到物品配置: " + itemId);
                player.sendMessage(ChatColor.RED + "当前物品未配置F键动作");
            }
        } else {
            plugin.getLogger().info("主手快捷栏为空");
            player.sendMessage(ChatColor.GRAY + "请手持配置的物品再按F键");
        }

        plugin.getLogger().info("=== F键事件处理结束 ===");
    }

    /**
     * 开始读条
     */
    private void startCharging(Player player, String itemId, int targetTime) {
        UUID playerId = player.getUniqueId();
        plugin.getLogger().info("开始为玩家 " + player.getName() + " 启动读条，目标时间: " + targetTime + "秒");

        // 如果已经在读条，取消之前的任务
        if (chargeTasks.containsKey(playerId)) {
            plugin.getLogger().info("玩家已在读条中，取消之前的读条任务");
            cancelCharging(playerId);
        }

        // 设置目标时间和初始进度
        chargeTargets.put(playerId, targetTime);
        chargeProgress.put(playerId, 0.0); // 初始化为0.0
        chargeItemIds.put(playerId, itemId); // 记录开始时的物品ID
        plugin.getLogger().info("初始化读条数据完成");

        // 开始读条任务 - 每4tick更新一次
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                // 检查物品是否发生变化
                if (!isItemUnchanged(playerId)) {
                    plugin.getLogger().info("检测到物品发生变化，取消读条");
                    player.sendMessage(ChatColor.RED + "物品已变更，读条取消");
                    cancelCharging(playerId);
                    return;
                }

                double currentProgress = chargeProgress.getOrDefault(playerId, 0.0);
                int target = chargeTargets.getOrDefault(playerId, targetTime);

                plugin.getLogger().fine("读条进度更新 - 当前: " + currentProgress + ", 目标: " + target);

                // 每次增加0.2秒的进度
                currentProgress += PROGRESS_INCREMENT;
                chargeProgress.put(playerId, currentProgress);

                // 显示进度条
                showChargeProgress(player, currentProgress, target);

                // 检查是否完成读条
                if (currentProgress >= target) {
                    plugin.getLogger().info("读条完成，执行F键动作");
                    // 读条完成，执行动作
                    completeCharging(player, itemId);
                }
            }
        }.runTaskTimer(plugin, 0L, UPDATE_INTERVAL); // 每4tick执行一次

        chargeTasks.put(playerId, task);
        player.sendMessage(ChatColor.YELLOW + "开始读条...");
        plugin.getLogger().info("读条任务已启动，更新间隔: " + UPDATE_INTERVAL + " ticks (0.2秒)");
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
        progressBar.append(ChatColor.GREEN + "] " + percent + "%");

        player.sendMessage(progressBar.toString());
        plugin.getLogger().fine("向玩家 " + player.getName() + " 发送进度条: " + percent + "%");
    }

    /**
     * 读条完成
     */
    private void completeCharging(Player player, String itemId) {
        UUID playerId = player.getUniqueId();
        plugin.getLogger().info("读条完成，准备执行F键动作");

        cancelCharging(playerId);

        player.sendMessage(ChatColor.GREEN + "读条完成!");
        executeFAction(player, itemId);
    }

    /**
     * 取消读条
     */
    private void cancelCharging(UUID playerId) {
        BukkitTask task = chargeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
            plugin.getLogger().info("已取消玩家的读条任务");
        }
        chargeProgress.remove(playerId);
        chargeTargets.remove(playerId);
        chargeItemIds.remove(playerId); // 清理物品ID记录
        plugin.getLogger().info("已清理玩家的读条数据");
    }

    /**
     * 执行F键动作
     */
    private void executeFAction(Player player, String itemId) {
        plugin.getLogger().info("执行玩家 " + player.getName() + " 的F键动作，物品ID: " + itemId);
        player.sendMessage(ChatColor.GREEN + "执行了 " + itemId + " 的F键动作!");
        // 这里可以添加具体的F键动作逻辑
        plugin.getLogger().info("F键动作执行完成");
    }

    /**
     * 获取物品的读条时间配置
     */
    private int getChargeTime(String itemId) {
        Map<String, Object> config = actionManager.getItemConfig(itemId);
        plugin.getLogger().fine("获取物品 " + itemId + " 的配置: " + config);

        if (config != null && config.containsKey("time")) {
            Object timeObj = config.get("time");
            plugin.getLogger().fine("读条时间配置值: " + timeObj + " (类型: " + timeObj.getClass().getSimpleName() + ")");

            if (timeObj instanceof Number) {
                int timeValue = ((Number) timeObj).intValue();
                plugin.getLogger().fine("转换后的读条时间: " + timeValue);
                return timeValue;
            }
        }
        plugin.getLogger().fine("使用默认读条时间: 0秒");
        return 0; // 默认为0秒
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
     * 监听物品切换事件，用于重置读条
     */
    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        plugin.getLogger().info("玩家 " + player.getName() + " 切换了物品栏");

        // 如果正在读条，取消读条
        if (chargeTasks.containsKey(playerId)) {
            plugin.getLogger().info("检测到玩家正在读条，因切换物品而取消");
            cancelCharging(playerId);
            player.sendMessage(ChatColor.RED + "读条已取消");
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

        plugin.getLogger().info("玩家 " + player.getName() + " 丢弃了物品: " + droppedItem.getType().name());

        // 如果正在读条，检查是否影响当前读条
        if (chargeTasks.containsKey(playerId)) {
            // 获取玩家当前主手物品
            ItemStack mainHandItem = player.getInventory().getItemInMainHand();

            // 如果主手物品与丢弃物品相同，或者主手变为空，则取消读条
            if (mainHandItem == null || mainHandItem.getType() == Material.AIR ||
                    mainHandItem.isSimilar(droppedItem)) {
                plugin.getLogger().info("检测到玩家丢弃了读条相关物品，取消读条");
                cancelCharging(playerId);
                player.sendMessage(ChatColor.RED + "物品已丢弃，读条取消");
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

        // 如果正在读条，取消读条
        if (chargeTasks.containsKey(playerId)) {
            plugin.getLogger().info("玩家 " + player.getName() + " 在读条期间操作了背包，取消读条");
            cancelCharging(playerId);
            player.sendMessage(ChatColor.RED + "读条期间不能操作背包");
        }
    }

    /**
     * 监听玩家退出事件，清理读条数据
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        plugin.getLogger().info("玩家 " + player.getName() + " 退出游戏，清理读条数据");

        if (chargeTasks.containsKey(playerId)) {
            cancelCharging(playerId);
        }
    }
}
