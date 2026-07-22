# 升级迁移

> 旧版升级报告详见：
> - `md/docs/7-杂七杂八/升级报告-JDK25-Boot4.1.md` — JDK 25 + Spring Boot 4.1 升级
> - `md/docs/v0.2-实现对照/` — 设计 vs 实现差异追踪

## 已知问题

详见 `AGENTS.md`:
- JJWT + Gson `Double→Integer` 类型转换（3 处受影响）
- Gateway 路由配置路径变更
- Jackson 包名 `com.fasterxml.jackson` → `tools.jackson`
