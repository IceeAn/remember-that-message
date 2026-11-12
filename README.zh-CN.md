# Remember that Message

[English](README.md)

适用于 **Minecraft Java 1.20～1.20.6、1.21～1.21.11 及 26.3** 的 Fabric 模组。玩家进入游戏时，补发当前世界最近的公开聊天记录。**服务端安装即可，原版客户端不需要安装本模组或 Fabric**。同一个 JAR 可选安装到客户端，提供个人偏好、设置界面及 Chat Patches、Chat Tools 历史时间联动。也支持单人世界及开放局域网的逻辑服务端。

0.3.0 按 Minecraft 版本分别提供 JAR，使用 Fabric Loader 0.20.1。模组 ID 为 `remember_that_message`。

## 安装

1. 使用对应的 Java 版本和 Fabric Loader 0.20.1 创建 Fabric 服务端。
2. 将下表中对应版本的 Fabric API 和模组 JAR 放入 `mods` 目录。
3. 启动后自动生成 `config/remember_that_message.json`，历史文件保存在世界存档中。

| Minecraft | Java 运行版本 | Fabric API | 模组 JAR |
| --- | --- | --- | --- |
| 26.3 | 25 | 0.165.0+26.3 | `remember-that-message-0.3.0+mc26.3.jar` |
| 1.21.1 | 21 | 0.116.17+1.21.1 | `remember-that-message-0.3.0+mc1.21.1.jar` |
| 1.20.1 | 17 | 0.92.12+1.20.1 | `remember-that-message-0.3.0+mc1.20.1.jar` |

完整依赖与联动范围见 [20 个版本的支持矩阵](docs/COMPATIBILITY.zh-CN.md)，上表为三个示例。

仅安装对应版本的 JAR。所有目标支持历史文件自动迁移和 Play 阶段的跨版本增强协议。跨版本连接仍需代理／协议转换层正常转发 `remember_that_message:bridge_v2` 通道及注册信息；本模组不负责转换游戏协议。Velocity＋Via 的真实联机与切服仍待验收，单元测试和无窗口检查不能代替这项验证。

单人或局域网游戏由房主在自己的 Fabric 实例安装上述模组，访客可以使用相同 Minecraft 版本的原版客户端。本模组不会修改正版验证、安全配置或服务端 EULA。

## 记录与补发

默认每个世界保留最近 **50 条记录**，所有维度共享。记录公开玩家聊天，以及公开的进入、退出、死亡、进度公告。多行消息仍计为一条。每次入服的首个 tick 结束后冻结候选历史，包括本次进入公告（需启用且确实广播）。Play 协商完成或约两秒超时后，从旧到新发送一次；等待期间的新聊天不会重复补发。超时不阻塞进入世界，原版客户端回退为系统消息。

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
/rememberthatmessage config
/rememberthatmessage config get historyLimit
/rememberthatmessage config set historyLimit 100
/rememberthatmessage config set events.death false
/rememberthatmessage config set replayMode system
/rememberthatmessage reload
/rememberthatmessage status
/rememberthatmessage clear
```

命令修改经过校验、保存后立即生效。`reload` 加载手动修改的文件；`status` 显示保留条数、可见条数、有效发送模式、配置和存储状态，以及待写任务数。发生过损坏恢复后，本次运行的状态会持续保留恢复警告，详情见日志。

`clear` 清空当前世界内存记录及恢复副本（包括隔离的损坏历史），取消尚未开始的旧快照写入，并等待正在进行的写入完成。仅在主文件及备份均重置后报告成功；失败会明确提示，并在下次保存或停服时重试。清空之后新到达的消息正常保留，不影响外部备份。

玩家语言为 `zh_cn` 时使用简体中文，其他语言使用英文。控制台和日志使用英文，不需要客户端语言资源包。

## 可选客户端安装

在相同 Minecraft 版本的 Fabric 客户端安装对应 JAR 和 Fabric API。Chat Patches、Chat Tools、Chat Heads、Mod Menu 均为**可选依赖**，不会打包进本模组。未安装已支持的时间显示模组时个人偏好仍可用，不额外添加时间前缀或独立历史页面。

使用 `/rememberthatmessageclient` 打开设置界面，也可从 Mod Menu 进入；可用命令：

```text
/rememberthatmessageclient status
/rememberthatmessageclient config get historyLimit
/rememberthatmessageclient config set historyLimit 100
/rememberthatmessageclient config set events.join false
/rememberthatmessageclient config set receiveHistory false
/rememberthatmessageclient config set messageTimeIntegration false
/rememberthatmessageclient config set chatHeadsIntegration false
/rememberthatmessageclient config set skipHistoryNarration false
/rememberthatmessageclient reload
```

这些是无需服务端权限的本地命令。1.20／1.21 系列会阻止聊天文本通过 `RUN_COMMAND` 点击事件执行 `/rememberthatmessageclient`；请手动输入，或使用需要自己确认提交的命令建议。`config/remember_that_message-client.json` 保存全局偏好，所有服务器共用：

| 设置 | 默认及作用 |
| --- | --- |
| `receiveHistory` | `true`；关闭后，在协商成功时不接收历史及开始、结束、空历史、配置错误提示。 |
| `historyLimit` | `0` 跟随服务端；正整数限制符合条件的条数。 |
| `events.join`、`events.leave`、`events.death`、`events.advancement` | 全部 `true`；仅筛选历史，不过滤实时聊天或更改服务端存储。 |
| `messageTimeIntegration` | `true`；启用已支持的消息时间显示兼容（Chat Patches、Chat Tools），更改需要**重启客户端**。 |
| `chatHeadsIntegration` | `true`；让 Chat Heads 使用现有玩家资料处理玩家历史头像，独立于时间兼容；更改需要**重启客户端**。 |
| `skipHistoryNarration` | `true`；“旁白跳过历史”，跳过增强历史正文，不开启或关闭 Minecraft 自带旁白。 |

接收偏好**下次连接生效**，不删除、重新插入或改写现有聊天及 Chat Patches 日志。服务端先按自己的策略、接收者可见性及个人事件偏好筛选，再取最近 N 条符合条件的记录，从旧到新发送。个人偏好只能缩小服务端已保留的范围。

支持的客户端在 Play 阶段协商，最多等待约两秒；旧同版协议保留配置阶段兼容。版本不兼容、通道缺失或超时会回退服务端默认历史并显示警告，此时个人偏好（包括关闭接收）可能不生效。原版客户端无需协商。客户端 `status` 区分已保存偏好和当前连接实际采用的偏好。

“旁白跳过历史”保存或重载后作用于后续收到的增强历史正文。关闭跳过时，成功插入的正文交给原版系统聊天旁白，仍遵守 Minecraft 的旁白开关；不会自动开启旁白、补读已显示历史或清除已经排队的语音。大量历史可能排队并延迟新消息播报，因此默认跳过。实时聊天、起止提示和系统消息回退仍按原版处理，后者无法可靠区分历史。设置页位于 `/rememberthatmessageclient` 的“旁白”标签。

增强回放在客户端额外遵守账号/启动器的玩家聊天接收限制：禁止玩家聊天时不显示或朗读玩家历史；事件按系统消息接收能力处理，发送者屏蔽及 Minecraft 支持的仅好友限制继续生效。1.20／1.21 系列遵循其原版 `ChatStatus` 规则，包括本地世界例外。

## 历史时间兼容

所有记录统一使用**服务端捕获时间**，不采用客户端声明的发送时间。增强协议独立传递时间，不经过签名玩家聊天验证，不改变安全配置，也无需设置 `enforce-secure-profile=false`。历史不是经过验证的玩家签名消息，不再发送临时玩家身份或聊天会话。

双方均安装本模组、服务端为 `auto` 且兼容联动已启用时，适配器向 Chat Patches 提供玩家聊天和事件的历史时间。时间格式、时区、悬浮/上下文显示、名称、重复合并及日志仍由 Chat Patches 的配置决定，也遵守其时间总开关和系统消息时间开关。服务端 `system`、原版客户端，或联动缺失/关闭/失败时，无法通过本适配器显示历史时间；Chat Patches 可能显示接收时间。

Chat Patches 的具体版本见 [支持矩阵](docs/COMPATIBILITY.zh-CN.md)。**1.20.2～1.20.6、1.21.2～1.21.10 暂不提供 Chat Patches 联动，也不承诺与它同装的时间／头像联动**。其他版本通过必要接口与字节码检查后可尝试适配；检查通过**不代表已完成实际游戏兼容性验证**，客户端状态会标明尚未实测。不兼容或运行异常会停用适配、保留原因并在本次连接提示一次，基础历史功能仍可用，不重新补发已经显示的整批记录。

设置 `messageTimeIntegration=false` 并重启后，本模组**不应用 Chat Patches、Chat Tools 时间 Mixin，也不向它们提供历史时间元数据**。这些模组仍按自己的设置处理普通聊天；该开关不关闭本模组独立的网络协议和个人偏好。

### Chat Tools

新增目标为 **Chat Tools 2.4.5（支持矩阵中对应的 Fabric 发布包）**，沿用 `messageTimeIntegration` 开关。增强路径中，玩家聊天和事件的时间前缀、悬浮日期/时间以及复制功能所用的消息时间元数据使用服务端捕获时间。时间开关、格式及本地时区仍遵从 Chat Tools；本模组不会自行再加一层前缀，也不改变它的其他 HUD 功能。系统回退路径无法通过此适配提供历史时间。

同装 Chat Patches 时，可选队列钩子会将历史时间保留到延迟插入时，独立于头像开关。两个时间模组均开启前缀时可能同时显示，请在它们各自设置中选择所需显示方式。`/rememberthatmessageclient status` 分别报告适配状态；Chat Tools 缺失或不兼容时基础历史仍可用。关闭时间兼容并重启后，不应用它的时间及时间队列钩子；独立的头像队列钩子仍可能启用。

自动测试及正式 JAR 无窗口检查已验证时间作用域、实际时间方法变换，以及与 Chat Patches、Chat Heads 同装的类加载。游戏内渲染、复制菜单、重复合并及真实联机兼容性仍须另行验收。

## Chat Heads 头像兼容

设置界面的“兼容”页提供独立的时间与 Chat Heads 开关。针对 **支持矩阵中的 Chat Heads 发布包**，增强玩家历史通过发送者 UUID 查询当前连接的玩家资料，交给它原有的头像识别逻辑；检测、头像位置及渲染设置仍由 Chat Heads 决定。本适配不为事件主动添加玩家头像。查不到玩家资料时沿用 Chat Heads 自己的识别，不新增离线资料缓存、皮肤请求或虚假玩家列表条目，不保证离线头像或发言当时的皮肤。

同时安装 **支持矩阵中可用的 Chat Patches** 时，可选队列钩子在其加载旧日志期间保留每条历史的头像元数据，出队时恢复，避免重复装饰。这个头像专用的 Chat Patches 钩子独立于 `messageTimeIntegration`，单独关闭时间兼容不会关闭它。设置 `chatHeadsIntegration=false` 并重启，才会关闭本模组的头像适配及队列钩子；Chat Heads 自身对普通消息的处理仍保留。缺失或不支持的适配不会禁用基础历史；状态显示具体原因，运行错误会停用本次连接的头像适配。

完整离线头像支持保留为后续事项。自动测试与无窗口类变换检查不能替代游戏内的头像位置、重复合并和旧日志加载验收。

原版最多保留 100 条消息及 100 行渲染文本，换行和批次提示也占用额度；扩大服务端容量不会扩大客户端聊天窗口。回放仍在入服 tick 内完成，不分页，也不跨 tick 分批。

**验证状态：**单元测试不能证明实际 Fabric 注入；无窗口变换检查不能证明游戏兼容性。原版、可选客户端、独立服、单人/LAN、Chat Patches 和 Chat Tools 另见 [验收清单](docs/TESTING.zh-CN.md)。

## 存储

历史位于每个世界的 `data/remember_that_message/history.json`，另有一份 `.bak` 恢复快照。格式版本 3 在文件头单处保存 `minecraftVersion` 与 `componentEncoding`，每条仍只保存原生富文本、到达顺序、捕获时间、发送者身份及必要的过滤后正文。同编码加载不转换；跨编码才迁移。已知的无标记格式 2 可自动升级，无法识别的旧结构或未来格式保留原件并停止保存，日志及 `/rememberthatmessage status` 说明原因。公开聊天以可读形式保存，请据此管理存档备份。

迁移先转换并验证全部记录，再写入新文件；保留最多一份固定的 `history.json.previous-version` 旧版本备份，正常 `.bak` 更新为新格式。普通文本、翻译、颜色、常见点击和文本悬浮尽量保留；物品／实体悬浮和无法映射的内容允许降级为可读文本或明确占位，损失条数及类别写入日志和状态命令。过滤后的正文单独转换，绝不用未过滤正文替代。迁移失败不会自动覆盖原件；如需回滚，停服后恢复 `.previous-version` 并使用匹配版本，避免运行中编辑。`clear` 会删除这份迁移备份。

跨版本增强回放按需生成可移植 JSON 并缓存，原文与过滤后正文分别缓存，多名玩家共享；容量淘汰、清空和停服释放。可移植正文不逐条双份落盘。单条协议正文上限为 256 KiB UTF-8，转换嵌套上限 64；超限拒绝，不截断后冒充完整历史。客户端对简化内容显示一次提示，时间、UUID 和接收限制保持独立。配置阶段协议保留为 1.20.5 及以后目标的同版兼容路径，跨版使用双方的新 Play 协议。

新 Play 回放中，单条转换或编码失败会改为“[聊天历史] 此条记录无法显示”的短占位，保留顺序和时间并继续后续记录；原历史文件不变。客户端正文解析失败也使用占位。占位仍遵守原消息接收限制，但不作为玩家发言提供给时间／头像联动。收到结束边界时，客户端核对条数并汇总不可显示记录；回放不完整时保留已经显示的内容、提示一次，不自动重试。此完整性提示需要更新后的客户端；旧客户端可显示占位的英文备用文本。真正的网络发送失败仍中止批次。

普通记录在有变更的服务器 tick 末尾合并为一次不可变持久化快照，未变化的快照会复用；单独的后台线程进一步合并尚未开始的普通保存任务。配置调整和清空保留显式持久化边界，正常停服等待最终快照完成，包括最后一个 tick 之后收到的记录。使用临时文件和文件系统支持的原子替换。崩溃或 I/O 故障可能丢失尚未落盘的记录，这不是事务式聊天归档系统。

回放按世界惰性缓存已解码文本组件，供不同接收者共享；历史淘汰、缩容、清空和关闭时释放失效缓存。可见性和文本过滤仍按接收者处理。回放仍在协商完成或超时后的单个 tick 内发送全部已保留且启用的记录，此优化不引入分页或分 tick 回放。

主文件无效时隔离为 `.corrupt-<uuid>` 并尝试恢复备份；两者均不可用时保留损坏文件、记录错误并从空历史开始。如果连隔离都失败，将拒绝覆盖原文件，需要修复权限后重启或明确执行清空。存储失败时继续内存记录，在后续变更和停服时重试。

## 构建

```sh
./gradlew clean build
```

使用 Java 25 运行 Gradle，同时安装 Java 17 和 Java 21 工具链。Gradle 9.6.1 Wrapper 会校验分发包校验和。上述命令构建全部 20 个目标；`./gradlew -PgameTargets=1.20.4,1.21.11 build` 只配置和构建所选目标及公共模块。也可使用 `:mc263:build` 等任务，但不指定 `gameTargets` 时仍会配置全部目标。

产物路径统一为 `versions/<版本>/build/libs/remember-that-message-0.3.0+mc<版本>.jar`。

`-sources.jar` 仅供开发。根项目统一构建各模块，不代表最新 Minecraft 版本。

- `common/`：独立编译的 Java 17 业务、配置、持久化、协议契约及语言资源，不依赖 Minecraft 或 Fabric。可用 `./gradlew :common:test --tests '*PersistenceTest'` 单独验证持久化。
- `platform-shared/src/`：显式共用的游戏适配及集成测试，分别按各版本依赖编译；允许依赖 Minecraft／Fabric，但版本之间不再隐式继承文件。
- `platform-adapters/`：按实际 API 差异拆分的薄适配及对应测试，见 [目录说明](platform-adapters/README.zh-CN.md)。
- `versions/<版本>/target.properties`：目标依赖、Java 和适配组合；仅不可共享的实现放在本版本 `src/`。根 `build.gradle.kts` 统一构建约定。

每份可安装 JAR 只包含一份公共类／资源及自身适配，无需另装公共模组。公共层定义文件格式、转换规则及稳定协议；现代版本采用类型化载荷，1.20～1.20.4 用旧通道 API 封装相同字节。版本差异保持在各适配目录。

各模块 HTML 测试报告位于各自 `build/reports/tests/test/`。GitHub Actions 安装三套工具链，按目标矩阵上传可安装 JAR 及全部测试报告，不自动发布版本。

内部按采集、不可变记录、容量管理、补发策略、原版/增强发送、可选客户端适配、配置及存储分层。验证范围见 [验证清单](docs/TESTING.zh-CN.md)。

MIT 许可证。作者：IceeAn。

## 短命令别名

服务端主命令 `/rememberthatmessage` 的短别名为 `/rtm`；客户端主命令 `/rememberthatmessageclient` 的短别名为 `/rtmclient`，子命令及权限一致。两端配置各自提供 `shortCommandAliases`（默认 `true`）。修改后需重启对应服务端／Minecraft，配置重载不动态修改已注册命令。客户端常规设置页也可修改该开关。

短名注册时已被占用则跳过并记录警告；其他模组之后注册同名命令仍可能冲突，可关闭短别名并使用完整名称。配置读取失败时不启用短别名；完整名称始终可用。
