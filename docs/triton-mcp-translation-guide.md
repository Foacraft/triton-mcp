# triton-mcp 自动翻译配置指南

> 本文档供 Claude 等 AI 对话使用，说明如何借助 triton-mcp 工具将 Minecraft 插件的配置文件消息文本批量录入 Triton 翻译系统，并将原始配置文件中的文本替换为 Triton 占位符。

---

## 一、Triton 翻译系统基础

### 核心概念

**Collection（集合）**
翻译条目的分组容器，类似文件夹。每个条目必须归属于某个集合。
- 默认集合名为 `default`
- 建议按插件名建集合，例如 `essentials`、`shop`、`survival`

**Translation Item（翻译条目）**
单条翻译，分两种类型：
- `text` — 文本消息（本指南的操作对象）
- `sign` — 告示牌翻译（不在本指南范围内）

**Key（键名）**
条目的唯一标识，使用点分隔的层级命名，例如 `essentials.info.NoPermission`。
Key 在整个系统中全局唯一，不同集合之间 key 也不能重复。

**Languages（语言映射）**
每个条目包含一个语言 → 文本的映射，例如：
```json
{
  "en_GB": "&cYou don't have permission!",
  "zh_CN": "&c你没有权限！"
}
```
语言 ID 与 Triton `config.yml` 中 `languages` 段的 key 严格对应（大小写敏感）。

**Triton 占位符**
替换原始配置文件中的消息，格式为：
- 无变量：`[lang]translation.key[/lang]`
- 有变量：`[lang]translation.key[args][arg]变量1[/arg][arg]变量2[/arg][/args][/lang]`

### variables（变量）处理

原始配置中的动态变量（玩家名、数字等）需替换为 `%1`、`%2`、`%3`……占位：

| 原始配置风格 | 翻译文本中写法 | 占位符 args 中写法 |
|---|---|---|
| `{player}` | `%1` | `[arg]{player}[/arg]` |
| `%player_name%` | `%1` | `[arg]%player_name%[/arg]` |
| `[playerName]` | `%1` | `[arg][playerName][/arg]` |
| `{0}`, `{1}` | `%1`, `%2` | `[arg]{0}[/arg][arg]{1}[/arg]` |
| `<amount>` | `%1` | `[arg]<amount>[/arg]` |

**示例**
原始消息：`"&c{player} 没有权限执行: {permission}"`
翻译文本：`"&c%1 没有权限执行: %2"`
最终占位符：`"[lang]plugin.no_permission[args][arg]{player}[/arg][arg]{permission}[/arg][/args][/lang]"`

---

## 二、哪些字符串应该翻译 ✅

以下特征的字符串是**有意义的消息文本**，应该录入 Triton：

### 明确应翻译
1. **自然语言句子/短语**：含多个单词，构成可读的句子或提示
   - `"&cYou don't have permission!"` ✅
   - `"&a成功购买了 {item}！"` ✅
   - `"&e欢迎来到服务器，{player}！"` ✅

2. **含颜色代码前缀的文本**：`&a`、`&c`、`§6` 等后面跟着可读文字
   - `"&6[商店] &f物品已上架"` ✅
   - `"§c错误: 背包已满"` ✅

3. **含变量的动态消息**：原始变量占位后剩余部分仍有可读意义
   - `"玩家 %player% 已加入游戏"` ✅
   - `"你的余额: $%balance%"` ✅

4. **带标点的短语**：含 `！`、`？`、`。`、`!`、`?`、`,` 等

5. **Key 路径暗示为消息的字段**（见下文关键字列表）

### 常见消息 Key 关键字（高置信度）

当插件配置的 YAML key 包含以下词时，其值极可能是消息文本：

```
message, msg, text, description, info, error, success, fail, failure,
warning, warn, title, subtitle, header, footer, prefix, suffix,
broadcast, announce, notify, notification, alert,
kick, ban, mute, tempban, tempmute, jail,
join, leave, quit, death, respawn,
help, usage, command, syntax,
format, display, label, name (仅在消息上下文中),
no-permission, nopermission, denied, forbidden,
reward, grant, give, receive, earn,
buy, sell, shop, cost, price,
level, rank, promote, demote,
chat, private, whisper, tell,
cooldown, wait, delay,
invalid, unknown, not-found, missing,
already, exists, full, empty,
enabled, disabled, toggled,
saved, loaded, created, deleted, removed, added, updated,
teleport, tp, spawn, home, warp,
confirm, cancel, accept, decline, reject
```

---

## 三、哪些字符串不应该翻译 ❌

以下字符串**不是消息文本**，不应录入 Triton：

### 明确跳过

| 类型 | 示例 |
|---|---|
| 权限节点 | `"triton.reload"`, `"essentials.home"` |
| 命令名（无文字内容） | `"home"`, `"tp"`, `"/spawn"` |
| 语言/区域代码 | `"en_GB"`, `"zh_CN"`, `"pt_PT"` |
| 服务器/世界名 | `"lobby-1"`, `"world_nether"` |
| 文件路径 | `"plugins/data/file.json"` |
| URL | `"https://example.com"` |
| Material/物品类型 | `"DIAMOND_SWORD"`, `"minecraft:dirt"` |
| 数字字符串 | `"100"`, `"0.5"` |
| 布尔值 | `"true"`, `"false"` |
| 纯颜色代码 | `"&a"`, `"§6"` |
| 单一单词（无上下文） | `"default"`, `"lobby"`, `"admin"` |
| 正则表达式 | `"\\d+"`, `"^§[0-9a-f]"` |
| 数据库表名 | `"triton_players"` |
| 格式模板（非消息） | `"yyyy-MM-dd HH:mm"` |
| YAML section key 本身 | 这是 key 不是 value |
| 仅包含占位变量无文字 | `"{0}"`, `"%player%"` |
| IP 地址 | `"192.168.1.1"` |
| Enum 值 | `"SURVIVAL"`, `"CREATIVE"` |

### 模糊情况的判断规则

**规则 1 — 单词数**：少于 2 个非变量单词的字符串，跳过。
例：`"&a{player}"` → 只有一个变量，无实际文字 → ❌
例：`"&a{player} joined"` → 有实际单词 "joined" + 变量 → ✅

**规则 2 — 去掉颜色码和变量后**：若剩余内容全是特殊字符或空，跳过。
例：`"&c&l"` → 去掉颜色码后为空 → ❌
例：`"&c&lError!"` → 剩余 "Error!" → ✅

**规则 3 — 列表中的 `name` 字段**：若 `name` 出现在物品定义、GUI 按钮中，且值是可读文字，则翻译；若是 ID 则跳过。
例：`name: "Diamond Sword"` → ✅
例：`name: "diamond_sword"` → ❌

**规则 4 — prefix 字段**：`prefix: "&6[Shop] "` 形式的前缀通常是消息的一部分 → ✅

---

## 四、Key 命名规范

```
{插件名}.{yaml路径}.{字段名}
```

- 插件名小写，去掉版本号
- YAML 嵌套路径用 `.` 连接
- 空格替换为 `_`，大写转小写

**示例**

```yaml
# CMI plugin config
info:
  NoPermission: "&cYou don't have permission!"
  Ingame: "&cYou can only use this in game!"
```
→ Key：`cmi.info.no_permission`、`cmi.info.ingame`

```yaml
# EssentialsX messages.yml
essentials:
  economy:
    pay-self: "&cYou can't pay yourself."
```
→ Key：`essentials.economy.pay_self`

---

## 五、使用 triton-mcp 工具的操作流程

### 前置：获取已配置的语言列表

**必须先执行此步**，获取服务器上实际配置的语言 ID：

```
工具: list_languages
参数: 无
```

返回示例：
```json
[
  {"name": "en_GB", "displayName": "English (UK)", "isMain": true},
  {"name": "zh_CN", "displayName": "简体中文", "isMain": false}
]
```

记录所有语言 ID（如 `en_GB`、`zh_CN`），后续翻译映射必须使用这些精确 ID。

---

### 标准操作流程

#### 步骤 1：确认目标集合

```
工具: list_collections
参数: 无
```

若目标插件的集合已存在，使用已有集合名；否则第一次写入时直接指定新集合名（会自动创建）。

#### 步骤 2：分析插件配置文件

阅读插件的配置文件（通常是 `messages.yml` 或 `config.yml`），按上文规则筛选出所有应翻译的字符串，整理为：

```
key: {生成的键名}
原始文本: {配置文件中的值}
variables: {变量列表，如有}
```

#### 步骤 3：批量写入翻译（优先使用 batch_upsert_items）

```
工具: batch_upsert_items
参数:
  collection: {集合名}
  items:
    - key: "plugin.path.field1"
      translations:
        en_GB: "English text here"
        zh_CN: "中文翻译"
    - key: "plugin.path.field2"
      translations:
        en_GB: "Another English text %1"
        zh_CN: "另一条中文翻译 %1"
```

#### 步骤 4：触发重载

```
工具: reload_triton
参数: 无
```

重载后修改立即对在线玩家生效。

#### 步骤 5：修改插件配置文件（替换占位符）

将插件原始配置文件中的消息文本替换为 Triton 占位符：

```yaml
# 原始
no_permission: "&cYou don't have permission!"

# 替换后（无变量）
no_permission: "[lang]plugin.no_permission[/lang]"
```

```yaml
# 原始
kill_message: "&c{killer} has killed {victim}!"

# 替换后（有变量）
kill_message: "[lang]plugin.kill_message[args][arg]{killer}[/arg][arg]{victim}[/arg][/args][/lang]"
```

然后在游戏内执行插件自身的 reload 命令（如 `/cmi reload`），使占位符生效。

---

## 六、单条操作工具参考

### 查询

| 目的 | 工具 | 关键参数 |
|---|---|---|
| 列出所有语言 | `list_languages` | 无 |
| 列出所有集合及条目数 | `list_collections` | 无 |
| 获取集合内全部条目 | `get_collection_items` | `collection` |
| 按 key 查单条 | `get_item` | `key` |
| 搜索缺少某语言的条目 | `search_items` | `missingLanguage`, `collection` |
| 按 key 前缀搜索 | `search_items` | `keyPattern: "^plugin\\."`  |

### 写入

| 目的 | 工具 | 说明 |
|---|---|---|
| 批量创建/更新 | `batch_upsert_items` | **首选**，key 存在则合并，不存在则创建 |
| 创建单条（key 不存在） | `create_text_item` | key 已存在时报错 |
| 更新单条（key 已存在） | `update_item_translations` | 只更新指定语言，其他语言保留 |
| 删除单条 | `delete_item` | `key` |
| 触发重载 | `reload_triton` | 每次写操作后调用 |

---

## 七、完整示例：翻译一个插件的消息文件

### 目标文件（假设为 `MyPlugin/messages.yml`）

```yaml
prefix: "&6[MyPlugin] "
messages:
  no-permission: "&cYou don't have permission to do this!"
  player-only: "&cThis command can only be used by players!"
  player-not-found: "&cPlayer &e{player} &cnot found."
  balance: "&aYour balance: &e${amount}"
  paid:
    success: "&aYou paid &e${amount} &ato &e{player}&a."
    received: "&aYou received &e${amount} &afrom &e{player}&a."
  cooldown: "&cPlease wait &e{seconds}s &cbefore using this again."

# 以下不应翻译
database:
  host: "localhost"
  port: 3306
  table: "myplugin_data"
permissions:
  admin: "myplugin.admin"
  use: "myplugin.use"
sounds:
  success: "ENTITY_PLAYER_LEVELUP"
format:
  date: "yyyy-MM-dd"
```

### 筛选结果

应翻译（7条）：
- `prefix` → `myplugin.prefix`
- `messages.no-permission` → `myplugin.messages.no_permission`
- `messages.player-only` → `myplugin.messages.player_only`
- `messages.player-not-found` → `myplugin.messages.player_not_found`（含变量 `{player}`）
- `messages.balance` → `myplugin.messages.balance`（含变量 `{amount}`）
- `messages.paid.success` → `myplugin.messages.paid.success`（含变量 `{amount}`, `{player}`）
- `messages.paid.received` → `myplugin.messages.paid.received`（含变量 `{amount}`, `{player}`）
- `messages.cooldown` → `myplugin.messages.cooldown`（含变量 `{seconds}`）

不翻译：`database.*`、`permissions.*`、`sounds.*`、`format.*`（均为配置值，非消息文本）

### 调用 batch_upsert_items

```json
{
  "collection": "myplugin",
  "items": [
    {
      "key": "myplugin.prefix",
      "translations": {
        "en_GB": "&6[MyPlugin] ",
        "zh_CN": "&6[我的插件] "
      }
    },
    {
      "key": "myplugin.messages.no_permission",
      "translations": {
        "en_GB": "&cYou don't have permission to do this!",
        "zh_CN": "&c你没有权限执行此操作！"
      }
    },
    {
      "key": "myplugin.messages.player_only",
      "translations": {
        "en_GB": "&cThis command can only be used by players!",
        "zh_CN": "&c此命令只能由玩家使用！"
      }
    },
    {
      "key": "myplugin.messages.player_not_found",
      "translations": {
        "en_GB": "&cPlayer &e%1 &cnot found.",
        "zh_CN": "&c找不到玩家 &e%1&c。"
      }
    },
    {
      "key": "myplugin.messages.balance",
      "translations": {
        "en_GB": "&aYour balance: &e$%1",
        "zh_CN": "&a你的余额：&e$%1"
      }
    },
    {
      "key": "myplugin.messages.paid.success",
      "translations": {
        "en_GB": "&aYou paid &e$%1 &ato &e%2&a.",
        "zh_CN": "&a你向 &e%2 &a支付了 &e$%1&a。"
      }
    },
    {
      "key": "myplugin.messages.paid.received",
      "translations": {
        "en_GB": "&aYou received &e$%1 &afrom &e%2&a.",
        "zh_CN": "&a你从 &e%2 &a收到了 &e$%1&a。"
      }
    },
    {
      "key": "myplugin.messages.cooldown",
      "translations": {
        "en_GB": "&cPlease wait &e%1s &cbefore using this again.",
        "zh_CN": "&c请等待 &e%1 秒&c后再次使用。"
      }
    }
  ]
}
```

### 替换后的 messages.yml

```yaml
prefix: "[lang]myplugin.prefix[/lang]"
messages:
  no-permission: "[lang]myplugin.messages.no_permission[/lang]"
  player-only: "[lang]myplugin.messages.player_only[/lang]"
  player-not-found: "[lang]myplugin.messages.player_not_found[args][arg]{player}[/arg][/args][/lang]"
  balance: "[lang]myplugin.messages.balance[args][arg]{amount}[/arg][/args][/lang]"
  paid:
    success: "[lang]myplugin.messages.paid.success[args][arg]{amount}[/arg][arg]{player}[/arg][/args][/lang]"
    received: "[lang]myplugin.messages.paid.received[args][arg]{amount}[/arg][arg]{player}[/arg][/args][/lang]"
  cooldown: "[lang]myplugin.messages.cooldown[args][arg]{seconds}[/arg][/args][/lang]"

# 以下保持不变
database:
  host: "localhost"
  port: 3306
  table: "myplugin_data"
permissions:
  admin: "myplugin.admin"
  use: "myplugin.use"
sounds:
  success: "ENTITY_PLAYER_LEVELUP"
format:
  date: "yyyy-MM-dd"
```

---

## 八、特殊格式支持

Triton 翻译文本支持三种格式（写在 `translations` 的值中）：

### 1. 传统颜色代码（默认）
```
&cRed text &aGreen text
```

### 2. MiniMessage 格式（需 Paper/Fork，Triton v3.5.1+）
在文本前加 `[minimsg]`：
```
[minimsg]<red>错误：</red><gold>%1</gold>
```

### 3. JSON Chat 组件（Triton v3.1.0+）
在文本前加 `[triton_json]`：
```
[triton_json]{"text":"%1","color":"gold","bold":true}
```

---

## 九、常见问题

**Q：key 是否区分大小写？**
A：是。建议统一使用小写加下划线，避免混淆。

**Q：variables 的顺序怎么确定？**
A：按照在占位符 args 中出现的顺序，第一个 `[arg]` 对应 `%1`，第二个对应 `%2`，以此类推。翻译文本中 `%1`、`%2` 的位置可以任意调整（适应不同语言语序）。

**Q：`batch_upsert_items` 和 `create_text_item` 什么时候用哪个？**
A：始终优先用 `batch_upsert_items`。它会自动判断 key 是否存在：存在则合并（只更新指定语言），不存在则创建。`create_text_item` 仅在确定 key 不存在且只创建一条时使用，否则报错。

**Q：写入后翻译没有生效？**
A：确认已调用 `reload_triton`，并且插件配置文件中的原始消息已替换为 Triton 占位符并重载了插件。两步缺一不可。

**Q：服务器有多个 backend，只想在某个服务器上生效？**
A：在 `create_text_item` 时使用 `servers` 和 `blacklist` 字段过滤。`batch_upsert_items` 中每条 item 不支持独立 server filter，可改用 `create_text_item` 单独处理这类条目。

**Q：如何检查哪些条目还缺少某语言的翻译？**
A：
```
工具: search_items
参数:
  collection: "myplugin"
  missingLanguage: "zh_CN"
```

**Q：Velocity 上 Triton 的限制？**
A：Triton v4（当前版本）在 Velocity 上支持翻译 Velocity 插件消息，且支持 MySQL。与 BungeeCord 模式行为一致。

---

## 十、给 AI 的行动准则

处理插件翻译任务时，按以下顺序操作：

1. **调用 `list_languages`** — 获取语言 ID 列表，这是写翻译前的必要前提
2. **阅读配置文件** — 扫描所有字符串，按第二、三节规则筛选
3. **整理翻译清单** — 列出 key、原文、变量，向用户确认或直接处理
4. **提供所有语言的翻译** — 对筛选出的每条消息，补全所有语言的翻译文本（保持原有颜色代码风格）
5. **调用 `batch_upsert_items`** — 一次性写入，优先批量
6. **调用 `reload_triton`** — 使变更生效
7. **输出替换后的配置文件** — 将原始消息替换为占位符，展示给用户

**质量检查点**：
- 每条消息的变量数量与原文一致
- `%N` 编号从 1 开始连续，不跳号
- 语言 ID 与 `list_languages` 返回的完全一致
- 不翻译 section 标题、注释行、ID 字段
- 颜色代码风格与原文保持一致（`&` vs `§`）
