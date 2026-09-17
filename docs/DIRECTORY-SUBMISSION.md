# Vaadin Directory 提交清单（0.1.0）

Directory 只接受 OSI 许可证的条目；本条目是 Apache 2.0 的 `agui-vaadin`，描述里链接 Pro 页面（条款允许）。
提交是网页表单，需要 vaadin.com 账号登录：https://vaadin.com/directory/ → "Add-on" → "Publish"。

| 字段 | 值 |
|---|---|
| Name | AgUiVaadin |
| Summary | AG-UI protocol chat component for Vaadin Flow: structured agent events streamed straight to the browser, no @Push required |
| Description | 粘贴 `DIRECTORY.md` 全文（含 Live demo 与 Pro 链接） |
| Version | 0.1.0 |
| Maven | `com.wontlost:agui-vaadin:0.1.0`（Maven Central，同步后可用） |
| Framework | Vaadin 25 |
| License | Apache 2.0 |
| Source | https://github.com/wontlost-ltd/vaadin-agui |
| Demo | https://agui.wontlost.com |
| Issue tracker | https://github.com/wontlost-ltd/vaadin-agui/issues |
| Screenshot | `docs/images/atlas-order-desk.png` |
| Tags | ai, chat, agent, ag-ui, streaming, spring-ai, llm |

发布件：GitHub Release v0.1.0 附带的 jar，或 Central 上的 `agui-vaadin-0.1.0.jar`。
Directory 的 Maven 集成在条目审核后自动拉取 Central 的坐标；在此之前"Download"链接指向 Release 附件。

**发布顺序**
1. `git tag v0.1.0 && git push origin v0.1.0` → `publish.yml` 校验 CI 后 `mvn -Prelease deploy` 到 Central 并创建 GitHub Release（已完成）。
2. 等 https://repo1.maven.org/maven2/com/wontlost/agui-vaadin/0.1.0/ 出现（通常 15–60 分钟）。
3. 在 Directory 表单里填上表，上传截图，提交审核。
4. 已上架：slug 为 `aguivaadin`（https://vaadin.com/directory/component/aguivaadin），README badge 与 `GetStartedDialog` 的链接已回填。
