package com.horizonkk.itemAction.api;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 法术读条API - 提供给法术插件使用
 * 与ChargeAPI不同：移动不取消、切换物品不取消、新法术读条会自动取消旧读条
 */
public interface SpellChargeAPI {

    /**
     * 开始读条，如果已有读条则自动取消旧的
     * @param player     玩家
     * @param spellId    法术ID
     * @param targetTime 读条时间（秒）
     * @param onComplete 读条完成回调（在主线程执行）
     */
    void startCharging(Player player, String spellId, int targetTime, Runnable onComplete);

    /**
     * 取消读条
     */
    void cancelCharging(UUID playerId);

    /**
     * 获取读条进度百分比
     * @return 0-100，未读条返回-1
     */
    int getChargeProgressPercent(UUID playerId);

    /**
     * 是否正在读条
     */
    boolean isPlayerCharging(UUID playerId);

    /**
     * 获取当前读条的法术ID
     * @return 法术ID，未读条返回null
     */
    String getCurrentSpellId(UUID playerId);

    /**
     * 清理所有读条数据（插件卸载时调用）
     */
    void cleanup();
}
