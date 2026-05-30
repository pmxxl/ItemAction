package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.api.SpellChargeAPI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpellChargeManager implements SpellChargeAPI {

    private final ItemAction plugin;

    private static final int UPDATE_INTERVAL = 4;
    private static final double PROGRESS_INCREMENT = 0.2;

    private final Map<UUID, BukkitTask> chargeTasks = new HashMap<>();
    private final Map<UUID, Double> chargeProgress = new HashMap<>();
    private final Map<UUID, Integer> chargeTargets = new HashMap<>();
    private final Map<UUID, String> chargeSpellIds = new HashMap<>();
    private final Map<UUID, Runnable> chargeCallbacks = new HashMap<>();

    public SpellChargeManager(ItemAction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void startCharging(Player player, String spellId, int targetTime, Runnable onComplete) {
        UUID playerId = player.getUniqueId();

        // 如果已有读条，自动取消旧的
        if (chargeTasks.containsKey(playerId)) {
            cancelCharging(playerId);
        }

        chargeTargets.put(playerId, targetTime);
        chargeProgress.put(playerId, 0.0);
        chargeSpellIds.put(playerId, spellId);
        chargeCallbacks.put(playerId, onComplete);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelCharging(playerId);
                    return;
                }

                double currentProgress = chargeProgress.getOrDefault(playerId, 0.0);
                int target = chargeTargets.getOrDefault(playerId, targetTime);

                currentProgress += PROGRESS_INCREMENT;
                chargeProgress.put(playerId, currentProgress);

                if (currentProgress >= target) {
                    completeCharging(playerId);
                }
            }
        }.runTaskTimer(plugin, 0L, UPDATE_INTERVAL);

        chargeTasks.put(playerId, task);
    }

    @Override
    public void cancelCharging(UUID playerId) {
        BukkitTask task = chargeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        chargeProgress.remove(playerId);
        chargeTargets.remove(playerId);
        chargeSpellIds.remove(playerId);
        chargeCallbacks.remove(playerId);
    }

    @Override
    public int getChargeProgressPercent(UUID playerId) {
        if (!chargeTasks.containsKey(playerId)) {
            return -1;
        }

        double currentProgress = chargeProgress.getOrDefault(playerId, 0.0);
        int target = chargeTargets.getOrDefault(playerId, 1);

        if (target <= 0) return 0;

        return Math.min(100, (int) ((currentProgress / target) * 100));
    }

    @Override
    public boolean isPlayerCharging(UUID playerId) {
        return chargeTasks.containsKey(playerId);
    }

    @Override
    public String getCurrentSpellId(UUID playerId) {
        return chargeSpellIds.get(playerId);
    }

    @Override
    public void cleanup() {
        for (UUID playerId : chargeTasks.keySet()) {
            cancelCharging(playerId);
        }
    }

    private void completeCharging(UUID playerId) {
        Runnable callback = chargeCallbacks.remove(playerId);
        cancelCharging(playerId);

        if (callback != null) {
            callback.run();
        }
    }
}
