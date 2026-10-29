# Server Chat History

[English](README.md)

适用于 **Minecraft Java 26.3** 的 Fabric 模组。玩家进入游戏时，补发当前世界最近的公开聊天记录。**服务端安装即可，原版客户端不需要安装本模组或 Fabric**。同一个 JAR 可选安装到客户端，提供个人偏好、设置界面及 Chat Patches 历史时间联动。也支持单人世界及开放局域网的逻辑服务端。

0.2.0 使用 Java 25、Fabric Loader 0.20.1、Fabric API 0.165.0+26.3。项目名称暂定，模组 ID 为 `server_chat_history`。

## 安装

1. 使用 Java 25 和 Fabric Loader 0.20.1 创建 Fabric 26.3 服务端。
2. 将 Fabric API 0.165.0+26.3 和 `server-chat-history-0.2.0.jar` 放入 `mods` 目录。
3. 启动后自动生成 `config/server_chat_history.json`，历史文件保存在世界存档中。

单人或局域网游戏由房主在自己的 Fabric 实例安装上述模组，访客可以使用原版 26.3。本模组不会修改正版验证、安全配置或服务端 EULA。

## 记录与补发

默认每个世界保留最近 **50 条记录**，所有维度共享。记录公开玩家聊天，以及公开的进入、退出、死亡、进度公告。多行消息仍计为一条。每次成功入服后，在当前 tick 结束、原版完成连接初始化后，从旧到新发送已保留且启用的记录，包括本次进入公告（需启用且确实广播）。

不记录私聊、队伍聊天、限制接收范围的死亡消息、命令输出、`/say`、`/me`、`/tellraw` 和外部聊天桥接。采集依据通过 Fabric 拦截检查的全服广播及原版消息类型；替换这些路径或伪造原版事件组件的模组可能需要专门适配。

保留富文本、名称、颜色、翻译组件及悬浮信息；只添加整段开始、结束提示，不在每条消息前添加标记或时间。遵守过滤信息和接收者的聊天可见性设置。补发和模组提示不会再次写入历史。

当前对**所有入服玩家**发送最近记录，包括首次进入和快速重连的玩家，不追踪个人已读位置，也没有玩家查询或分页命令。

## 配置

配置属于游戏或服务端安装目录，同一安装中的单人存档共用配置，但历史互相隔离。

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
  "showConfigErrors": true
}
```

| 配置项 | 含义 |
| --- | --- |
| `historyLimit` | 正 64 位整数。不设额外固定上限，不预分配对应容量；仍受实际内存、磁盘和客户端限制。 |
| `events.join`、`events.leave`、`events.death`、`events.advancement` | 控制该类事件的采集和显示。关闭后旧记录仍占容量，在自然淘汰前重新开启可再次显示。 |
| `replayMode` | `auto` 对兼容客户端使用增强历史协议，其余使用原版系统消息；`system` 强制系统消息。协商成功的个人偏好在两种模式下均有效。 |
| `showEmptyHistory` | 没有可发送记录时显示提示。 |
| `showConfigErrors` | 配置出错时在入服补发结尾追加红色提示，与空历史提示开关独立。 |

缩小容量立即淘汰旧记录，扩大容量不能恢复已淘汰内容。缺少字段使用默认值，未知字段或无效值会被拒绝。启动时配置无效会保留文件并使用默认值；重载失败保留上一次有效配置。成功加载或命令保存有效配置后解除错误状态。

## 管理命令

控制台、OP2 及以上玩家、单人或局域网世界主人可管理。主人无需开启作弊，普通局域网访客无权操作。具备对应命令权限的命令方块也可以执行。

```text
/chathistory config
/chathistory config get historyLimit
/chathistory config set historyLimit 100
/chathistory config set events.death false
/chathistory config set replayMode system
/chathistory reload
/chathistory status
/chathistory clear
```

命令修改经过校验、保存后立即生效。`reload` 加载手动修改的文件；`status` 显示保留条数、可见条数、有效发送模式、配置和存储状态，以及待写任务数。发生过损坏恢复后，本次运行的状态会持续保留恢复警告，详情见日志。

`clear` 清空当前世界内存记录及恢复副本（包括隔离的损坏历史），取消尚未开始的旧快照写入，并等待正在进行的写入完成。仅在主文件及备份均重置后报告成功；失败会明确提示，并在下次保存或停服时重试。清空之后新到达的消息正常保留，不影响外部备份。

玩家语言为 `zh_cn` 时使用简体中文，其他语言使用英文。控制台和日志使用英文，不需要客户端语言资源包。

## 可选客户端安装

在 Fabric 26.3 客户端安装同一个 JAR 和 Fabric API。Chat Patches、Mod Menu 均为**可选依赖**，不会打包进本模组。未安装 Chat Patches 时个人偏好仍可用，不额外添加时间前缀或独立历史页面。

使用 `/chathistoryclient` 打开设置界面，也可从 Mod Menu 进入；可用命令：

```text
/chathistoryclient status
/chathistoryclient config get historyLimit
/chathistoryclient config set historyLimit 100
/chathistoryclient config set events.join false
/chathistoryclient config set receiveHistory false
/chathistoryclient config set chatPatchesIntegration false
/chathistoryclient reload
```

这些是无需服务端权限的本地命令。`config/server_chat_history-client.json` 保存全局偏好，所有服务器共用：

| 设置 | 默认及作用 |
| --- | --- |
| `receiveHistory` | `true`；关闭后，在协商成功时不接收历史及开始、结束、空历史、配置错误提示。 |
| `historyLimit` | `0` 跟随服务端；正整数限制符合条件的条数。 |
| `events.join`、`events.leave`、`events.death`、`events.advancement` | 全部 `true`；仅筛选历史，不过滤实时聊天或更改服务端存储。 |
| `chatPatchesIntegration` | `true`；更改需要**重启客户端**。 |

普通偏好**下次连接生效**，不删除、重新插入或改写现有聊天及 Chat Patches 日志。服务端先按自己的策略、接收者可见性及个人事件偏好筛选，再取最近 N 条符合条件的记录，从旧到新发送。个人偏好只能缩小服务端已保留的范围。

支持的客户端在配置阶段协商，最多等待两秒。版本不兼容、通道缺失或超时会回退服务端默认历史并显示警告，此时个人偏好（包括关闭接收）可能不生效。原版客户端无需协商。客户端 `status` 区分已保存偏好和当前连接实际采用的偏好。

## 历史时间与 Chat Patches

所有记录统一使用**服务端捕获时间**，不采用客户端声明的发送时间。增强协议独立传递时间，不经过签名玩家聊天验证，不改变安全配置，也无需设置 `enforce-secure-profile=false`。历史不是经过验证的玩家签名消息，不再发送临时玩家身份或聊天会话。

双方均安装本模组、服务端为 `auto` 且兼容联动已启用时，适配器向 Chat Patches 提供玩家聊天和事件的历史时间。时间格式、时区、悬浮/上下文显示、名称、重复合并及日志仍由 Chat Patches 的配置决定，也遵守其时间总开关和系统消息时间开关。服务端 `system`、原版客户端，或联动缺失/关闭/失败时，无法通过本适配器显示历史时间；Chat Patches 可能显示接收时间。

目标为 **Chat Patches 8.0.0+26.3-fabric**。其他版本通过必要接口与字节码检查后可尝试适配；检查通过**不代表已完成实际游戏兼容性验证**，客户端状态会标明尚未实测。不兼容或运行异常会停用适配、保留原因并在本次连接提示一次，基础历史功能仍可用，不重新补发已经显示的整批记录。

设置 `chatPatchesIntegration=false` 并重启后，本模组**不应用 Chat Patches Mixin，也不访问其内部消息元数据**。Chat Patches 自身仍按自己的设置处理普通聊天；该开关不关闭本模组独立的网络协议和个人偏好。

原版最多保留 100 条消息及 100 行渲染文本，换行和批次提示也占用额度；扩大服务端容量不会扩大客户端聊天窗口。回放仍在入服 tick 内完成，不分页，也不跨 tick 分批。

**验证状态：**自动化检查不能证明实际 Fabric 注入或游戏兼容性。原版、可选客户端、独立服、单人/LAN 和 Chat Patches 另见 [验收清单](docs/TESTING.zh-CN.md)。

## 存储

历史位于每个世界的 `data/server_chat_history/history.json`，另有一份 `.bak` 恢复快照。格式版本 2 保存到达顺序、服务端捕获时间、富文本、发送者身份及必要的过滤后组件，移除重复的原版聊天包数据。不支持旧格式历史。公开聊天以可读形式保存，请据此管理存档备份。

普通记录在有变更的服务器 tick 末尾合并为一次不可变持久化快照，未变化的快照会复用；单独的后台线程进一步合并尚未开始的普通保存任务。配置调整和清空保留显式持久化边界，正常停服等待最终快照完成，包括最后一个 tick 之后收到的记录。使用临时文件和文件系统支持的原子替换。崩溃或 I/O 故障可能丢失尚未落盘的记录，这不是事务式聊天归档系统。

回放按世界惰性缓存已解码文本组件，供不同接收者共享；历史淘汰、缩容、清空和关闭时释放失效缓存。可见性和文本过滤仍按接收者处理。回放仍在入场 tick 内发送全部已保留且启用的记录，此优化不引入分页或分 tick 回放。

主文件无效时隔离为 `.corrupt-<uuid>` 并尝试恢复备份；两者均不可用时保留损坏文件、记录错误并从空历史开始。如果连隔离都失败，将拒绝覆盖原文件，需要修复权限后重启或明确执行清空。存储失败时继续内存记录，在后续变更和停服时重试。

## 构建

```sh
./gradlew clean build
```

需要 Java 25。Gradle 9.6.1 Wrapper 会校验分发包校验和。产物为 `build/libs/server-chat-history-0.2.0.jar`，`-sources.jar` 仅供开发。测试位于 `src/test`，HTML 报告位于 `build/reports/tests/test/`。GitHub Actions 构建并上传 JAR 和测试报告，不自动发布版本。

内部按采集、不可变记录、容量管理、补发策略、原版/增强发送、可选客户端适配、配置及存储分层。验证范围见 [验证清单](docs/TESTING.zh-CN.md)。

MIT 许可证。作者：IceeAn。
