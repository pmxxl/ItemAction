package com.horizonkk.itemAction.api;

import org.bukkit.entity.Player;

import java.util.UUID;

public interface ChargeAPI {

    void startCharging(Player player, String itemId, int targetTime, String chargeType);

    void cancelCharging(UUID playerId);

    int getChargeProgressPercent(UUID playerId);

    boolean isPlayerCharging(UUID playerId);

    String getChargeType(UUID playerId);

    boolean isMoveAllowedForPlayer(UUID playerId);

    void cleanup();
}
