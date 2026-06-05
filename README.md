# ItemAction — 物品动作

一个基于 Spigot 1.21 的 Minecraft 服务器插件，允许为 NeigeItems 自定义物品绑定交互动作，并支持技能吟唱条（蓄力条）系统。

## ✨ 功能特性

- **物品自定义动作** — 为 NeigeItems 定义的物品绑定 F 键、右键等交互动作，触发自定义命令
- **技能吟唱条（蓄力系统）** — 动作可配置蓄力时间，玩家需长按指定按键等待吟唱完成，期间显示进度条
- **多种执行模式** — 命令可以以玩家身份、临时 OP 身份或控制台身份执行
- **权限控制** — 物品动作可基于权限节点进行限制，无权限时显示不同提示文本
- **物品分组模板** — 通过 NBT 标签匹配物品分组，批量应用动作模板，避免逐个配置
- **PlaceholderAPI 变量** — 提供丰富的 PlaceholderAPI 占位符，方便其他插件读取物品动作状态

## 📦 依赖

| 依赖 | 说明 |
|------|------|
| [Spigot API](https://www.spigotmc.org/) 1.21+ | 服务端 API |
| [NeigeItems](https://github.com/NeigeSoft/NeigeItems) | 自定义物品插件（硬依赖） |
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) | 占位符变量插件（硬依赖） |

## 🔧 安装

1. 确保服务端已安装 **NeigeItems** 和 **PlaceholderAPI**
2. 将编译好的 `ItemAction.jar` 放入服务端 `plugins/` 目录
3. 启动服务器，插件将自动生成默认配置文件
4. 根据需要编辑 `plugins/ItemAction/` 下的配置文件
5. 使用 `/ItemAction reload` 重载配置

## 📖 配置说明

### `config.yml` — 主配置

```yaml
# 调试模式
debug: false

# 消息前缀
prefix: "[物品动作]"

# 无权限提示
no-permission: "§c你没有权限使用此物品"

# 仅限玩家提示
player-only: "§c该命令仅限玩家执行"

# 手持物品检测（开关 & 检测间隔）
hand-item-check:
  enabled: true
  interval: 20  # ticks
```

### `Action.yml` — 物品动作配置

为每个 NeigeItems 物品 ID 定义交互动作：

```yaml
example_item:
  Id: DIAMOND
  action:
    - F           # F 键（交换副手）
    - right       # 右键
  time: 3.0       # 吟唱时间（秒），0 或不填则立即执行
  move: false     # 吟唱时是否允许移动
  amount: 1       # 执行后扣除数量
  command:
    - type: normal          # normal=玩家执行, op=临时OP执行, console=控制台执行
      command: "say %player% 使用了示例物品"
  text: "§a正在使用示例物品..."
  permission: "itemaction.use.example"
  permission-text: "§c你没有权限使用此物品"
```

**配置项说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `action` | 列表 | 触发方式：`F` / `right` / `left` / `shift` |
| `time` | 浮点数 | 吟唱时间（秒），0 表示立即执行 |
| `move` | 布尔值 | 吟唱期间是否允许移动，默认 `false` |
| `amount` | 整数 | 执行后扣除的物品数量 |
| `command` | 列表 | 要执行的命令列表 |
| `command[].type` | 字符串 | `normal`（玩家）/ `op`（临时OP）/ `console`（控制台） |
| `text` | 字符串 | 吟唱时显示的提示文本 |
| `permission` | 字符串 | 所需权限节点 |
| `permission-text` | 字符串 | 无权限时的提示文本 |

### `group.yml` — 物品分组模板

通过 NBT 标签匹配同类物品，批量应用动作模板：

```yaml
spirit_stone_group:
  match:
    tag: "CustomModelData"    # 匹配的 NBT 标签路径
    value: 1001               # 匹配值
  template:
    action:
      - F
    time: 2.0
    amount: 1
    command:
      - type: op
        command: "give %player% diamond 1"
    text: "§e正在使用灵石...%itemid%"
```

模板中支持 `%itemid%`、`%player%`、`%uuid%` 占位符。

## 🎮 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/ItemAction reload` | 重载所有配置文件 | `itemaction.reload`（默认 OP） |
| `/ItemAction help` | 查看帮助信息 | 所有玩家 |
| `/ItemAction info` | 查看插件信息 | 所有玩家 |
| `/ItemAction version` | 查看版本信息 | 所有玩家 |

## 📊 PlaceholderAPI 变量

插件注册了 `itemaction` 标识符，可通过 `%itemaction_变量名%` 使用：

### 物品信息

| 变量 | 说明 |
|------|------|
| `%itemaction_item_id%` | 手持物品的 NeigeItems ID |
| `%itemaction_item_name%` | 手持物品名称 |
| `%itemaction_item_exists%` | 手持物品是否为 NeigeItems 物品 |

### 动作能力

| 变量 | 说明 |
|------|------|
| `%itemaction_can_use_f%` | 是否支持 F 键动作 |
| `%itemaction_can_use_right%` | 是否支持右键动作 |
| `%itemaction_can_use_left%` | 是否支持左键动作 |
| `%itemaction_can_use_shift%` | 是否支持 Shift 动作 |

### 物品吟唱状态

| 变量 | 说明 |
|------|------|
| `%itemaction_is_charging%` | 是否正在吟唱 |
| `%itemaction_charging_progress%` | 吟唱进度（秒） |
| `%itemaction_charging_progress_percent%` | 吟唱进度百分比 |
| `%itemaction_charging_type%` | 吟唱触发类型（F/right 等） |
| `%itemaction_charging_text%` | 吟唱提示文本（带权限判断） |

### 技能吟唱状态

| 变量 | 说明 |
|------|------|
| `%itemaction_spell_is_charging%` | 是否正在技能吟唱 |
| `%itemaction_spell_charging_progress%` | 技能吟唱进度（秒） |
| `%itemaction_spell_charging_progress_percent%` | 技能吟唱进度百分比 |
| `%itemaction_spell_id%` | 当前技能 ID |

## 🔌 API

插件提供了两套蓄力 API 供其他插件调用：

### ChargeAPI — 物品蓄力

适用于物品交互场景。蓄力期间切换物品、移动（可配置）会取消蓄力。

```java
ChargeAPI chargeAPI = ItemAction.getInstance().getChargeManager();

// 开始蓄力
chargeAPI.startCharging(player, itemId, chargeTime, chargeType, moveAllowed);

// 取消蓄力
chargeAPI.cancelCharging(player);

// 查询状态
boolean charging = chargeAPI.isPlayerCharging(player);
double progress = chargeAPI.getChargeProgressPercent(player);
```

### SpellChargeAPI — 技能蓄力

适用于外部技能插件。蓄力期间切换物品和移动**不会**取消，新技能会自动覆盖旧技能。支持毫秒精度。

```java
SpellChargeAPI spellAPI = ItemAction.getInstance().getSpellChargeManager();

// 开始技能蓄力（毫秒精度，带完成回调）
spellAPI.startChargingMillis(player, spellId, 1500, () -> {
    // 吟唱完成，执行技能逻辑
});

// 取消技能蓄力
spellAPI.cancelCharging(player);
```

## 🏗️ 构建

```bash
# 克隆项目
git clone <repository-url>
cd ItemAction

# Maven 编译打包
mvn clean package

# 产物位于 target/ 目录
```

> **注意：** NeigeItems API 以本地 JAR 形式引入（`libs/` 目录），编译前无需额外配置。

## 📁 项目结构

```
src/main/java/com/horizonkk/itemAction/
├── ItemAction.java              # 插件主类
├── api/
│   ├── ChargeAPI.java           # 物品蓄力 API 接口
│   └── SpellChargeAPI.java      # 技能蓄力 API 接口
├── command/
│   └── ItemActionCommand.java   # 命令处理器
├── event/
│   ├── CancelEvent.java         # 蓄力取消监听器
│   ├── FkeyAction.java          # F 键动作处理
│   ├── HandTick.java            # 手持物品监听（预留）
│   └── RightKeyAction.java      # 右键动作处理
├── hook/
│   ├── NeigeItemsAPI.java       # NeigeItems 集成封装
│   └── PlaceholderAPI.java      # PlaceholderAPI 变量扩展
├── manager/
│   ├── ActionManager.java       # 动作配置管理器
│   ├── ChargeManager.java       # 物品蓄力管理器
│   ├── GroupManager.java        # 物品分组管理器
│   └── SpellChargeManager.java  # 技能蓄力管理器
└── util/
    └── NbtUtils.java            # NBT 工具类（反射读取）
```

## 📋 适用版本

- **Minecraft**: 1.21+
- **服务端**: Spigot / Paper
- **Java**: 21+

## 📄 许可证

本项目仅供学习交流使用。

---

**作者**: HorizonKK
