package com.horizonkk.itemAction.command;

import com.horizonkk.itemAction.ItemAction;
import com.horizonkk.itemAction.manager.ActionManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ItemActionCommand implements CommandExecutor, TabCompleter {

    private final ItemAction plugin;
    private final ActionManager actionManager;

    public ItemActionCommand(ItemAction plugin) {
        this.plugin = plugin;
        this.actionManager = plugin.getActionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 检查参数数量
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        // 处理不同的子命令
        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReloadCommand(sender);
            case "help":
                sendHelpMessage(sender);
                return true;
            case "info":
                sendPluginInfo(sender);
                return true;
            case "version":
                sendVersionInfo(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "未知的子命令: " + args[0]);
                sendHelpMessage(sender);
                return false;
        }
    }

    /**
     * 处理重载命令 - 重载所有配置文件
     */
    private boolean handleReloadCommand(CommandSender sender) {
        // 权限检查
        if (!sender.hasPermission("itemaction.reload")) {
            sender.sendMessage(formatMessage("&c你没有权限执行此命令!"));
            return false;
        }

        try {
            sender.sendMessage(formatMessage("&e正在重载所有配置文件..."));

            // 重载Action配置（内部会同时重载group.yml）
            actionManager.reloadConfig();

            // 重载主配置文件
            plugin.reloadConfig();

            // 发送成功消息
            sender.sendMessage(formatMessage("&a所有配置文件重载成功!"));
            sender.sendMessage(formatMessage("&7- Action.yml 已重载"));
            sender.sendMessage(formatMessage("&7- group.yml 已重载"));
            sender.sendMessage(formatMessage("&7- config.yml 已重载"));

            // 如果是玩家，发送ActionBar提示
            if (sender instanceof Player) {
                Player player = (Player) sender;
                player.sendMessage(formatMessage("&a所有配置重载完成!"));
            }

            // 记录日志
            plugin.getLogger().info(sender.getName() + " 执行了全部配置重载命令");
            return true;

        } catch (Exception e) {
            sender.sendMessage(formatMessage("&c重载配置文件时发生错误: " + e.getMessage()));
            plugin.getLogger().severe("重载配置文件失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(formatMessage("&6==== &eItemAction 命令帮助 &6===="));
        sender.sendMessage(formatMessage("&e/ItemAction reload &7- 重载所有配置文件 (Action.yml, config.yml)"));
        sender.sendMessage(formatMessage("&e/ItemAction help &7- 显示此帮助信息"));
        sender.sendMessage(formatMessage("&e/ItemAction info &7- 显示插件信息"));
        sender.sendMessage(formatMessage("&e/ItemAction version &7- 显示版本信息"));
        if (sender.hasPermission("itemaction.reload")) {
            sender.sendMessage(formatMessage("&c需要权限: itemaction.reload (重载命令)"));
        }
        sender.sendMessage(formatMessage("&6========================"));
    }

    /**
     * 发送插件信息
     */
    private void sendPluginInfo(CommandSender sender) {
        sender.sendMessage(formatMessage("&6==== &eItemAction 插件信息 &6===="));
        sender.sendMessage(formatMessage("&e版本: &f" + plugin.getDescription().getVersion()));
        sender.sendMessage(formatMessage("&e作者: &f" + String.join(", ", plugin.getDescription().getAuthors())));
        sender.sendMessage(formatMessage("&eAPI版本: &f" + plugin.getDescription().getAPIVersion()));
        sender.sendMessage(formatMessage("&e描述: &f" + plugin.getDescription().getDescription()));
        sender.sendMessage(formatMessage("&e命令别名: &f/ia, /itemaction"));
        sender.sendMessage(formatMessage("&6========================"));
    }

    /**
     * 发送版本信息
     */
    private void sendVersionInfo(CommandSender sender) {
        sender.sendMessage(formatMessage("&6==== &e版本信息 &6===="));
        sender.sendMessage(formatMessage("&e插件版本: &f" + plugin.getDescription().getVersion()));
        sender.sendMessage(formatMessage("&eBukkit版本: &f" + plugin.getServer().getBukkitVersion()));
        sender.sendMessage(formatMessage("&e服务器版本: &f" + plugin.getServer().getVersion()));
        sender.sendMessage(formatMessage("&6=================="));
    }

    /**
     * Tab自动补全
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // 第一个参数的自动补全
            completions.add("help");
            completions.add("info");
            completions.add("version");

            // 只有有权限的用户才能看到reload相关命令
            if (sender.hasPermission("itemaction.reload")) {
                completions.add("reload");
            }

            // 过滤匹配用户输入的部分
            return filterCompletions(completions, args[0]);
        }

        return completions;
    }

    /**
     * 过滤补全建议
     */
    private List<String> filterCompletions(List<String> completions, String input) {
        List<String> filtered = new ArrayList<>();
        String lowerInput = input.toLowerCase();

        for (String completion : completions) {
            if (completion.toLowerCase().startsWith(lowerInput)) {
                filtered.add(completion);
            }
        }
        return filtered;
    }

    /**
     * 格式化消息颜色
     */
    private String formatMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
