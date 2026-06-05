
# AI智能体助手-JChatMind

根据代码随想录卡哥的项目去做优化和扩展
开始状态
单agent
spring ai自带的工具调用
think execute循环架构
记忆系统是简单的滑动窗口，一些记忆是rag保存到pgsql向量数据库中
消息用sse向前端推送

更新1状态
更改循环设计范式使用react
将记忆系统分为短期记忆长期记忆
增加了ai coding以及mcp

更新2状态
增加了多agent协作模式
对记忆窗口太小做了修复
可以仿claude跑一个产品的全流程自动输出
使用uv代替mcp proxy来安装一些依赖




