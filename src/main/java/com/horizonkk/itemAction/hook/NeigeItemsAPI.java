package com.horizonkk.itemAction.hook;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import pers.neige.neigeitems.item.ItemPack;
import pers.neige.neigeitems.manager.ItemManager;
import pers.neige.neigeitems.manager.ItemPackManager;
import pers.neige.neigeitems.utils.ItemUtils;
import pers.neige.neigeitems.item.ItemInfo;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class NeigeItemsAPI {

    /**
     * 获取物品
     * @param itemId 物品ID
     * @param player 玩家对象
     * @param data 数据参数
     * @return ItemStack 物品堆
     */
    public static ItemStack getItemStack(String itemId, Player player, Map<String, Object> data) {
        // 将Object类型的Map转换为String类型的Map
        Map<String, String> stringData = new HashMap<>();
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                stringData.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return ItemManager.INSTANCE.getItemStack(itemId, player, stringData);
    }

    /**
     * 获取物品（简化版本）
     * @param itemId 物品ID
     * @param player 玩家对象
     * @return ItemStack 物品堆
     */
    public static ItemStack getItemStack(String itemId, Player player) {
        return ItemManager.INSTANCE.getItemStack(itemId, player, new HashMap<>());
    }

    /**
     * 获取物品包
     * @param packId 物品包ID
     * @return ItemPack 物品包对象
     */
    public static ItemPack getItemPack(String packId) {
        return ItemPackManager.INSTANCE.getItemPack(packId);
    }


    /**
     * 获取NI物品ID
     * @param itemStack 物品堆
     * @return String 物品ID
     */
    public static String getItemId(ItemStack itemStack) {
        return ItemUtils.getItemId(itemStack);
    }

    /**
     * 获取NI物品ID及物品节点信息
     * @param itemStack 物品堆
     * @return ItemInfo 物品信息对象
     */
    public static ItemInfo getItemInfo(ItemStack itemStack) {
        return ItemUtils.isNiItem(itemStack);
    }

    /**
     * 检查物品是否为NI物品
     * @param itemStack 物品堆
     * @return boolean 是否为NI物品
     */
    public static boolean isNiItem(ItemStack itemStack) {
        return ItemUtils.isNiItem(itemStack) != null;
    }

    /**
     * 安全获取物品ID（包含空值检查）
     * @param itemStack 物品堆
     * @return String 物品ID，如果不存在则返回null
     */
    public static String getSafeItemId(ItemStack itemStack) {
        if (itemStack == null) return null;
        return getItemId(itemStack);
    }

    /**
     * 安全获取物品信息（包含空值检查）
     * @param itemStack 物品堆
     * @return ItemInfo 物品信息对象，如果不存在则返回null
     */
    public static ItemInfo getSafeItemInfo(ItemStack itemStack) {
        if (itemStack == null) return null;
        return getItemInfo(itemStack);
    }
}
