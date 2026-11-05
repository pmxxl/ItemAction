package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.api.SpellChargeAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** One owned session per player; removing it precedes either terminal callback. */
public class SpellChargeManager implements SpellChargeAPI {
    private final ItemAction plugin;
    private final Map<UUID, Charge> charges = new ConcurrentHashMap<>();
    private boolean cleaningUp;

    private static final class Charge {
        final Player player;
        final String id;
        final int durationMillis;
        final Runnable complete;
        final Runnable cancel;
        volatile long elapsedMillis;
        BukkitTask task;

        Charge(Player player, String id, int durationMillis, Runnable complete, Runnable cancel) {
            this.player = player;
            this.id = id;
            this.durationMillis = durationMillis;
            this.complete = complete;
            this.cancel = cancel;
        }
    }

    public SpellChargeManager(ItemAction plugin) {
        this.plugin = plugin;
    }

    @Override
    public void startCharging(Player player, String spellId, int targetTime, Runnable onComplete) {
        startChargingMillis(player, spellId, Math.toIntExact((long) targetTime * 1000L), onComplete);
    }

    @Override
    public void startChargingMillis(Player player, String spellId, int targetTimeMillis, Runnable onComplete) {
        startChargingMillis(player, spellId, targetTimeMillis, onComplete, null);
    }

    @Override
    public void startChargingMillis(Player player, String spellId, int targetTimeMillis,
                                    Runnable onComplete, Runnable onCancel) {
        requireMainThread();
        if (cleaningUp || !plugin.isEnabled()) throw new IllegalStateException("读条服务正在关闭");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(spellId, "spellId");
        if (!player.isOnline()) throw new IllegalArgumentException("玩家不在线");
        if (targetTimeMillis <= 0) throw new IllegalArgumentException("读条时间必须大于 0");
        UUID playerId = player.getUniqueId();
        cancelCharging(playerId);
        // A cancellation callback may itself start another charge. Never orphan that session.
        if (charges.containsKey(playerId)) throw new IllegalStateException("取消回调已启动新的读条");
        Charge charge = new Charge(player, spellId, targetTimeMillis, onComplete, onCancel);
        charges.put(playerId, charge);
        try {
            // Server tick time: first increment occurs after one tick, never at time zero.
            charge.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(playerId, charge), 1L, 1L);
        } catch (RuntimeException exception) {
            finish(playerId, charge, false);
            throw exception;
        }
    }

    private void tick(UUID playerId, Charge charge) {
        if (charges.get(playerId) != charge) return;
        if (!charge.player.isOnline()) {
            finish(playerId, charge, false);
            return;
        }
        charge.elapsedMillis += 50L;
        if (charge.elapsedMillis >= charge.durationMillis) finish(playerId, charge, true);
    }

    @Override
    public void cancelCharging(UUID playerId) {
        requireMainThread();
        Charge charge = playerId == null ? null : charges.get(playerId);
        if (charge != null) finish(playerId, charge, false);
    }

    private void finish(UUID playerId, Charge charge, boolean completed) {
        if (!charges.remove(playerId, charge)) return;
        if (charge.task != null) charge.task.cancel();
        Runnable callback = completed ? charge.complete : charge.cancel;
        if (callback != null) {
            try {
                callback.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "读条回调执行失败: " + charge.id, exception);
            }
        }
    }

    @Override
    public int getChargeProgressPercent(UUID playerId) {
        Charge charge = playerId == null ? null : charges.get(playerId);
        return charge == null ? -1 : (int) Math.min(100L, charge.elapsedMillis * 100L / charge.durationMillis);
    }

    @Override
    public boolean isPlayerCharging(UUID playerId) {
        return playerId != null && charges.containsKey(playerId);
    }

    @Override
    public String getCurrentSpellId(UUID playerId) {
        Charge charge = playerId == null ? null : charges.get(playerId);
        return charge == null ? null : charge.id;
    }

    @Override
    public void cleanup() {
        requireMainThread();
        boolean alreadyCleaningUp = cleaningUp;
        cleaningUp = true;
        try {
            for (UUID playerId : List.copyOf(charges.keySet())) cancelCharging(playerId);
        } finally {
            cleaningUp = alreadyCleaningUp;
        }
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("读条操作必须在服务器主线程执行");
    }
}
