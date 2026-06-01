package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.hook.NeigeItemsAPI;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class ActionManager {
    private final ItemAction plugin;
    private YamlConfiguration actionConfig;
    private Map<String, Map<String, Object>> itemCache;
    private GroupManager groupManager;

    public ActionManager(ItemAction plugin) {
        this.plugin = plugin;
        this.itemCache = new HashMap<>();
        loadActionConfig();
    }

    /**
     * 设置GroupManager引用（由ItemAction在初始化后注入）
     */
    public void setGroupManager(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * 获取GroupManager
     */
    public GroupManager getGroupManager() {
        return groupManager;
    }

    private void loadActionConfig() {
        File configFile = new File(plugin.getDataFolder(), "Action.yml");
        if (!configFile.exists()) {
            plugin.saveResource("Action.yml", false);
        }
        actionConfig = YamlConfiguration.loadConfiguration(configFile);
        buildItemCache();
    }

    private void buildItemCache() {
        itemCache.clear();
        for (String key : actionConfig.getKeys(false)) {
            if (actionConfig.isConfigurationSection(key)) {
                itemCache.put(key, actionConfig.getConfigurationSection(key).getValues(false));
            }
        }
    }

    public Map<String, Object> getItemConfig(String itemId) {
        return itemCache.get(itemId);
    }

    public boolean hasItemConfig(String itemId) {
        return itemCache.containsKey(itemId);
    }

    public List<String> getItemActions(String itemId) {
        Map<String, Object> config = getItemConfig(itemId);
        if (config != null) {
            List<String> rawActions = (List<String>) config.get("action");
            if (rawActions != null) {
                // 统一转换为大写，确保匹配一致性
                List<String> normalizedActions = new ArrayList<>();
                for (String action : rawActions) {
                    normalizedActions.add(action.toUpperCase());
                }
                return normalizedActions;
            }
        }
        return null;
    }

    public boolean canPerformAction(String itemId, String action) {
        List<String> actions = getItemActions(itemId);
        return actions != null && actions.contains(action.toUpperCase());
    }

    public String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name();
    }

    /**
     * 从物品中获取物品ID（优先使用NeigeItems API）
     * @param item 物品堆
     * @return 物品ID
     */
    public String getItemIdFromItem(ItemStack item) {
        // 首先尝试通过NeigeItems API获取ID
        String niItemId = NeigeItemsAPI.getSafeItemId(item);
        if (niItemId != null && !niItemId.isEmpty()) {
//            plugin.getLogger().info("通过NeigeItems API获取到物品ID: " + niItemId);
            return niItemId;
        }
        // 如果不是NI物品，则使用显示名称作为ID
        String displayName = getItemDisplayName(item);
//        plugin.getLogger().info("使用显示名称作为物品ID: " + displayName);
        return displayName;
    }

    /**
     * 检查物品是否为配置的物品
     * @param item 物品堆
     * @return 是否为配置物品
     */
    public boolean isConfiguredItem(ItemStack item) {
        String itemId = getItemIdFromItem(item);
        boolean isConfigured = hasItemConfig(itemId);
//        plugin.getLogger().info("检查物品 '" + itemId + "' 是否为配置物品: " + isConfigured);
        return isConfigured;
    }

    /**
     * 获取物品使用完成后的消息，没有配置则返回null
     */
    public String getActionMessage(String itemId) {
        Map<String, Object> config = getItemConfig(itemId);
        if (config != null && config.containsKey("message")) {
            return (String) config.get("message");
        }
        return null;
    }

    /**
     * 获取物品的有效配置（优先Action.yml，其次group.yml）
     * 如果是通过group匹配的，会自动将%itemid%替换为实际的物品NI ID
     *
     * @param item 物品堆
     * @return 有效配置Map，如果都没有匹配则返回null
     */
    public Map<String, Object> getEffectiveConfig(ItemStack item) {
        if (item == null) return null;

        String itemId = getItemIdFromItem(item);

        // 优先检查Action.yml中的直接配置
        Map<String, Object> directConfig = getItemConfig(itemId);
        if (directConfig != null) {
            return directConfig;
        }

        // 其次检查group.yml中的组匹配
        if (groupManager != null) {
            GroupManager.GroupEntry matchedGroup = groupManager.getMatchingGroup(item);
            if (matchedGroup != null) {
                return groupManager.resolveTemplate(matchedGroup, itemId);
            }
        }

        return null;
    }

    /**
     * 检查物品是否可以执行指定动作（同时检查Action.yml和group.yml）
     *
     * @param item   物品堆
     * @param action 动作类型 (F, LEFT, RIGHT, SHIFT)
     * @return 是否可以执行
     */
    public boolean canPerformAction(ItemStack item, String action) {
        // 首先检查直接配置
        String itemId = getItemIdFromItem(item);
        if (canPerformAction(itemId, action)) {
            return true;
        }

        // 然后检查组匹配
        if (groupManager != null) {
            GroupManager.GroupEntry matchedGroup = groupManager.getMatchingGroup(item);
            if (matchedGroup != null) {
                Map<String, Object> template = groupManager.resolveTemplate(matchedGroup, itemId);
                Object actionObj = template.get("action");
                if (actionObj instanceof List) {
                    for (Object a : (List<?>) actionObj) {
                        if (a instanceof String && ((String) a).equalsIgnoreCase(action)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * 检查物品是否有任何有效配置（Action.yml或group.yml）
     *
     * @param item 物品堆
     * @return 是否有配置
     */
    public boolean hasConfigForItem(ItemStack item) {
        return getEffectiveConfig(item) != null;
    }

    // 重新加载配置文件
    public void reloadConfig() {
        loadActionConfig();
        if (groupManager != null) {
            groupManager.reloadConfig();
        }
        plugin.getLogger().info("物品动作配置已重新加载");
    }
}
