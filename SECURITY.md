# 安全策略 / Security Policy

## 支持的版本 / Supported versions

| 版本 | 状态 |
|---|---|
| 0.1.x | ✅ 接受安全修复 |

## 报告漏洞 / Reporting a vulnerability

**请不要通过公开 issue 报告安全问题。**
Please do not report security issues through public issues.

请使用 GitHub 的私密报告通道：
[Security → Report a vulnerability](https://github.com/wontlost-ltd/vaadin-agui/security/advisories/new)，
或发送邮件至 service@wontlost.com。

## 本项目的安全边界 / Threat model

这个 add-on 做的事：把浏览器发出的对话（用户输入、历史、上下文）POST 到应用自己的 SSE 端点，
把 agent 返回的事件渲染成 UI。它不做认证鉴权，不持久化数据。

值得关注的攻击面：

1. **模型输出渲染。** 回答是不可信内容，经 marked 解析后全部通过 DOMPurify 消毒再插入 DOM，
   外链强制 `rel="noopener noreferrer"`。若你替换渲染器，请保留消毒步骤。
2. **SSE 端点。** `POST <agui.path>/<bean>` 是普通 Spring MVC 路由，与应用其它接口一样
   受你配置的安全策略约束。请求携带 same-origin cookie，`setRequestHeaders` 可附加令牌。
3. **工具执行。** 后端工具在服务器上运行，参数来自模型；请像对待任何外部输入一样校验。
   前端工具由你的 Flow 代码执行，同样如此。
4. **随 jar 分发的前端依赖**（Lit、marked、DOMPurify）在下游用户的浏览器里执行，
   由 Dependabot 跟踪。
