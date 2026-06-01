package com.horizonkk.itemAction.hook;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.api.ChargeAPI;
import com.horizonkk.itemAction.api.SpellChargeAPI;
import com.horizonkk.itemAction.event.RightKeyAction;
import com.horizonkk.itemAction.manager.ActionManager;
import com.horizonkk.itemAction.event.FkeyAction;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaceholderAPI extends PlaceholderExpansion {
    private final ItemAction plugin;
    private final ActionManager actionManager;
    private final FkeyAction fkeyAction;
    private final RightKeyAction rightKeyAction;
    private final ChargeAPI chargeManager; // 新增共享读条管理器
    private final SpellChargeAPI spellChargeManager; // 法术读条管理器

    public PlaceholderAPI(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
        this.fkeyAction = plugin.getFkeyAction();
        this.rightKeyAction = plugin.getRightKeyAction();
        this.chargeManager = plugin.getChargeManager(); // 获取共享读条管理器
        this.spellChargeManager = plugin.getSpellChargeManager(); // 获取法术读条管理器
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
        UUID playerId = player.getUniqueId();

        // 优先处理读条相关变量（不依赖手持物品，且合并两边管理器）
        String chargeResult = processChargePlaceholders(playerId, params);
        if (chargeResult != null) {
            return chargeResult;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand == null) {
            return handleNoItemPlaceholders(params);
        }
        // 【优化】itemId计算一次，复用给getEffectiveConfig
        String itemId = actionManager.getItemIdFromItem(itemInHand);
        Map<String, Object> effectiveConfig = actionManager.getEffectiveConfig(itemInHand, itemId);
        List<String> actions = actionManager.getItemActions(itemId);
        // 如果Action.yml中没有，尝试从group模板中获取actions
        if (actions == null && effectiveConfig != null) {
            Object actionObj = effectiveConfig.get("action");
            if (actionObj instanceof List) {
                actions = new ArrayList<>();
                for (Object a : (List<?>) actionObj) {
                    if (a instanceof String) {
                        actions.add(((String) a).toUpperCase());
                    }
                }
            }
        }
        if (actions == null) {
            return handleNoItemPlaceholders(params);
        }

        switch (params.toLowerCase()) {
            // 基础物品信息
            case "hand_item_id":
                return itemId;

            case "hand_item_exists":
                return "true";

            case "hand_item_display_name":
                return actionManager.getItemDisplayName(itemInHand);

            // 新增：返回配置中的text文字信息
            case "text":
                if (effectiveConfig != null) {
                    // 检查是否有权限配置
                    if (effectiveConfig.containsKey("permission") && effectiveConfig.containsKey("permission-text")) {
                        String permission = (String) effectiveConfig.get("permission");
                        String permissionText = (String) effectiveConfig.get("permission-text");

                        // 如果玩家拥有该权限，返回permission-text
                        if (player.hasPermission(permission)) {
                            return permissionText;
                        }
                    }
                    // 如果没有权限配置或玩家没有权限，返回普通的text
                    if (effectiveConfig.containsKey("text")) {
                        Object textObj = effectiveConfig.get("text");
                        if (textObj instanceof String) {
                            return (String) textObj;
                        }
                    }
                }
                return ""; // 如果没有配置text或不是字符串，则返回空字符串

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

            // 特定物品检测（含Action.yml和group.yml）
            case "is_configured_item":
                return String.valueOf(effectiveConfig != null);

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

    /**
     * 处理读条相关占位符，合并 ChargeAPI 和 SpellChargeAPI 两边状态
     * @return 占位符值，不匹配时返回null
     */
    private String processChargePlaceholders(UUID playerId, String params) {
        boolean isCharging = chargeManager.isPlayerCharging(playerId);
        boolean isSpellCharging = spellChargeManager.isPlayerCharging(playerId);
        boolean anyCharging = isCharging || isSpellCharging;

        // 获取进度：法术优先
        int progress = -1;
        if (isSpellCharging) {
            progress = spellChargeManager.getChargeProgressPercent(playerId);
        } else if (isCharging) {
            progress = chargeManager.getChargeProgressPercent(playerId);
        }

        switch (params.toLowerCase()) {
            case "is_charging":
                return String.valueOf(anyCharging);

            case "charging_progress":
                return progress >= 0 ? String.valueOf(progress) : "";

            case "charging_progress_percent":
                return progress >= 0 ? progress + "%" : "";

            case "charging_complete":
                return String.valueOf(progress >= 100);

            case "is_charging_complete":
                return String.valueOf(progress >= 100 && anyCharging);

            case "charging_type":
                if (isSpellCharging) {
                    String spellId = spellChargeManager.getCurrentSpellId(playerId);
                    return spellId != null ? spellId : "";
                }
                if (isCharging) {
                    String chargeType = chargeManager.getChargeType(playerId);
                    return chargeType != null ? chargeType : "";
                }
                return "";

            case "default_max_progress":
            case "max_charge_value":
            case "full_progress":
                return "100";

            // 法术读条独立变量（保留用于精确查询）
            case "spell_charging_progress": {
                int sp = spellChargeManager.getChargeProgressPercent(playerId);
                return sp >= 0 ? String.valueOf(sp) : "";
            }
            case "spell_charging_progress_percent": {
                int sp = spellChargeManager.getChargeProgressPercent(playerId);
                return sp >= 0 ? sp + "%" : "";
            }
            case "spell_is_charging":
                return String.valueOf(isSpellCharging);

            case "spell_charging_complete": {
                int sp = spellChargeManager.getChargeProgressPercent(playerId);
                return String.valueOf(sp >= 100);
            }
            case "spell_current_id": {
                String spellId = spellChargeManager.getCurrentSpellId(playerId);
                return spellId != null ? spellId : "";
            }

            default:
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

            // 新增：没有物品时text变量的处理
            case "text":
                return "";

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

    /**
     * 获取物品配置中的text文本
     * @param itemId 物品ID
     * @return 配置的text文本，如果不存在则返回空字符串
     */
    private String getItemText(String itemId) {
        Map<String, Object> itemConfig = actionManager.getItemConfig(itemId);
        if (itemConfig != null && itemConfig.containsKey("text")) {
            Object textObj = itemConfig.get("text");
            if (textObj instanceof String) {
                return (String) textObj;
            }
        }
        return "";
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
