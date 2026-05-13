package com.horizonkk.itemAction;

import com.horizonkk.itemAction.command.ItemActionCommand;
import com.horizonkk.itemAction.event.*;
import com.horizonkk.itemAction.hook.PlaceholderAPI;
import com.horizonkk.itemAction.manager.ActionManager;
import com.horizonkk.itemAction.manager.ChargeManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public final class ItemAction extends JavaPlugin {
    private ActionManager actionManager;
    private PlaceholderAPI placeholderAPI;
    private ItemActionCommand commandHandler;
    private ChargeManager chargeManager; // 新增共享读条管理器
    private FkeyAction fkeyAction;
    private RightKeyAction rightKeyAction; // 新增
    private CustomRecipe customRecipe;

    @Override
    public void onEnable() {
        // Plugin startup logic
        actionManager = new ActionManager(this);
        chargeManager = new ChargeManager(this); // 初始化共享读条管理器
        // 初始化ActionManager
        // 注册PlaceholderAPI
        // 注册事件
        registerEvents();
        registerPlaceholderAPI();
        // 注册命令
        registerCommands();
        getLogger().info("ItemAction物品动作 插件已启用!");
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public void registerEvents(){
        // 注册事件监听器
        fkeyAction = new FkeyAction(this);
        rightKeyAction = new RightKeyAction(this);
//        customRecipe = new CustomRecipe(this); 注释自定义合成配方
        getServer().getPluginManager().registerEvents(fkeyAction, this);
        getServer().getPluginManager().registerEvents(rightKeyAction, this);
        getServer().getPluginManager().registerEvents(new HandTick(this), this);
        getServer().getPluginManager().registerEvents(new CancelEvent(this), this);
//        getServer().getPluginManager().registerEvents(customRecipe, this); 注释自定义合成配方
    }

    private void registerPlaceholderAPI() {
        placeholderAPI = new PlaceholderAPI(this);
        if (placeholderAPI.register()) {
            getLogger().info("PlaceholderAPI扩展已注册");
        } else {
            getLogger().warning("PlaceholderAPI扩展注册失败");
        }
    }

    private void registerCommands() {
        commandHandler = new ItemActionCommand(this);
        this.getCommand("ItemAction").setExecutor(commandHandler);
        this.getCommand("ItemAction").setTabCompleter(commandHandler);
        getLogger().info("命令处理器已注册");
    }

    public ActionManager getActionManager() {
        return actionManager;
    }

    public Map<String, Object> getItemConfig(String itemId) {
        return actionManager.getItemConfig(itemId);
    }

    public PlaceholderAPI getPlaceholderAPI() {
        return placeholderAPI;
    }

    public ItemActionCommand getCommandHandler() {
        return commandHandler;
    }

    public CustomRecipe getCustomRecipe() {
        return customRecipe;
    }

    public FkeyAction getFkeyAction() {
        return fkeyAction;
    }
    public RightKeyAction getRightKeyAction() { // 新增getter方法
        return rightKeyAction;
    }
    public ChargeManager getChargeManager() { // 新增getter方法
        return chargeManager;
    }
}
