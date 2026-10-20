package com.horizonkk.itemAction.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class LingQiAbsorbEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String itemId;
    private final int consumedAmount;

    public LingQiAbsorbEvent(Player player, String itemId, int consumedAmount) {
        this.player = player;
        this.itemId = itemId;
        this.consumedAmount = consumedAmount;
    }

    public Player getPlayer() {
        return player;
    }

    public String getItemId() {
        return itemId;
    }

    public int getConsumedAmount() {
        return consumedAmount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
