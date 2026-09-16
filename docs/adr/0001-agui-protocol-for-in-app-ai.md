# ADR-0001：应用内 AI 集成采用 AG-UI 协议、浏览器直连 SSE、按 threadId 持有状态

**状态：** 已采纳
**日期：** 2026-09-16
**决策人：** WontLost Ltd（Ryan Pang）

## 背景

Vaadin 25.1 起内置了应用内 AI 集成（Preview，需开启 `com.vaadin.experimental.aiComponents`）：
`AIOrchestrator` 把 `MessageList`、`MessageInput` 与 `LLMProvider` 接起来，
25.2 又加了 Grid、Chart、Form 三个 controller。它已经覆盖了"三行代码接一个聊天机器人"。

我们要做一个 Directory add-on。核对 25.2.6 的文档与 Javadoc 后，官方方案有三个**结构性**选择，
功能缺失（没有停止、重新生成、工具调用不可见、思考与引用丢失）都是这三个选择的后果：

1. **数据模型是纯文本。** `LLMProvider.stream()` 返回 `Flux<String>`，结构在进入 UI 前已经丢掉。
2. **传输走 Vaadin push。** 每个 token 一次 `ui.access()`，经 Atmosphere、UIDL 与 UI 锁；
   于是一次只能一个请求、必须 `@Push`、服务端负载随 token 数线性增长。
3. **状态归属 HTTP 会话与组件实例。** orchestrator 绑定具体组件实例，provider 不可序列化，
   每个组件实例只能绑一个 orchestrator。

另外，该功能处于 Preview：25.1 到 25.2 之间监听器与 controller 钩子已发生过破坏性重命名。

约束：add-on 必须与 Vaadin 25.2、Java 21、Spring Boot 4、Jackson 3 兼容；
Apache 2.0；发布到 Maven Central；Lumo 与 Aura 两套主题都要能用。

## 决策

不做 orchestrator 的皮肤，而是把三个底层选择全部换掉：

- **协议层用 AG-UI。** 开放的、约 16 种事件的 agent↔UI 协议，事件名与规范逐字一致。
  UI 侧是对事件流做归约；结构化事件是唯一的数据模型。
- **传输层绕过 Vaadin push。** 浏览器里的 Lit 组件直接 `fetch` + SSE 消费事件，
  Markdown 在客户端增量渲染；Flow 服务端只在运行生命周期节点收 DOM 事件。
- **状态层按 threadId 归服务端存储。** agent 无状态：每次运行携带完整消息；
  需要跨运行的状态由应用按 `threadId` 持久化，不依赖 HTTP 会话与组件实例。

Java 侧不依赖社区的 AG-UI Java SDK（Maven Central 上没有该 groupId 的构件，且自述不稳定），
事件模型用 Jackson 3 record 自行实现，约 200 行。

## 备选方案

### 方案 A：官方 AIOrchestrator 之上的富 UI 层

实现 `AIMessageList`、`AIMessage`、`AIInput`、`AIFileReceiver` 四个接口，
用装饰器补停止（`takeUntilOther` 截断 provider 的 Flux）与工具可视化（包 `ToolSpec.execute`）。

| 维度 | 评估 |
|---|---|
| 复杂度 | 中：两个装饰器加四个接口实现，但重新生成与会话切换都要重建组件树 |
| 成本 | 低起步，高维护：Preview API 每个次版本都可能变 |
| 可扩展性 | 受限：思考、引用、用量在 `Flux<String>` 里拿不到，只能旁路 |
| 团队熟悉度 | 高 |

**优点：** 与官方路径完全兼容；用户已有 orchestrator 代码不用改。
**缺点：** 核心卖点会被官方下一个版本吃掉；并发、多标签页、水平扩展问题原样继承。

### 方案 B：AG-UI 协议 + 浏览器直连 SSE（采纳）

| 维度 | 评估 |
|---|---|
| 复杂度 | 中：事件模型、SSE 端点、Lit 归约器各一份，没有装饰器与重建逻辑 |
| 成本 | 中起步，低维护：只依赖协议的 JSON schema 与 Vaadin 稳定的 Element API |
| 可扩展性 | 高：工具、思考、状态同步、前端工具、人机确认都是标准事件 |
| 团队熟悉度 | 中：AG-UI 是新协议，但概念与 Vercel AI SDK 的 UI stream 相同 |

**优点：** 结构化事件；聊天不需要 `@Push`；每个 threadId 独立并发；
同一个 Web Component 在 Flow 与 Hilla/React 里行为一致；Java 生态里尚无 AG-UI 的 UI 端实现。
**缺点：** SSE 端点要与 Vaadin 会话共用认证，反向代理需关闭 SSE 缓冲；协议仍在演进。

### 方案 C：自定义 WebSocket 协议

自定义事件格式，走 WebSocket。

**优点：** 双向通信原生。
**缺点：** 又一个私有协议，任何第三方前端与 agent 运行时都无法复用；
与"标准化 + 生态复用"的最高优先级直接冲突。否决。

## 权衡分析

方案 A 的所有工作都建立在会被官方吸收的空洞上；方案 B 建立在官方没有涉足的协议层。
方案 B 多付出的是 SSE 端点与客户端归约器，换来的是并发模型、状态归属和跨前端复用，
这三样是方案 A 无论怎么打补丁都拿不到的。Spring AI 2.0 删除了 `internalToolExecutionEnabled`，
模型实现默认内部执行工具，适配器顺着它做（观察器 + `returnDirect`）反而比方案 A 的 `ToolSpec` 包装更薄。

## 后果

- **变容易：** 停止是断开 SSE；重新生成是截断后重跑；多会话是换 threadId；
  工具调用、思考、状态同步各是一种事件；不再需要 `@Push`。
- **变困难：** 需要一个 HTTP 端点而不只是一个组件；SSE 端点的认证与代理配置由应用负责；
  Spring AI 之外的框架各要一个适配器。
- **需要回头看：** AG-UI 协议版本变化；官方 `LLMProvider` 若增加结构化事件，
  可考虑补一个把 orchestrator 事件转成 AG-UI 的桥；Spring AI 前端工具与后端工具同轮混合调用的边界。

## 行动项

1. [x] 事件模型与 `RunAgentInput`（Jackson 3 record）
2. [x] `AgUiAgent` / `AgUiEmitter` SPI，与 AI 框架无关
3. [x] Spring MVC SSE 端点与 Boot 4 自动配置（bean 名即端点名）
4. [x] `SpringAiAgent`：观察器包装后端工具，前端工具走 `returnDirect`
5. [x] `<agui-chat>` Lit 组件：SSE 客户端、归约器、Markdown 渲染、工具卡片、思考块、停止、重新生成
6. [x] `AgUiChat` Flow 组件：属性同步、七个生命周期事件、服务端驱动动作
7. [x] 示例应用生产构建 + Playwright E2E（Chromium 与 Firefox，28 例）
8. [x] Aura 主题下的视觉核对（截图核对，变量回退链生效）
9. [ ] 发布 0.1.0 到 Maven Central 与 Vaadin Directory
