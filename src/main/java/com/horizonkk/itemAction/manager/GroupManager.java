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
     * 检查物品是否匹配任何组
     *
     * @param item 物品堆
     * @return 是否匹配
     */
    public boolean matchesAnyGroup(ItemStack item) {
        return getMatchingGroup(item) != null;
    }

    /**
     * 解析模板中的占位符，生成最终配置
     * 将 %itemid% 替换为实际的物品NI ID
     *
     * @param group  匹配的组条目
     * @param itemId 物品的NI ID
     * @return 解析后的配置Map
     */
    public Map<String, Object> resolveTemplate(GroupEntry group, String itemId) {
        Map<String, Object> resolved = deepCopyMap(group.template);
        resolvePlaceholders(resolved, itemId);
        // 如果模板中没有显式配置permission，则自动使用itemId作为permission
        if (!resolved.containsKey("permission")) {
            resolved.put("permission", itemId);
        }
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
