package com.horizonkk.itemAction.event;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.hook.NeigeItemsAPI;
import com.horizonkk.itemAction.manager.RecipeManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

public class CustomRecipe implements Listener {

    private final ItemAction plugin;
    private final RecipeManager recipeManager;
    private boolean recipesRegistered = false;

    public CustomRecipe(ItemAction plugin) {
        this.plugin = plugin;
        this.recipeManager = new RecipeManager(plugin);

        // 延迟检查配方注册状态
        new BukkitRunnable() {
            @Override
            public void run() {
                if (recipeManager.isInitialized()) {
                    recipesRegistered = true;
                    plugin.getLogger().info("配方系统已准备就绪，共加载 " + recipeManager.getRecipeCount() + " 个配方");
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 每秒检查一次
    }

    /**
     * 获取配方管理器实例
     */
    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    /**
     * 合成事件监听 - 检查权限和材料匹配
     */
    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        if (!recipesRegistered) {
            return; // 配方系统还未准备好
        }

        Player player = (Player) event.getWhoClicked();
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();

        if (result == null || result.getType() == Material.AIR) return;

        // 检查是否为自定义配方
        RecipeManager.RecipeData recipeData = recipeManager.findRecipeByResult(result);
        if (recipeData != null) {
            plugin.getLogger().info("检测到自定义配方合成: " + recipeData.name);

            // 检查权限
            if (!recipeData.permission.equals("None") && !player.hasPermission(recipeData.permission)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "你没有权限制作这个物品!");
                plugin.getLogger().info("玩家 " + player.getName() + " 缺少权限: " + recipeData.permission);
                return;
            }

            // 检查材料是否匹配
            if (!checkMaterialsMatch(inventory, recipeData)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "材料不匹配!");
                plugin.getLogger().info("玩家 " + player.getName() + " 材料不匹配");
                return;
            }

            plugin.getLogger().info("玩家 " + player.getName() + " 成功合成了: " + recipeData.name);
        }
    }

    /**
     * 检查合成材料是否匹配
     */
    private boolean checkMaterialsMatch(CraftingInventory inventory, RecipeManager.RecipeData recipeData) {
        // 获取合成网格中的物品
        ItemStack[] matrix = inventory.getMatrix();

        // 将配方模式转换为索引映射
        Map<Integer, String> patternMap = new java.util.HashMap<>();
        int index = 0;
        for (String row : recipeData.pattern) {
            for (char c : row.toCharArray()) {
                if (c != 'X') { // X通常表示空位
                    patternMap.put(index, String.valueOf(c));
                }
                index++;
            }
        }

        // 检查每个位置的材料
        for (int i = 0; i < Math.min(matrix.length, 9); i++) {
            ItemStack itemInSlot = matrix[i];
            String requiredSymbol = patternMap.get(i);

            if (requiredSymbol != null) {
                String requiredMaterial = recipeData.materialMap.get(requiredSymbol.charAt(0));
                if (!isMatchingMaterial(itemInSlot, requiredMaterial)) {
                    plugin.getLogger().warning("材料不匹配 - 位置 " + i + ": 期望 " + requiredMaterial + ", 实际 " +
                            (itemInSlot != null ? itemInSlot.getType().name() : "空"));
                    return false;
                }
            } else if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
                // 不应该有物品的位置却有物品
                plugin.getLogger().warning("位置 " + i + " 不应该有物品，但发现了: " + itemInSlot.getType().name());
                return false;
            }
        }

        return true;
    }

    /**
     * 检查物品是否与所需材料匹配
     */
    private boolean isMatchingMaterial(ItemStack item, String materialName) {
        if (item == null || item.getType() == Material.AIR) {
            return materialName.equalsIgnoreCase("AIR") || materialName.equals("X");
        }

        // 首先检查是否为NeigeItems物品
        String niItemId = NeigeItemsAPI.getSafeItemId(item);
        if (niItemId != null) {
            boolean matches = niItemId.equals(materialName);
            if (!matches) {
                plugin.getLogger().fine("NI物品ID不匹配: " + niItemId + " vs " + materialName);
            }
            return matches;
        }

        // 检查是否为原版物品
        try {
            Material requiredMaterial = Material.valueOf(materialName.toUpperCase());
            return item.getType() == requiredMaterial;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无法识别的材料类型: " + materialName);
            return false;
        }
    }

    /**
     * 预合成事件监听 - 显示配方信息
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!recipesRegistered) {
            return; // 配方系统还未准备好
        }

        if (!(event.getView().getPlayer() instanceof Player)) return;

        Player player = (Player) event.getView().getPlayer();
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();

        if (result == null || result.getType() == Material.AIR) return;

        // 检查是否为自定义配方并显示信息
        RecipeManager.RecipeData recipeData = recipeManager.findRecipeByResult(result);
        if (recipeData != null) {
        }
    }

    /**
     * 重新加载配方配置
     */
    public boolean reloadRecipes() {
        boolean result = recipeManager.reloadRecipes();
        if (result) {
            recipesRegistered = true;
        }
        return result;
    }

    /**
     * 获取配方数量
     */
    public int getRecipeCount() {
        return recipeManager.getRecipeCount();
    }

    /**
     * 获取配方名称列表
     */
    public java.util.List<String> getRecipeNames() {
        return recipeManager.getRecipeNames();
    }

    /**
     * 检查配方系统是否已准备就绪
     */
    public boolean isRecipesRegistered() {
        return recipesRegistered && recipeManager.isInitialized();
    }
}
