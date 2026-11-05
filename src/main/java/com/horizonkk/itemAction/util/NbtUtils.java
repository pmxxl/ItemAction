package com.horizonkk.itemAction.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * NBT工具类 - 用于读取物品的原始NBT标签
 * 支持Minecraft 1.20.5+ (Data Components) 和旧版本的NBT访问
 */
public class NbtUtils {

    private static final Logger LOG = Bukkit.getLogger();
    private static String serverVersion;
    private static boolean versionDetected = false;

    static {
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            serverVersion = pkg.substring(pkg.lastIndexOf('.') + 1);
            versionDetected = true;
        } catch (Exception e) {
            serverVersion = null;
            LOG.warning("[NbtUtils] 无法检测服务器版本: " + e.getMessage());
        }
    }

    private static final String PDC_PREFIX = "PublicBukkitValues";

    public static String getNbtString(ItemStack item, String key) {
        if (item == null || key == null || !versionDetected) return null;

        Object nmsItem = getNmsItem(item);
        if (nmsItem == null) return null;

        Object rootTag = getRootTag(nmsItem);
        if (rootTag == null) return null;

        if (key.contains(".")) {
            return getNestedString(rootTag, key);
        }

        // 不含 . → 先根NBT，再 PublicBukkitValues
        String value = getNestedString(rootTag, key);
        if (value != null) return value;
        return getNestedString(rootTag, PDC_PREFIX + "." + key);
    }

    private static Object getRootTag(Object nmsItem) {
        // 方式1：DataComponents.CUSTOM_DATA（1.20.5+）
        Object tag = getViaComponents(nmsItem);
        if (tag != null) return tag;

        // 方式2：getTag()（旧版本回退）
        return getViaLegacyTag(nmsItem);
    }

    private static Object getViaComponents(Object nmsItem) {
        try {
            Method getComponents = findMethod(nmsItem.getClass(), "getComponents");
            if (getComponents == null) return null;
            Object components = getComponents.invoke(nmsItem);
            if (components == null) return null;

            Class<?> dataComponentsClass = findClass("net.minecraft.core.component.DataComponents");
            if (dataComponentsClass == null) return null;

            Object customDataType = dataComponentsClass.getField("CUSTOM_DATA").get(null);
            if (customDataType == null) return null;

            Method get = findMethod(components.getClass(), "get");
            if (get == null) return null;
            Object customData = get.invoke(components, customDataType);
            if (customData == null) return null;

            // CustomData 包装 → 解包取 CompoundTag
            return unwrapCustomData(customData);

        } catch (Exception e) {
            return null;
        }
    }

    private static Object unwrapCustomData(Object customData) {
        // 尝试 copyTag()
        Method m = findMethod(customData.getClass(), "copyTag");
        if (m != null) {
            try { return m.invoke(customData); } catch (Exception ignored) {}
        }
        // 尝试 getUnsafe()
        m = findMethod(customData.getClass(), "getUnsafe");
        if (m != null) {
            try { return m.invoke(customData); } catch (Exception ignored) {}
        }
        // 尝试 contents()
        m = findMethod(customData.getClass(), "contents");
        if (m != null) {
            try { return m.invoke(customData); } catch (Exception ignored) {}
        }
        // 兜底：直接返回原对象
        return customData;
    }

    private static Object getViaLegacyTag(Object nmsItem) {
        try {
            Method getTag = findMethod(nmsItem.getClass(), "getTag");
            if (getTag == null) return null;
            return getTag.invoke(nmsItem);
        } catch (Exception e) {
            return null;
        }
    }

    private static String getNestedString(Object tag, String path) {
        if (tag == null || path == null) return null;

        String[] parts = path.split("\\.");
        Object currentTag = tag;

        try {
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLast = (i == parts.length - 1);

                if (isLast) {
                    Method getString = currentTag.getClass().getMethod("getString", String.class);
                    Object value = unwrapOptional(getString.invoke(currentTag, part));
                    return value instanceof String string && !string.isEmpty() ? string : null;
                } else {
                    Method getCompound = findCompoundGetter(currentTag.getClass());
                    if (getCompound == null) return null;
                    currentTag = unwrapOptional(getCompound.invoke(currentTag, part));
                    if (currentTag == null) return null;
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    private static Object unwrapOptional(Object value) {
        // 1.21.5+ 的 NBT getter 返回 Optional；旧版本直接返回标签或字符串。
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    private static Method findMethod(Class<?> clazz, String methodName) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() <= 1) {
                return m;
            }
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() <= 1) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static Method findCompoundGetter(Class<?> clazz) {
        for (Method m : clazz.getMethods()) {
            String name = m.getName();
            if ((name.equals("getCompound") || name.equals("getCompoundTag"))
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == String.class) {
                return m;
            }
        }
        return null;
    }

    public static boolean hasNbtValue(ItemStack item, String key, String value) {
        if (value == null) return false;
        String actual = getNbtString(item, key);
        return value.equals(actual);
    }

    private static Object getNmsItem(ItemStack item) {
        String[] possiblePaths = {
                "org.bukkit.craftbukkit." + serverVersion + ".inventory.CraftItemStack",
                "org.bukkit.craftbukkit.inventory.CraftItemStack"
        };

        for (String path : possiblePaths) {
            try {
                Class<?> craftItemStack = Class.forName(path);
                Method asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
                return asNMSCopy.invoke(null, item);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
