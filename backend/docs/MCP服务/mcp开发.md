我想在当前文章生成项目中引入 MCP 进行工具调用，但不要破坏现有主流程。

请先不要改代码，先阅读项目并生成一份 MCP 工具调用开发文档。

目标：
1. 保留现有 ArticleAsyncService 多阶段生成流程；
2. 保留 SSE；
3. 保留配图流程；
4. 新增 toolCallingEnabled 字段；
5. 新增 ragMode 字段；
6. 新增 ToolCallingArticleAgent；
7. 新增 MCP Tool Server；
8. MCP Server 第一版只暴露 rag_search_user_knowledge 工具；
9. Article 服务作为 MCP Client 连接 MCP Server；
10. 正文阶段优先接入 MCP Tool Calling；
11. MCP 调用失败时降级普通正文生成；
12. ragMode=AUTO 可作为 MCP 失败后的后端 RAG 兜底；
13. 项目使用 MyBatis-Flex，不要使用 MyBatis-Plus；
14. 不引入真实向量数据库；
15. 不引入真实 embedding；
16. 不让 MCP 工具修改数据库主状态；
17. 不让 MCP 工具处理扣费、权限、SSE、删除等高风险操作。

请文档中说明：
1. 当前本地 @Tool 调用和 MCP 的区别；
2. 为什么本项目适合把 RAG 检索抽成 MCP Tool；
3. MCP Server 和 MCP Client 如何拆分；
4. 新增哪些文件；
5. 修改哪些文件；
6. 数据库如何新增字段；
7. ToolCallingArticleAgent 如何调用 MCP 工具；
8. RagMcpTool 如何自动选择知识库；
9. toolCallingEnabled 与 ragMode 如何配合；
10. 失败如何降级；
11. 日志如何设计；
12. 测试用例如何设计；
13. 本阶段不要修改哪些内容。

请输出 Markdown 开发文档，不要直接修改代码。