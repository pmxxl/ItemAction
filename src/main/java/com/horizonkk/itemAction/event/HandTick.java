package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * 手持物品切换监听
 * 注意：PlaceholderAPI 采用按需查询（on-demand），无需轮询扫描
 */
public class HandTick implements Listener {
    private final ItemAction plugin;

    public HandTick(ItemAction plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        // 当玩家切换物品栏时，不做额外处理
        // 物品动作、读条等均由各自的EventHandler按需触发
        // PlaceholderAPI 变量在占位符被解析时实时查询
    }
}
