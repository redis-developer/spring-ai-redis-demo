# Spring AI Redis Demo 技术框架解读报告

## 原始数据
https://gh.llkk.cc/https://github.com/megadotnet/spring-ai-redis-demo/raw/refs/heads/main/src/main/resources/data/beers.json.gz

## 1. 框架全景图

这是一个基于 Spring Boot 4.2.0 与 Spring AI 2.1.0 的检索增强生成（RAG）演示应用，结合了 Milvus Vector Database 和大语言模型（LLM）来实现基于知识库的问答系统。该应用使用前后端分离架构，前端采用 React 实现用户界面，后端基于 Spring AI 框架集成 Milvus 和 LLM 服务。

### 项目结构

```
spring-ai-redis-demo/
├── frontend/                 # React 前端应用
├── src/
│   └── main/
│       ├── java/             # 后端 Java 代码
│       └── resources/        # 配置文件和资源
├── Dockerfile                # Docker 构建文件
├── docker-compose.yml        # 多容器编排配置
└── pom.xml                   # Maven 项目配置
```

## 2. 架构设计文档

### 整体架构

该应用采用典型的三层架构模式：
1. 表示层（Presentation Layer）：React 前端应用
2. 业务逻辑层（Business Logic Layer）：Spring Boot 后端服务
3. 数据访问层（Data Access Layer）：Milvus Vector Store（向量检索）+ Redis（BM25 索引持久化）

### 核心组件

1. **前端组件**：
   - React 应用提供用户界面
   - ChatWindow 组件实现聊天窗口
   - ChatBubble 组件显示聊天消息
   - API 模块处理 HTTP 请求

2. **后端组件**：
   - RagApplication：Spring Boot 主应用类
   - RagController：REST API 控制器
   - RagService：RAG 核心业务逻辑
   - RagConfiguration：Spring 配置类
   - RagDataLoader：数据初始化加载器

3. **数据存储**：
   - Milvus Vector Store：存储向量嵌入和元数据（由 `docker-compose.yml` 的 `standalone` 容器提供）

### 设计模式应用

1. **控制反转（IoC）**：Spring 框架管理组件生命周期
2. **依赖注入（DI）**：组件间依赖通过构造函数注入
3. **单一职责原则**：每个类都有明确的职责
4. **配置分离**：应用配置与代码分离

## 3. 核心机制解析

### RAG 工作流程

1. **数据预处理**：
   - 应用启动时，RagDataLoader 加载啤酒数据
   - 使用 OllamaEmbeddingModel（Ollama `bge-m3:latest`，1024 维）生成向量嵌入
   - 将向量数据存储到 Milvus Vector Store

2. **查询处理**：
   - 用户输入问题发送到 RagController
   - RagService 使用相似性搜索从 Milvus 获取相关文档
   - 构造包含检索文档的提示模板
   - 调用 LLM 生成基于检索文档的回答

3. **响应返回**：
   - LLM 生成的结果通过控制器返回给前端
   - 前端显示回答给用户

### 关键技术点

1. **向量搜索**：
   - 使用 Milvus Vector Database 进行相似性搜索
   - 支持 Top-K 查询返回最相关的文档

2. **混合检索**：
   - BM25 关键词检索：基于 Apache Lucene 的内存索引
   - 向量语义检索：基于 Milvus 的嵌入向量相似度
   - RRF (Reciprocal Rank Fusion) 算法融合两路结果

3. **BM25 索引持久化**：
   - 使用 Redis Cloud 存储文档数据
   - 应用重启后自动从 Redis 恢复 BM25 索引
   - OOM 错误时自动降级为内存模式

4. **嵌入生成**：
   - 使用 OllamaEmbeddingModel 生成文本嵌入
   - 基于 bge-m3:latest 模型（1024 维）

5. **提示工程**：
   - 使用系统提示模板指导 LLM 回答
   - 将检索到的文档作为上下文提供给 LLM

## 4. 源码分析要点

### 后端核心实现

1. **RagConfiguration**：
   - 配置 OllamaEmbeddingModel 用于生成嵌入
   - 配置 MilvusVectorStore 用于向量存储和检索
   - 配置 RagService 并注入依赖

2. **RagService**：
   - 实现核心 RAG 逻辑
   - 支持纯向量检索和混合检索两种模式
   - 构造提示并调用 LLM 生成回答

3. **HybridSearchService**：
   - 实现混合检索融合逻辑
   - 并行执行 BM25 和向量检索
   - 使用 RRF 算法融合结果

4. **BM25SearchService**：
   - 基于 Lucene 的 BM25 全文检索
   - 支持 Redis 持久化和索引恢复
   - OOM 错误时自动降级

5. **RagController**：
   - 提供 REST API 接口
   - 处理聊天会话和消息传递

4. **RagDataLoader**：
   - 实现 ApplicationRunner 接口，在应用启动时执行
   - 加载并处理啤酒数据，生成向量嵌入并存储到 Milvus

### 前端核心实现

1. **ChatWindow 组件**：
   - 管理聊天状态和用户交互
   - 处理消息发送和接收

2. **API 模块**：
   - 封装与后端的 HTTP 通信
   - 提供 StartChat 和 SendMessage 方法

## 5. 应用场景指南

### 典型使用案例

该应用演示了一个典型的 RAG 系统实现，特别适用于：
1. 基于特定领域知识库的问答系统
2. 产品目录查询系统
3. 客服机器人应用

### 最佳实践

1. **向量存储优化**：
   - 使用 Milvus Vector Database 提供高效的向量检索
   - 预先生成并缓存向量嵌入以提高性能

2. **提示工程**：
   - 使用模板化系统提示确保回答质量
   - 将检索到的上下文明确提供给 LLM

3. **错误处理**：
   - 实现重试机制处理服务不可用情况
   - 提供友好的用户反馈

### 性能考虑

1. **嵌入生成**：
   - 使用本地 Ollama 嵌入模型（bge-m3:latest）避免网络延迟
   - 在应用启动时预处理数据

2. **向量检索**：
   - 利用 Milvus 的高性能向量搜索能力
   - 通过 Top-K 参数控制检索结果数量

## 6. 生态系统报告

### 技术栈

1. **后端技术**：
   - Spring Boot 4.2.0
   - Spring AI 2.1.0
   - Apache Lucene 9.11.1 (BM25 全文检索)
   - Spring Data Redis (索引持久化)
   - Milvus SDK 2.6.21 (随 spring-ai-starter-vector-store-milvus 2.1.0 传递引入)
   - Java 17（Spring Boot 4.2 最低要求；容器运行镜像为 Eclipse Temurin 21）

2. **前端技术**：
   - React 18.2.0
   - React DOM 18.2.0
   - Bootstrap 5.3.2
   - React Bootstrap 2.9.2

3. **构建与部署**：
   - Maven 3.6.3+ 构建工具（Spring Boot 4.2 最低要求）
   - Node.js v18.16.0 + npm 9.5.1（`pom.xml` 属性声明的前端构建环境版本）
   - JaCoCo 0.8.16 测试覆盖率报告
   - Testcontainers 1.21.4（Milvus 集成测试容器；Boot 4.2.0 BOM 不再统一管理该版本，已在 `pom.xml` 显式钉版）
   - Docker 容器化
   - Docker Compose 编排（`docker-compose.yml` 已内置 Milvus 单机版三件套：etcd `v3.5.5` + MinIO `RELEASE.2023-03-20T20-16-18Z` + standalone `milvusdb/milvus:v2.6.7`，与 Redis 应用服务同网络编排，部署细节见 [DOCKER-README.md](DOCKER-README.md)）

### 扩展能力

1. **LLM 集成**：
   - 支持多种 LLM 提供商（当前配置为 DeepSeek）
   - 可轻松切换到其他模型如 OpenAI、Azure OpenAI 等

2. **向量数据库集成**：
   - 更多向量数据库支持
   - 更丰富的查询功能
   - 更好的性能优化

3. **生产化特性**：
   - 更完善的监控和可观测性
   - 更好的错误处理和恢复机制
   - 更强的安全性支持

## 7. 版本演进预测

### 当前状态

该项目当前基于 Spring AI 2.1.0（Spring Boot 4.2.0），由 Spring Boot 3.5.15 / Spring AI 1.1.8 迁移而来，迁移过程与验证记录见 [MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md](src/docs/MIGRATION-BOOT-4.2.0-SPRING-AI-2.1.0-RECORD.md)。Spring AI 框架仍在快速发展中，后续版本可能会有较大变化。

### 发展趋势

1. **Spring AI 成熟度提升**：
   - API 稳定性增强
   - 更多模型提供商支持
   - 更好的性能优化

2. **向量数据库集成**：
   - 更多向量数据库支持
   - 更丰富的查询功能
   - 更好的性能优化

3. **生产化特性**：
   - 更完善的监控和可观测性
   - 更好的错误处理和恢复机制
   - 更强的安全性支持

## 8. 技术选型对比表

| 技术点 | 选型 | 替代方案 | 选型理由 |
|--------|------|----------|----------|
| 后端框架 | Spring Boot 4.2.0 + Spring AI 2.1.0 | FastAPI + LangChain | Spring 生态丰富，企业级支持好 |
| 向量数据库 | Milvus v2.6.7 单机版（Compose：etcd + MinIO + standalone） | Pinecone, Weaviate, Chroma | 本地部署简单，性能优秀 |
| 全文检索 | Apache Lucene (BM25) | Elasticsearch, OpenSearch | 轻量级内存索引，无外部依赖 |
| 索引持久化 | Redis Cloud | MongoDB, PostgreSQL | 高性能 KV 存储，云服务稳定 |
| 嵌入模型 | Ollama (bge-m3:latest，1024 维) | OpenAI Embeddings | 本地运行，无需 API 调用 |
| 前端框架 | React | Vue, Angular | 生态丰富，组件化架构 |
| LLM | DeepSeek (通过 SiliconFlow) | OpenAI, Claude, Llama | 成本较低，中文支持好 |
| 部署方式 | Docker Compose | Kubernetes | 简单易用，适合演示 |

## 总结

该 Spring AI Milvus Demo 项目展示了如何使用现代技术栈构建一个完整的检索增强生成（RAG）应用。通过结合 Milvus Vector Database 的高效向量检索能力、Lucene BM25 的关键词检索能力、Redis Cloud 的持久化存储以及大语言模型的理解能力，实现了基于知识库的智能问答系统。

项目架构清晰，代码质量良好，遵循了现代软件开发的最佳实践。混合检索功能显著提升了检索准确率，Redis 持久化解决了应用重启后索引重建的问题。对于希望了解和实践 RAG 技术的开发者来说，这是一个很好的学习和参考示例。