# RPC 框架选型对比：Feign / Dubbo / gRPC

> 来源：云栈社区、CSDN、阿里云开发者社区、APIFox 等多篇文章对比总结

---

## 一、三框架核心差异

| 维度 | OpenFeign | Dubbo | gRPC |
|------|-----------|-------|------|
| 通信协议 | HTTP/1.1 | 自定义 TCP / Triple(HTTP/2) | HTTP/2 |
| 序列化 | JSON | Hessian2 / Protobuf / JSON | Protobuf |
| 跨语言 | ❌ 仅 Java | ❌ 主要 Java（Triple 支持跨语言） | ✅ 多语言 |
| 服务治理 | ❌ 依赖 Spring Cloud | ✅ 内置全套 | ❌ 无 |
| 性能 | 一般（文本协议） | 高（二进制、长连接） | 高（HTTP/2 + Protobuf） |
| 学习成本 | 极低（Spring MVC 注解） | 中 | 高（.proto + 代码生成） |
| 流式通信 | ❌ | ❌ 主要 Request-Response | ✅ 支持双向流 |

---

## 二、性能数据对比

```
Dubbo 3.x（Triple 协议）：
  单机 15万+ QPS
  延迟稳定在毫秒级

gRPC：
  单机 约 1.3万 QPS（Java 环境测试）
  跨语言场景下性能最优

OpenFeign（JSON + HTTP/1.1）：
  单机 约 8,000 QPS
  受限于 HTTP 头部开销和 JSON 序列化

数据来源：阿里云开发者社区 RPC 性能对比实测
```

---

## 三、什么场景选什么

### 选 OpenFeign
- 纯 Java + Spring Cloud 技术栈
- 并发要求不高（< 5,000 QPS）
- 正在微服务改造，想快速上手
- 已有 RESTful 规范强依赖

### 选 Dubbo
- 高并发 Java 服务（电商、金融、交易）
- 需要深度服务治理（限流、熔断、负载均衡）
- 阿里系技术栈为主

### 选 gRPC
- 多语言服务（Java + Go + Python 混用）
- 云原生架构（K8s + Istio）
- 需要流式通信（实时推送、音视频）
- 追求极致序列化性能

---

## 四、向面试官表述的选型逻辑

> "当前项目选了 Feign + JSON，因为 Spring Cloud 生态统一，开发效率高，QPS 在 5,000 以下 JSON 的序列化开销可以忽略。
>
> 如果量级再上一个台阶（10万+ QPS），或者引入多语言服务，我会迁移到 Dubbo（纯 Java）或 gRPC（跨语言）。
>
> Dubbo 的优势是内置服务治理，性能比 Feign 高 2~3 倍；gRPC 的优势是 HTTP/2 多路复用 + Protobuf 极致序列化，体积比 JSON 小 5~10 倍。"
