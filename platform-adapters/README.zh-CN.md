# 平台适配源码组合

此目录按游戏或第三方 API 的实际差异组织源码，不代表主版本继承树。
每个 `versions/<版本>/target.properties` 明确选择 `adapters`，根构建脚本将这些目录与 `platform-shared/src` 共同编译。每份产物只包含选中的实现；不使用同路径覆盖或逐消息版本判断。

- `network-*`：Fabric 网络注册、握手与原生包封装。
- `text-*`、`hover-event-*`：原生文本编解码及对应测试夹具。
- `language-*`、`profile-*`、`runtime-version-*`：语言、玩家档案及实际版本接口。
- `client-platform-*`、`client-screen-*`、`chat-visibility-*`、`narrator-*`：显示、设置界面与接收许可。
- `chat-integrations-*`、`chatpatches-queue-*`、`chatheads-*`：可选模组结构契约及具体队列／装饰入口。
- `click-filter-*`、`click-screen-*`：点击命令与原版过滤规则一致的保护。
- `commands-*`、`client-commands-*`、`identifier-*`：权限、客户端命令来源及资源标识符接口。
- `metadata-*`、`mixin-config-*`：产物元数据及对应注入列表。

增加目标时先验证现有组合；只有具体接口不兼容时才新增适配。类名相同的实现只能选择一个；测试跟随其验证的接口放在同一适配组。不要为了减少目录数合并不相关的版本分支。共享编解码源码也必须保持原生编码的版本独立身份。

使用 `./gradlew -PgameTargets=1.20,1.21 build` 可只配置所选目标及公共模块。未指定时构建所有已登记目标。编译通过不等于正式 JAR 的 Mixin 和游戏内行为已验证。

26.1～26.3 按实际 API 复用现代网络、文本、客户端和联动适配；元数据按目标展开。每个目标仍保留独立的原生正文编码身份。
