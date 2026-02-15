package com.horizonkk.itemAction.manager;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.hook.NeigeItemsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class RecipeManager {

    private final ItemAction plugin;
    private final Map<String, RecipeData> recipes = new HashMap<>();
    private final Set<NamespacedKey> registeredRecipes = new HashSet<>();
    private static final Pattern VALID_KEY_PATTERN = Pattern.compile("[a-z0-9/._-]+");
    private boolean initialized = false;

    public RecipeManager(ItemAction plugin) {
        this.plugin = plugin;
        // 延迟加载，等待服务器完全启动
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            loadRecipes();
            registerRecipes();
            initialized = true;
            plugin.getLogger().info("配方管理器初始化完成");
        }, 20L); // 延迟1秒执行
    }

    /**
     * 加载配方配置
     */
    private void loadRecipes() {
        File recipeFile = new File(plugin.getDataFolder(), "Recipe.yml");
        if (!recipeFile.exists()) {
            plugin.saveResource("Recipe.yml", false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(recipeFile);

        for (String recipeName : config.getKeys(false)) {
            ConfigurationSection recipeSection = config.getConfigurationSection(recipeName);
            if (recipeSection != null) {
                RecipeData recipeData = parseRecipe(recipeName, recipeSection);
                if (recipeData != null) {
                    recipes.put(recipeName, recipeData);
                    plugin.getLogger().info("加载自定义配方: " + recipeName);
                }
            }
        }
    }

    /**
     * 解析配方配置
     */
    private RecipeData parseRecipe(String name, ConfigurationSection section) {
        try {
            String permission = section.getString("permission", "None");
            List<String> pattern = section.getStringList("pattern");
            ConfigurationSection itemSection = section.getConfigurationSection("item");
            ConfigurationSection resultSection = section.getConfigurationSection("result");

            if (pattern.isEmpty() || itemSection == null || resultSection == null) {
                plugin.getLogger().warning("配方 " + name + " 配置不完整");
                return null;
            }

            // 解析材料映射
            Map<Character, String> materialMap = new HashMap<>();
            for (String key : itemSection.getKeys(false)) {
                char symbol = key.charAt(0);
                String materialName = itemSection.getString(key);
                materialMap.put(symbol, materialName);
            }

            // 解析结果物品
            String resultItem = resultSection.getString("item");
            int amount = resultSection.getInt("amount", 1);

            return new RecipeData(name, permission, pattern, materialMap, resultItem, amount);

        } catch (Exception e) {
            plugin.getLogger().severe("解析配方 " + name + " 时出错: " + e.getMessage());
            return null;
        }
    }

    /**
     * 注册所有自定义配方
     */
    private void registerRecipes() {
        if (recipes.isEmpty()) {
            plugin.getLogger().warning("没有找到任何配方配置");
            return;
        }

        plugin.getLogger().info("开始注册 " + recipes.size() + " 个自定义配方");

        for (RecipeData recipeData : recipes.values()) {
            registerRecipe(recipeData);
        }
    }

    /**
     * 注册单个配方
     */
    private void registerRecipe(RecipeData recipeData) {
        try {
            String validKey = generateValidKey(recipeData.name);
            NamespacedKey key = new NamespacedKey(plugin, validKey);

            ItemStack resultItem = createResultItem(recipeData);
            if (resultItem == null || resultItem.getType() == Material.AIR) {
                plugin.getLogger().warning("无法创建有效的结果物品: " + recipeData.resultItem + "，跳过配方注册");
                return;
            }

            ShapedRecipe recipe = new ShapedRecipe(key, resultItem);

            // 设置配方形状
            recipe.shape(recipeData.pattern.toArray(new String[0]));

            // 设置材料 - 为每个材料创建RecipeChoice
            Map<Character, RecipeChoice> validChoices = new HashMap<>();
            List<String> failedMaterials = new ArrayList<>();

            for (Map.Entry<Character, String> entry : recipeData.materialMap.entrySet()) {
                char symbol = entry.getKey();
                String materialName = entry.getValue();
                RecipeChoice recipeChoice = createRecipeChoice(materialName);

                if (recipeChoice != null) {
                    validChoices.put(symbol, recipeChoice);
                } else {
                    failedMaterials.add(materialName);
                }
            }

            // 应用有效的材料选择
            for (Map.Entry<Character, RecipeChoice> entry : validChoices.entrySet()) {
                recipe.setIngredient(entry.getKey(), entry.getValue());
            }

            // 检查是否所有材料都有效
            if (failedMaterials.isEmpty()) {
                // 注册配方
                Bukkit.addRecipe(recipe);
                registeredRecipes.add(key);
                plugin.getLogger().info("成功注册配方: " + recipeData.name + " (key: " + validKey + ")");
                plugin.getLogger().info("配方结果物品: " + resultItem.getType() +
                        (resultItem.hasItemMeta() && resultItem.getItemMeta().hasDisplayName() ?
                                " (" + resultItem.getItemMeta().getDisplayName() + ")" : ""));
            } else {
                plugin.getLogger().warning("配方 " + recipeData.name + " 的以下材料无法识别: " + String.join(", ", failedMaterials));
                plugin.getLogger().warning("配方未注册");
            }

        } catch (Exception e) {
            plugin.getLogger().severe("注册配方 " + recipeData.name + " 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 创建RecipeChoice用于配方材料
     */
    private RecipeChoice createRecipeChoice(String materialName) {
        // 检查是否为特殊标记（如X表示空位）
        if (materialName.equalsIgnoreCase("AIR") || materialName.equals("X")) {
            return null; // 空位不设置材料
        }

        // 直接从NeigeItems物品库获取物品
        try {
            // 使用NeigeItemsAPI直接获取物品，不依赖玩家
            ItemStack niItem = getNiItemFromRegistry(materialName);
            if (niItem != null && niItem.getType() != Material.AIR) {
                plugin.getLogger().info("成功从物品库获取NeigeItems材料: " + materialName);
                // 对于NeigeItems物品，创建ExactChoice以保持物品的完整信息
                return new RecipeChoice.ExactChoice(niItem);
            } else {
                plugin.getLogger().warning("物品库中未找到NeigeItems材料: " + materialName);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("从物品库获取NeigeItems材料异常 " + materialName + ": " + e.getMessage());
        }

        // 如果不是NI物品，尝试作为原版物品
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            ItemStack vanillaItem = new ItemStack(material);
            plugin.getLogger().info("使用原版材料: " + materialName);
            return new RecipeChoice.MaterialChoice(vanillaItem.getType());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无法识别的材料: " + materialName);
            return null;
        }
    }

    /**
     * 从NeigeItems物品库直接获取物品（不依赖玩家）
     */
    private ItemStack getNiItemFromRegistry(String itemId) {
        try {
            // 尝试通过反射或其他方式直接访问NeigeItems的物品注册表
            // 这里需要根据NeigeItems的具体API来实现

            // 方法1: 尝试使用ItemManager的静态方法
            // ItemStack item = ItemManager.getItemStack(itemId, null, new HashMap<>());

            // 方法2: 如果上述方法不可用，尝试创建一个临时玩家上下文
            Player dummyPlayer = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
            if (dummyPlayer != null) {
                return NeigeItemsAPI.getItemStack(itemId, dummyPlayer);
            }

            // 如果都没有玩家在线，返回null
            return null;

        } catch (Exception e) {
            plugin.getLogger().warning("从物品库获取物品失败 " + itemId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 生成有效的NamespacedKey
     */
    private String generateValidKey(String originalName) {
        if (VALID_KEY_PATTERN.matcher(originalName.toLowerCase()).matches()) {
            return originalName.toLowerCase();
        }

        // 简单的中文转英文映射
        Map<String, String> mapping = new HashMap<>();
        mapping.put("火行阵旗", "fire_array_flag");
        mapping.put("火元石", "fire_element_stone");
        mapping.put("水行阵旗", "water_array_flag");
        mapping.put("木行阵旗", "wood_array_flag");
        mapping.put("金行阵旗", "metal_array_flag");
        mapping.put("土行阵旗", "earth_array_flag");

        String mapped = mapping.getOrDefault(originalName, "recipe_" + Math.abs(originalName.hashCode()));
        return mapped.toLowerCase();
    }

    /**
     * 创建结果物品
     */
    public ItemStack createResultItem(RecipeData recipeData) {
        // 检查是否为特殊值
        if (recipeData.resultItem == null || recipeData.resultItem.trim().isEmpty()) {
            plugin.getLogger().warning("结果物品名称为空");
            return new ItemStack(Material.STONE, recipeData.amount);
        }

        // 直接从NeigeItems物品库获取结果物品
        try {
            ItemStack niItem = getNiItemFromRegistry(recipeData.resultItem);
            if (niItem != null && niItem.getType() != Material.AIR) {
                plugin.getLogger().info("成功从物品库获取NeigeItems结果物品: " + recipeData.resultItem);
                niItem.setAmount(recipeData.amount);
                return niItem.clone();
            } else {
                plugin.getLogger().warning("物品库中未找到NeigeItems结果物品: " + recipeData.resultItem);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("从物品库获取NeigeItems结果物品异常 " + recipeData.resultItem + ": " + e.getMessage());
        }

        // 如果不是NI物品，尝试作为原版物品
        try {
            Material material = Material.valueOf(recipeData.resultItem.toUpperCase());
            plugin.getLogger().info("使用原版结果物品: " + recipeData.resultItem);
            return new ItemStack(material, recipeData.amount);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无法识别的结果物品: " + recipeData.resultItem);
            return new ItemStack(Material.STONE, recipeData.amount);
        }
    }

    /**
     * 获取材料物品（用于事件检查）
     */
    public ItemStack getMaterialItem(String materialName) {
        // 直接从NeigeItems物品库获取
        try {
            ItemStack niItem = getNiItemFromRegistry(materialName);
            if (niItem != null && niItem.getType() != Material.AIR) {
                return niItem.clone();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("从物品库获取NeigeItems材料失败: " + materialName);
        }

        // 如果不是NI物品，尝试作为原版物品
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 重新加载配方配置
     */
    public boolean reloadRecipes() {
        try {
            plugin.getLogger().info("开始重载配方配置...");

            // 移除已注册的配方
            int removedCount = 0;
            for (NamespacedKey key : registeredRecipes) {
                if (Bukkit.removeRecipe(key)) {
                    removedCount++;
                }
            }
            plugin.getLogger().info("移除了 " + removedCount + " 个已注册配方");

            registeredRecipes.clear();
            recipes.clear();

            // 重新加载和注册
            loadRecipes();
            registerRecipes();

            plugin.getLogger().info("配方配置重载完成，当前有 " + recipes.size() + " 个配方");
            return true;

        } catch (Exception e) {
            plugin.getLogger().severe("重载配方配置失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 检查是否已初始化完成
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * 获取配方数据
     */
    public RecipeData getRecipeData(String recipeName) {
        return recipes.get(recipeName);
    }

    /**
     * 获取所有配方数据
     */
    public Collection<RecipeData> getAllRecipes() {
        return recipes.values();
    }

    /**
     * 检查物品是否为配方结果
     */
    public RecipeData findRecipeByResult(ItemStack result) {
        if (result == null || result.getType() == Material.AIR) {
            return null;
        }

        for (RecipeData recipeData : recipes.values()) {
            ItemStack expectedResult = createResultItem(recipeData);
            if (expectedResult != null && expectedResult.isSimilar(result)) {
                return recipeData;
            }
        }
        return null;
    }

    /**
     * 获取已注册的配方数量
     */
    public int getRecipeCount() {
        return recipes.size();
    }

    /**
     * 获取配方名称列表
     */
    public List<String> getRecipeNames() {
        return new ArrayList<>(recipes.keySet());
    }

    /**
     * 配方数据类
     */
    public static class RecipeData {
        public final String name;
        public final String permission;
        public final List<String> pattern;
        public final Map<Character, String> materialMap;
        public final String resultItem;
        public final int amount;

        public RecipeData(String name, String permission, List<String> pattern,
                          Map<Character, String> materialMap, String resultItem, int amount) {
            this.name = name;
            this.permission = permission;
            this.pattern = pattern;
            this.materialMap = materialMap;
            this.resultItem = resultItem;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "RecipeData{" +
                    "name='" + name + '\'' +
                    ", permission='" + permission + '\'' +
                    ", resultItem='" + resultItem + '\'' +
                    ", amount=" + amount +
                    '}';
        }
    }
}
