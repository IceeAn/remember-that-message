<h1 align="center">
  <img src="docs/assets/remember-that-message-superellipse.svg" alt="Remember that Message mod icon" width="128" height="128"><br />
  Remember that Message
</h1>

<p align="center">在 Minecraft 服务端记录消息，即使离线也不再错过聊天。</p>

<p align="center">
  <strong>0.3.0</strong> · Fabric · 服务端安装 · <a href="LICENSE">MIT</a>
</p>

<p align="center">
  <a href="README.md">English</a> · <strong>简体中文</strong>
</p>

<p align="center">
  <a href="#安装">安装</a> ·
  <a href="#配置">配置</a> ·
  <a href="#管理命令">命令</a> ·
  <a href="#可选客户端安装">客户端增强</a> ·
  <a href="docs/COMPATIBILITY.zh-CN.md">版本支持</a> ·
  <a href="#开发与构建">构建</a>
</p>

---

适用于 **Minecraft Java 1.20～1.20.6、1.21～1.21.11 及 26.3**。在 Fabric 服务端安装后，玩家进入游戏时会收到当前世界最近的公开聊天记录。**原版客户端不需要安装本模组或 Fabric。**

| 功能 | 使用体验 |
| --- | --- |
| 入服补发 | 从旧到新补发最近聊天及已启用的进入、退出、死亡、进度公告。 |
| 按世界保存 | 默认保留 **50 条**，容量可配置；历史随存档保存，重启后保留。 |
| 可选客户端增强 | 同一 JAR 提供个人偏好、设置界面及受支持的时间／头像联动。 |
| 多种游戏场景 | 支持独立服、单人世界及局域网房主的逻辑服务端。 |

> 首次进入的玩家也会收到历史。

## 安装

1. 将与 Minecraft 版本匹配的 Fabric API 和模组 JAR 放入服务端的 `mods` 目录。
2. 重启服务端。

配置文件位于 `config/remember_that_message.json`，历史文件保存在世界存档中。

依赖版本与模组联动范围见 [版本支持矩阵](docs/COMPATIBILITY.zh-CN.md)。

单人或局域网游戏可由房主在自己的 Fabric 实例安装上述模组。

## 记录与补发

默认每个世界保留最近 **50 条记录**。

记录公开玩家聊天，以及公开的进入、退出、死亡、进度公告，**不**记录私聊、队伍聊天、限制接收范围的死亡消息、命令输出、`/say`、`/me`、`/tellraw` 和外部聊天桥接等。

## 配置

配置文件为 `config/remember_that_message.json`，属于游戏或服务端安装目录。

同一安装中的单人存档共用配置，但消息历史记录互相隔离。

```json
{
  "historyLimit": 50,
  "events": {
    "join": true,
    "leave": true,
    "death": true,
    "advancement": true
  },
  "replayMode": "auto",
  "showEmptyHistory": true,
  "showConfigErrors": true,
  "shortCommandAliases": true
}
```

| 配置项 | 含义 |
| --- | --- |
| `historyLimit` | 保存并发送的历史记录上限，正 64 位整数。 |
| `events.join`、`events.leave`、`events.death`、`events.advancement` | 是否记录和显示加入、离开、死亡和进度消息。 |
| `replayMode` | `auto` 对已安装客户端 mod 的用户使用增强协议，其余使用原版系统消息；`system` 强制系统消息。 |
| `showEmptyHistory` | 没有可发送记录时是否显示提示。 |
| `showConfigErrors` | 入服补发时是否同时提示配置错误。 |
| `shortCommandAliases` | 是否启用服务端短命令 `/rtm`。修改后**重启服务端**生效。 |

缩小容量会立即淘汰旧记录，扩大容量不能恢复已淘汰内容。

## 管理命令

控制台、OP2 及以上玩家、单人或局域网世界主人可管理。主人无需开启作弊，普通局域网访客无权操作。具备对应命令权限的命令方块也可以执行。

以下示例使用短别名 `/rtm`，相关配置关闭或命令名称冲突时也可使用完整命令 `/rememberthatmessage`。

```text
/rtm config
/rtm config get historyLimit
/rtm config set historyLimit 100
/rtm config set events.death false
/rtm config set replayMode system
/rtm reload
/rtm status
/rtm clear
```

命令修改立即生效（`shortCommandAliases` 除外，需重启服务端）。`reload` 加载手动修改的文件；`status` 显示保留条数、可见条数、有效发送模式、配置和存储状态，以及待写任务数。发生过损坏恢复后，本次运行的状态会持续保留恢复警告，详情见日志。

`clear` 清空当前世界内存记录及恢复副本（包括隔离的损坏历史），清空之后新到达的消息正常保留。

## 可选客户端安装

在相同 Minecraft 版本的 Fabric 客户端安装对应 JAR 和 Fabric API。

### 客户端设置与命令

以下示例使用短别名 `/rtmclient`，相关配置关闭或命令名称冲突时也可使用完整命令 `/rememberthatmessageclient`。

使用 `/rtmclient` 可打开设置界面，也可从 Mod Menu 进入；可用命令：

```text
/rtmclient status
/rtmclient config get historyLimit
/rtmclient config set historyLimit 100
/rtmclient config set events.join false
/rtmclient config set receiveHistory false
/rtmclient config set messageTimeIntegration false
/rtmclient config set chatHeadsIntegration false
/rtmclient config set skipHistoryNarration false
/rtmclient reload
```

| 设置 | 作用 |
| --- | --- |
| `receiveHistory` | 是否接收服务端 mod 发送的历史消息。 |
| `historyLimit` | 限制接收历史消息条数；`0` 表示跟随服务端。 |
| `events.join`、`events.leave`、`events.death`、`events.advancement` | 控制历史消息中的加入、退出、死亡、进度是否显示。 |
| `messageTimeIntegration` | （目前支持 Chat Patches、Chat Tools）是否更改兼容的 mod 显示的“消息时间”为服务端记录的消息时间，更改需要**重启客户端**。 |
| `chatHeadsIntegration` | 是否让 Chat Heads 显示在线玩家的历史消息头像，更改需要**重启客户端**。 |
| `skipHistoryNarration` | 是否让旁白跳过历史消息。 |
| `shortCommandAliases` | 是否启用客户端短命令 `/rtmclient`，更改需要**重启客户端**。 |

`messageTimeIntegration`、`chatHeadsIntegration` 和 `skipHistoryNarration` 仅在服务端设置为 `replayMode=auto` 且与服务端协商成功时生效。

修改 `receiveHistory`、`historyLimit`、`events` 和 `skipHistoryNarration` 无需重启客户端，在下次连接服务端并接收历史时生效。安装可选客户端后，会在连接时与服务端协商发送内容。协商失败会回退服务端默认配置并显示警告。

`messageTimeIntegration`、`chatHeadsIntegration`、`shortCommandAliases` 需重启客户端生效。

## 历史消息的时间与头像显示兼容

客户端安装本模组 + Chat Patches/Chat Tools 时，默认启用的 `messageTimeIntegration` 可让这些 mod 显示的消息时间变为服务端记录的真实消息时间，而非收到历史消息的入服时间。

客户端安装本模组 + Chat Heads 时，默认启用的 `chatHeadsIntegration` 可在历史消息中显示入服时已在线的玩家的头像。离线玩家暂时无法显示头像。

Chat Patches、Chat Tools、Chat Heads 的具体联动版本及验证范围见 [支持矩阵](docs/COMPATIBILITY.zh-CN.md)。

如果与这些 mod 同时安装时出现功能异常，可尝试设置 `messageTimeIntegration=false`、`chatHeadsIntegration=false` 关闭相关功能并重启 Minecraft。

## 存储

历史位于每个世界的 `data/remember_that_message/history.json`，另有一份 `.bak` 恢复快照。

普通记录发生变化后，会在该服务端 tick 结束时提交后台保存，写入采用尽力保存方式。主文件无效时隔离为 `.corrupt-<uuid>` 并尝试恢复备份。

跨 Minecraft 版本迁移时，会尝试对记录进行转换，并对转换前版本保留最多一份固定的 `history.json.previous-version` 旧版本备份。

## 开发与构建

```sh
./gradlew clean build
```

使用 Java 25 运行 Gradle，同时安装 Java 17 和 Java 21 工具链。Gradle 9.6.1 Wrapper 会校验分发包校验和。上述命令构建全部 20 个目标；`./gradlew -PgameTargets=1.20.4,1.21.11 build` 只配置和构建所选目标及公共模块。也可使用 `:mc263:build` 等任务，但不指定 `gameTargets` 时仍会配置全部目标。

产物路径统一为 `versions/<版本>/build/libs/remember-that-message-0.3.0+mc<版本>.jar`。

`-sources.jar` 仅供开发。根项目统一构建各模块，不代表最新 Minecraft 版本。

### 项目结构

- `common/`：独立编译的 Java 17 业务、配置、持久化、协议契约及语言资源，不依赖 Minecraft 或 Fabric。可用 `./gradlew :common:test --tests '*PersistenceTest'` 单独验证持久化。
- `platform-shared/src/`：显式共用的游戏适配及集成测试，分别按各版本依赖编译；允许依赖 Minecraft／Fabric，但版本之间不再隐式继承文件。
- `platform-adapters/`：按实际 API 差异拆分的薄适配及对应测试，见 [目录说明](platform-adapters/README.zh-CN.md)。
- `versions/<版本>/target.properties`：目标依赖、Java 和适配组合；仅不可共享的实现放在本版本 `src/`。根 `build.gradle.kts` 统一构建约定。

每份可安装 JAR 只包含一份公共类／资源及自身适配，无需另装公共模组。公共层定义文件格式、转换规则及稳定协议；现代版本采用类型化载荷，1.20～1.20.4 用旧通道 API 封装相同字节。

各模块 HTML 测试报告位于各自 `build/reports/tests/test/`。GitHub Actions 安装三套工具链，按目标矩阵上传可安装 JAR 及全部测试报告，不自动发布版本。

内部按采集、不可变记录、容量管理、补发策略、原版/增强发送、可选客户端适配、配置及存储分层。验证范围见 [验证清单](docs/TESTING.zh-CN.md)。

## 许可证

[MIT](LICENSE) · 作者：IceeAn。
