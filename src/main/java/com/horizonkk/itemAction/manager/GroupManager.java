package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.util.NbtUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

/**
 * 物品组管理器
 * 负责加载和管理group.yml配置文件
 * 通过NBT标签匹配物品组，并提供动作模板
 */
public class GroupManager {

    private final ItemAction plugin;
    private YamlConfiguration groupConfig;
    private final Map<String, GroupEntry> groups = new LinkedHashMap<>();

    // 【缓存】itemId → 匹配的GroupEntry（null值表示"确认无匹配"）
    private final Map<String, GroupEntry> matchCache = new HashMap<>();
    // 【缓存】"groupName:itemId" → 解析后的模板配置
    private final Map<String, Map<String, Object>> templateCache = new HashMap<>();

    public GroupManager(ItemAction plugin) {
        this.plugin = plugin;
        loadGroupConfig();
    }

    /**
     * 加载group.yml配置文件
     */
    public void loadGroupConfig() {
        File configFile = new File(plugin.getDataFolder(), "group.yml");
        if (!configFile.exists()) {
            plugin.saveResource("group.yml", false);
        }
        groupConfig = YamlConfiguration.loadConfiguration(configFile);
        buildGroupCache();
    }

    /**
     * 构建组缓存
     */
    private void buildGroupCache() {
        groups.clear();
        // 清除查询缓存（group定义变了，旧的匹配关系失效）
        matchCache.clear();
        templateCache.clear();
        for (String key : groupConfig.getKeys(false)) {
            if (groupConfig.isConfigurationSection(key)) {
                ConfigurationSection section = groupConfig.getConfigurationSection(key);
                if (section == null) continue;

                GroupEntry entry = new GroupEntry();
                entry.groupName = key;

                // 解析匹配条件
                ConfigurationSection matchSection = section.getConfigurationSection("match");
                if (matchSection != null) {
                    entry.matchTag = matchSection.getString("tag");
                    entry.matchValue = matchSection.getString("value");
                }

                // 解析模板
                ConfigurationSection templateSection = section.getConfigurationSection("template");
                if (templateSection != null) {
                    entry.template = templateSection.getValues(false);
                }

                // 校验必要字段
                if (entry.matchTag != null && entry.matchValue != null && entry.template != null) {
                    groups.put(key, entry);
                } else {
                    plugin.getLogger().warning("物品组 '" + key + "' 配置不完整，已跳过");
                }
            }
        }
        plugin.getLogger().info("已加载 " + groups.size() + " 个物品组配置");
    }

    /**
     * 根据物品NBT匹配对应的组
     *
     * @param item 物品堆
     * @return 匹配的组条目，如果没有匹配则返回null
     */
    public GroupEntry getMatchingGroup(ItemStack item) {
        if (item == null) return null;

        for (GroupEntry entry : groups.values()) {
            if (entry.matchTag != null && entry.matchValue != null) {
                String itemNbtValue = NbtUtils.getNbtString(item, entry.matchTag);
                if (entry.matchValue.equals(itemNbtValue)) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * 【高效】根据物品NBT匹配对应的组，优先查缓存
     * 同一个itemId的匹配结果在配置重载前是不变的
     *
     * @param item   物品堆
     * @param itemId 已计算的物品ID（作为缓存键）
     * @return 匹配的组条目，如果没有匹配则返回null
     */
    public GroupEntry getMatchingGroup(ItemStack item, String itemId) {
        // 缓存命中
        if (matchCache.containsKey(itemId)) {
            return matchCache.get(itemId);
        }
        // 执行NBT扫描
        GroupEntry result = getMatchingGroup(item);
        // 缓存结果（包括null，表示"此itemId不匹配任何group"）
        matchCache.put(itemId, result);
        return result;
    }

    /**
     * 检查物品是否匹配任何组
     *
     * @param item 物品堆
     * @return 是否匹配
     */
    public boolean matchesAnyGroup(ItemStack item) {
        return getMatchingGroup(item) != null;
    }

    /**
     * 【高效】解析模板中的占位符，生成最终配置，优先查缓存
     * 同一个 (groupName, itemId) 组合的解析结果始终不变
     *
     * @param group  匹配的组条目
     * @param itemId 物品的NI ID
     * @return 解析后的配置Map
     */
    public Map<String, Object> resolveTemplate(GroupEntry group, String itemId) {
        String cacheKey = group.groupName + ":" + itemId;
        Map<String, Object> cached = templateCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Map<String, Object> resolved = deepCopyMap(group.template);
        resolvePlaceholders(resolved, itemId);
        if (!resolved.containsKey("permission")) {
            resolved.put("permission", itemId);
        }

        templateCache.put(cacheKey, resolved);
        return resolved;
    }

    /**
     * 递归解析占位符
     */
    private void resolvePlaceholders(Map<String, Object> map, String itemId) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) {
                entry.setValue(((String) value).replace("%itemid%", itemId));
            } else if (value instanceof List) {
                List<Object> resolvedList = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    if (item instanceof String) {
                        resolvedList.add(((String) item).replace("%itemid%", itemId));
                    } else {
                        resolvedList.add(item);
                    }
                }
                entry.setValue(resolvedList);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                resolvePlaceholders(subMap, itemId);
            }
        }
    }

    /**
     * 深度复制Map（避免修改原始模板）
     */
    private Map<String, Object> deepCopyMap(Map<String, Object> original) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : original.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                copy.put(entry.getKey(), deepCopyMap(subMap));
            } else if (value instanceof List) {
                copy.put(entry.getKey(), new ArrayList<>((List<?>) value));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return copy;
    }

    /**
     * 重新加载group.yml配置
     */
    public void reloadConfig() {
        loadGroupConfig();
        plugin.getLogger().info("物品组配置已重新加载");
    }

    /**
     * 获取组条目的数量
     */
    public int getGroupCount() {
        return groups.size();
    }

    /**
     * 物品组条目
     */
    public static class GroupEntry {
        /** 组名（配置中的key） */
        public String groupName;
        /** NBT匹配标签名 */
        public String matchTag;
        /** NBT匹配标签值 */
        public String matchValue;
        /** 动作模板 */
        public Map<String, Object> template;
    }
}
