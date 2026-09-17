# 贡献指南 / Contributing

欢迎 issue 与 PR。以下是让改动更容易被合并的一些说明。

## 构建与测试

```bash
# 库本体（JUnit：事件模型、SSE 端点、Spring AI 适配器）
mvn clean verify

# 前端类型检查
cd src/main/resources/META-INF/frontend/agui-vaadin && npm ci && npm run typecheck

# 端到端测试（Chromium + Firefox）
mvn install -DskipTests
# The Atlas Order Desk showcase and its Playwright suite live in the private agui-examples repository
```

需要 Java 21、Node 24（仓库根目录有 `.nvmrc`）。示例应用不需要任何 API key。

## 关于测试的一点说明

E2E 的原则：**验证过程确实发生了，而不只是终态正确。**

一个把整段回答一次性塞进来的实现终态也是对的，而那正是流式组件要防止的失败。
所以测试会采样流式中途的文本长度、断言工具卡片在运行中就出现、确认服务端只在结束时
收到一次事件。新增测试时请沿用这个思路。

单元测试里的 `ScriptedChatModel` 复刻了 Spring AI 模型实现的内部工具循环。改动
`SpringAiAgent` 时请先确认它与你所针对的 Spring AI 版本行为一致。

## 代码风格

- 沿用现有风格。Java 侧四空格缩进，TypeScript 侧遵循仓库的 tsconfig。
- 注释写「为什么」，不写「做了什么」。
- 协议字段名与 AG-UI 规范逐字一致，不要为了 Java 习惯改名。
- 若某处实现绕开了显而易见的写法，请在注释里说明原因。

## 提交 PR 前

- [ ] `mvn clean verify` 通过
- [ ] 前端 `npm run typecheck` 通过
- [ ] 若改动影响运行时行为，E2E 也通过
- [ ] 新增或修改的公开 API 有 Javadoc
- [ ] 若改动架构层面的选择，补一条 ADR 到 `docs/adr/`

## 提交信息

用 [Conventional Commits](https://www.conventionalcommits.org/)：
`feat:` / `fix:` / `docs:` / `chore:` / `refactor:` / `test:`

## 报告安全问题

请勿通过公开 issue 提交，见 [SECURITY.md](SECURITY.md)。
