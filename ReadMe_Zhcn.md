# Spring AI Redis Demo 技术架构与使用指南

[English](README.md) | 简体中文
[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/megadotnet/spring-ai-redis-demo)

本项目是一个基于 Spring Boot 4.2.0 和 Spring AI 2.1.0 的检索增强生成（RAG）演示应用，结合了 Milvus 向量数据库和大型语言模型（LLM），实现面向领域知识文档的智能问答。

---

## 🛠️ 技术栈清单

以下梳理了本项目各核心模块采用的具体技术选型、版本及核心作用。

### 1. 🎨 前端框架 (Frontend)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **React** | `18.2.0` | 核心视图层库，采用函数式与 Class Component 架构构建用户界面。 |
| **React DOM** | `18.2.0` | 将 React 虚拟 DOM 渲染到 Web 端实际 DOM 的渲染器。 |
| **Bootstrap** | `5.3.2` | 提供响应式布局、预设样式与基础 CSS 框架。 |
| **React Bootstrap** | `2.9.2` | 将 Bootstrap 组件封装为 React 组件，方便基于状态驱动 UI 渲染。 |

### 2. ⚙️ 后端语言及运行环境 (Backend)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Java** | `17` | 后端核心开发语言，提供强大的生态支持和性能保障。 |
| **Spring Boot** | `4.2.0` | 应用脚手架，实现自动装配、快速构建生产级 RESTful API 服务。 |
| **Spring AI** | `2.1.0` | 统一抽象 LLM 和向量数据库交互，支持 Ollama、OpenAI 及 Milvus。 |
| **Apache Lucene** | `9.11.1` | 强大的文本搜索引擎库，用于实现本地化高性能的 BM25 关键词检索。 |
| **Apache POI** | `5.2.3` | 办公文档解析库（`poi` 及 `poi-ooxml`），辅助解析导入的结构化文档。 |

### 3. 🗄️ 数据库系统与基础设施 (Infrastructure)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Milvus** | 客户端 SDK `2.6.21` | 高性能向量数据库，存储高维嵌入向量（Embeddings）并实现快速的 Top-K 语义相似度搜索。`milvus-sdk-java` 由 `spring-ai-starter-vector-store-milvus` 传递引入；`docker-compose.yml` 部署的是 `milvusdb/milvus:v2.6.7` 单机版（etcd + MinIO + standalone），Testcontainers 集成测试也用同一镜像。 |
| **Redis** | Redis Stack Server | 用作高性能 KV 缓存，并借助 Spring Data Redis 持久化 BM25 全文索引，支持应用重启后的索引恢复。 |
| **Ollama** | 最新稳定版 | 本地运行的 LLM 引擎，用于执行 `qwen2.5:0.5b` 等语言模型及 `bge-m3:latest` 等嵌入模型。 |

### 4. 🧰 工具链与构建部署 (Toolchain)

| 技术/组件 | 版本号 | 核心作用说明 |
| --- | --- | --- |
| **Maven** | `3.6.3+` | 后端项目依赖管理及构建工具。 |
| **Node.js & npm** | Node `v18.16.0`+, npm `9.5.1`+ | 前端运行环境及包管理工具。 |
| **Frontend Maven Plugin** | `1.15.0` | 在 Maven 构建生命周期中自动构建前端项目（如下载 Node 并执行 `npm run build`）。 |
| **Docker & Compose** | `20+` | 容量化技术，统一编排 Redis、Milvus、应用本身等组件的部署环境，解决依赖冲突问题。 |
| **JaCoCo** | `0.8.16` | 代码覆盖率工具，在测试阶段生成统计报告，保障代码质量。 |
| **Testcontainers** | `1.21.4` | 集成测试用的容器运行时（Milvus 容器测试）。Spring Boot 4.2.0 不再统一管理该版本，已在 `pom.xml` 中显式钉住。 |

---

## 🤖 Chat、Embedding 与 Rerank 模型配置

项目将对话生成、文本嵌入和结果重排分为独立的模型角色。三者可以分别使用本地或远程 API。

| 角色 | 当前默认值 | API 地址 | 主要配置项 |
| --- | --- | --- | --- |
| Chat Model | 硅基流动 `Qwen/Qwen3-8B` | 远程：`https://api.siliconflow.cn` | `chat.provider=openai`、`spring.ai.openai.*` |
| Embedding Model | Ollama `bge-m3:latest` | 本地 Ollama：`http://127.0.0.1:11434` | `spring.ai.ollama.base-url`、`spring.ai.ollama.embedding.options.model` |
| Rerank Model | 硅基流动 `BAAI/bge-reranker-v2-m3` | 远程：`https://api.siliconflow.cn/v1/rerank` | `rerank.enabled=true`、`rerank.model` |

所有模型配置位于 [`src/main/resources/application.properties`](src/main/resources/application.properties)，Java 配置和调用实现位于：

* [`ChatModelConfiguration.java`](src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java)
* [`RagConfiguration.java`](src/main/java/com/redis/demo/spring/ai/RagConfiguration.java)
* [`RerankService.java`](src/main/java/com/redis/demo/spring/ai/service/RerankService.java)

### 1. Chat Model

`chat.provider` 属性用于切换 Chat Model 实现：

```properties
# 可选值：ollama 或 openai
chat.provider=openai
```

* `chat.provider=ollama`：使用本地 Ollama Chat API，由 `spring.ai.ollama.chat.options.model` 和 `spring.ai.ollama.base-url` 配置。
* `chat.provider=openai`：使用 OpenAI 兼容的 Chat Completions API，可连接官方 OpenAI 或硅基流动等兼容服务。

当前远程硅基流动配置：

```properties
spring.ai.openai.api-key=${SILICONFLOW_KEY:}
spring.ai.openai.chat.model=Qwen/Qwen3-8B
spring.ai.openai.chat.options.model=Qwen/Qwen3-8B
spring.ai.openai.base-url=https://api.siliconflow.cn
spring.ai.openai.chat.options.temperature=0.7
```

启动前设置 API Key：

```bash
# macOS / Linux
export SILICONFLOW_KEY=你的硅基流动API_KEY

# Windows 命令提示符
set SILICONFLOW_KEY=你的硅基流动API_KEY
```

切换为本地 Ollama 对话模型：

```properties
chat.provider=ollama
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.chat.options.model=qwen2.5:0.5b
spring.ai.ollama.connection-timeout=60s
spring.ai.ollama.read-timeout=60s
```

先下载并启动本地模型：

```bash
ollama pull qwen2.5:0.5b
ollama serve
```

### 2. Embedding Model

Embedding Model 当前不受 `chat.provider` 影响，始终使用 Ollama：

```properties
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.embedding.options.model=bge-m3:latest
spring.ai.ollama.embedding.connect-timeout=10s
spring.ai.ollama.embedding.read-timeout=300s
```

文档入库和用户查询向量化都会调用 `127.0.0.1:11434` 上的 Ollama。若 Ollama 部署在 Docker 容器或其他主机，请修改 `spring.ai.ollama.base-url`。

从 Spring Boot 4.2 起，`OllamaApi` 内部的默认请求工厂变为 Reactor Netty，带来约 10 秒的有效读超时，因此上述 embedding 超时需显式设置，而不能依赖 `spring.ai.ollama.read-timeout`。

```bash
ollama pull bge-m3:latest
ollama serve
```

向量维度必须与 Embedding Model 输出维度一致。当前 Milvus 配置使用 1024 维：

```properties
spring.ai.vectorstore.milvus.embeddingDimension=1024
```

### 3. Rerank Model

精排默认只有显式启用时才生效：

```properties
rerank.enabled=true
rerank.model=BAAI/bge-reranker-v2-m3
rerank.topN=5
```

Rerank 请求使用 `SILICONFLOW_KEY` 调用远程接口 `https://api.siliconflow.cn/v1/rerank`。如果精排 API 调用失败，`RerankService` 会降级为原始候选顺序，并返回配置的 `topN` 文档。

API Key 的读取优先级：

1. 环境变量 `SILICONFLOW_KEY`
2. 配置项 `siliconflow.api-key`
3. 未配置时记录警告并返回空 Key

### DeepSeek-OCR

PDF OCR 是独立的远程文档识别能力，不属于 Chat、Embedding 或 Rerank 模型角色。它使用硅基流动的 DeepSeek-OCR API：

```properties
deepseek-ocr.enabled=true
deepseek-ocr.model=deepseek-ai/DeepSeek-OCR
```

同样使用 `SILICONFLOW_KEY`。PDF 页面会在本地渲染为 JPEG 图片并转成 Base64，发送到远程 API 识别为 Markdown，再进行文档切块。

---

## 💻 环境依赖要求

为了避免环境冲突，请确保您的开发和运行环境满足以下最低兼容版本：

* **Java**：`JDK 17` 及以上（Spring Boot 4.2 的最低要求；`Dockerfile` 运行镜像为 Eclipse Temurin 21）
* **Maven**：`3.6.3` 及以上
* **Node.js**：`v18.16.0` 及以上（推荐 LTS 版本）
* **npm**：`9.5.1` 及以上
* **Docker**：最新稳定版
* **Docker Compose**：`v2.0.0` 及以上

---

## 🚀 本地部署与启动步骤

适配 Windows、macOS、Linux 主流开发环境的可直接执行指令如下。

### 1. 启动基础设施依赖（Milvus、Redis 等）

利用 Docker Compose 一键启动依赖组件：

```bash
cd spring-ai-redis-demo
docker-compose up -d
```

Compose 文件已在 Redis 旁内置 Milvus v2.6.7 单机版所需的三个容器：

| 服务 | 镜像 | 端口 |
| --- | --- | --- |
| `etcd` | `quay.io/coreos/etcd:v3.5.5` | `2379`（仅 compose 网络内访问） |
| `minio` | `minio/minio:RELEASE.2023-03-20T20-16-18Z` | `9000`（S3）/ `9001`（控制台） |
| `standalone` | `milvusdb/milvus:v2.6.7` | `19530`（gRPC）/ `9091`（metrics） |

若只需先启动向量数据库：

```bash
docker-compose up -d etcd minio standalone
docker-compose logs -f standalone
```

`standalone` 没有配置 healthcheck，`docker-compose ps` 只会显示 `running`；请等到 `19530` 端口真正监听后再启动后端。

后端在宿主机运行时，`spring.ai.vectorstore.milvus.client.host` 填 `127.0.0.1`；跑在同一 compose 网络内时填服务名 `standalone`（`spring` 服务已通过 `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST` 覆盖）。端口在 `RagConfiguration` 中固定为 `19530`。镜像源 retag、数据卷与安全提示、停止清理步骤详见 [DOCKER-README.md](DOCKER-README.md)。

### 2. 下载并运行本地模型（Ollama）

确保已安装 [Ollama](https://ollama.com/)：

```bash
# 下载嵌入模型（1024 维向量）
ollama pull bge-m3:latest

# 下载可选的本地对话模型
ollama pull qwen2.5:0.5b
```

### 3. 配置环境变量

如需通过硅基流动使用远程 Chat、Rerank 或 DeepSeek-OCR：

```bash
# Windows
set SILICONFLOW_KEY=你的硅基流动API_KEY

# macOS / Linux
export SILICONFLOW_KEY=你的硅基流动API_KEY
```

如需使用 Redis Cloud 或远程 Redis：

```bash
export REDIS_HOST=你的Redis地址
export REDIS_PORT=6379
export REDIS_PASSWORD=你的Redis密码
```

### 4. 编译与启动后端服务

由于使用了 `frontend-maven-plugin`，Maven 会在构建时自动完成前端的下载、依赖安装与编译。

```bash
# Windows
mvnw.cmd clean install -DskipTests
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

应用启动后，将在端口 `8080` 监听。访问 [http://localhost:8080](http://localhost:8080) 即可看到前端 React 界面。

附：单独启动前端的命令：

```bash
cd frontend
npm install
npm start
```

---

## 📂 项目结构说明

```text
spring-ai-redis-demo/
├── frontend/                       # React 前端工程目录
│   ├── public/                     # 静态资源（HTML 模板、图标等）
│   ├── src/                        # 前端源代码
│   │   ├── Components/             # 界面组件（如 ChatWindow）
│   │   ├── App.js / api.js         # 前端根组件与 API 请求封装
│   └── package.json                # 前端依赖配置
├── src/
│   ├── main/
│   │   ├── java/com/redis/demo/    # 后端 Java 核心业务逻辑
│   │   │   ├── RagApplication.java # Spring Boot 启动类
│   │   │   ├── RagController.java  # REST API 路由
│   │   │   ├── RagConfiguration.java# AI 及向量数据库 Bean 配置
│   │   │   ├── RagDataLoader.java  # 应用启动时初始数据的预处理和注入
│   │   │   └── service/            # RAG、精排、BM25 搜索等业务服务
│   │   └── resources/              # 后端配置（application.properties 等）
│   └── test/                       # 单元测试与集成测试
├── Dockerfile                      # 应用容器构建文件
├── docker-compose.yml              # 依赖服务编排文件
└── pom.xml                         # Maven 构建与依赖管理配置文件
```

---

## 📜 开发规范

1. **分支管理**：
    - `main`：用于发布稳定版本，不允许直接 Push。
    - `develop`：开发主分支。
    - `feature/*`：新功能分支，从 `develop` 检出，开发完成后合并回 `develop`。
2. **提交规范**：
    - 采用 Angular 规范格式：`<type>(<scope>): <subject>`（例如 `feat(rag): 增加 RRF 混合检索算法`、`fix(ui): 修复聊天气泡溢出问题`）。
3. **代码风格**：
    - Java：遵循 Spring Boot 官方编码规范，并利用 Lombok `@Slf4j`、`@Data` 简化代码。
    - 前端：遵循 ESLint + Prettier，采用函数式组件与 Hooks 为主（在逐步迁移旧 Class 组件）。
4. **组件职责**：
    - 保持单一职责原则。Controller 层只处理 HTTP 请求包装；大模型提示拼接、检索流程交由 Service 层处理。

---

## ❓ 常见问题排查（Troubleshooting）

1. **前端编译失败或 Node 找不到**
    - **问题表现**：运行 `mvn clean install` 时在 `frontend-maven-plugin` 步骤报错。
    - **解决方案**：检查系统是否正确安装 Node.js（`v18+`），或者清理 Maven 缓存后重试。也可以手动进入 `frontend` 目录执行 `npm install` 与 `npm run build`。

2. **Milvus 连接被拒绝 / Connection Refused**
    - **问题表现**：后端启动时抛出 `Failed to connect to Milvus` 异常。
    - **解决方案**：检查 Docker Compose 是否已完全启动（`docker ps` 确保 Milvus 状态为 `healthy` 或 `running`）；`standalone` 容器启动较慢，可用 `docker-compose logs standalone` 确认 `19530` 已开始监听；若 Milvus 部署在远程，请确保 `application.properties` 中的 `spring.ai.vectorstore.milvus.client.host` 地址可达。

3. **Ollama 模型拉取失败或超时**
    - **问题表现**：对话或生成 Embedding 时后端日志提示模型调用超时或 404。
    - **解决方案**：通过 `ollama pull <model_name>` 将 `bge-m3:latest` 或 `qwen2.5:0.5b` 拉取到本地，并检查 `http://127.0.0.1:11434` 是否存活。

4. **远程 API Key 缺失或被拒绝**
    - **问题表现**：Chat、Rerank 或 DeepSeek-OCR 调用出现鉴权或授权错误。
    - **解决方案**：设置 `SILICONFLOW_KEY`，并确认该 Key 有权访问配置中的模型。

5. **Redis OOM（内存溢出）**
    - **问题表现**：加载海量数据建立 BM25 索引持久化时 Redis 崩溃。
    - **解决方案**：后端应用具备降级到本地 Lucene 内存索引的机制；请监控 Redis 内存分配，如有需要可在 `docker-compose.yml` 中调整 Redis 内存限制。
