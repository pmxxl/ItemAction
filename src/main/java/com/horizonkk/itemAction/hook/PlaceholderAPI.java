package com.horizonkk.itemAction.hook;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.manager.ActionManager;
import com.horizonkk.itemAction.event.FkeyAction;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class PlaceholderAPI extends PlaceholderExpansion {

    private final ItemAction plugin;
    private final ActionManager actionManager;
    private final FkeyAction fkeyAction;

    public PlaceholderAPI(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
        this.fkeyAction = plugin.getFkeyAction();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "itemaction";
    }

    @Override
    public @NotNull String getAuthor() {
        return "HorizonKK";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) {
            return "";
        }

        Player onlinePlayer = player.getPlayer();
        if (onlinePlayer == null) {
            return "";
        }

        return processPlaceholder(onlinePlayer, params);
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        return processPlaceholder(player, params);
    }

    private String processPlaceholder(Player player, String params) {
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null) {
            return handleNoItemPlaceholders(params);
        }
        String itemId = actionManager.getItemIdFromItem(itemInHand);
        List<String> actions = actionManager.getItemActions(itemId);
        if (actions == null) {
            return handleNoItemPlaceholders(params);
        }

        // 处理读条相关变量
        UUID playerId = player.getUniqueId();
        switch (params.toLowerCase()) {
            // 基础物品信息
            case "hand_item_id":
                return itemId;

            case "hand_item_exists":
                return "true";

            case "hand_item_display_name":
                return actionManager.getItemDisplayName(itemInHand);

            // 读条进度变量
            case "charging_progress":
                int progress = fkeyAction.getChargeProgressPercent(playerId);
                return progress >= 0 ? String.valueOf(progress) : "";

            case "charging_progress_percent":
                int percent = fkeyAction.getChargeProgressPercent(playerId);
                return percent >= 0 ? percent + "%" : "";

            case "is_charging":
                return String.valueOf(fkeyAction.isPlayerCharging(playerId));

            // 读条满进度变量
            case "charging_complete":
                int completeProgress = fkeyAction.getChargeProgressPercent(playerId);
                return String.valueOf(completeProgress >= 100);

            case "is_charging_complete":
                int completionProgress = fkeyAction.getChargeProgressPercent(playerId);
                return String.valueOf(completionProgress >= 100 && fkeyAction.isPlayerCharging(playerId));

            // 新增：默认返回100的变量
            case "default_max_progress":
                return "100";

            case "max_charge_value":
                return "100";

            case "full_progress":
                return "100";

            // 动作检测变量 (返回 true/false)
            case "can_use_f":
                return String.valueOf(actions.contains("F"));

            case "can_use_left":
                return String.valueOf(actions.contains("LEFT"));

            case "can_use_right":
                return String.valueOf(actions.contains("RIGHT"));

            case "can_use_shift":
                return String.valueOf(actions.contains("SHIFT"));

            // 组合动作检测
            case "has_any_action":
                return String.valueOf(!actions.isEmpty());

            case "has_multiple_actions":
                return String.valueOf(actions.size() > 1);

            case "action_count":
                return String.valueOf(actions.size());

            // 特定物品检测
            case "is_configured_item":
                return String.valueOf(actionManager.hasItemConfig(itemId));

            default:
                // 处理带参数的占位符
                if (params.startsWith("has_action_")) {
                    String action = params.substring(11).toUpperCase();
                    return String.valueOf(actions.contains(action));
                }

                if (params.startsWith("action_list_")) {
                    String format = params.substring(12);
                    return formatActionList(actions, format);
                }

                return null;
        }
    }

    private String handleNoItemPlaceholders(String params) {
        switch (params.toLowerCase()) {
            case "hand_item_id":
                return "";

            case "hand_item_exists":
                return "false";

            case "hand_item_display_name":
                return "";

            case "charging_progress":
            case "charging_progress_percent":
                return "";

            case "is_charging":
            case "charging_complete":
            case "is_charging_complete":
                return "false";

            // 默认返回100的变量（即使没有物品也返回100）
            case "default_max_progress":
            case "max_charge_value":
            case "full_progress":
                return "100";

            case "can_use_f":
            case "can_use_left":
            case "can_use_right":
            case "can_use_shift":
            case "has_any_action":
            case "is_configured_item":
                return "false";

            case "action_count":
                return "0";

            case "has_multiple_actions":
                return "false";

            default:
                if (params.startsWith("has_action_")) {
                    return "false";
                }
                if (params.startsWith("action_list_")) {
                    return "";
                }
                return null;
        }
    }

    private String formatActionList(List<String> actions, String format) {
        if (actions == null || actions.isEmpty()) {
            return "";
        }

        switch (format.toLowerCase()) {
            case "comma":
                return String.join(",", actions);
            case "space":
                return String.join(" ", actions);
            case "bracket":
                return "[" + String.join("][", actions) + "]";
            default:
                return String.join(",", actions);
        }
    }
}
