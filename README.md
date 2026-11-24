# Spring AI Redis Demo Technical Architecture and Usage Guide

English | [简体中文](ReadMe_Zhcn.md)

[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCAxMkwxMiA0TDQgMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00IDEyTDEyIDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/megadotnet/spring-ai-redis-demo)

This project is a Retrieval-Augmented Generation (RAG) demonstration application based on Spring Boot 4.2.0 and Spring AI 2.1.0. It integrates the Milvus vector database and Large Language Models (LLMs) to provide an intelligent question-answering system for knowledge documents.

---

## 🛠️ Tech Stack List

The following details the specific technology selections, their versions, and core roles for each core module in this project.

### 1. 🎨 Frontend Framework

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **React** | `18.2.0` | Core view layer library, building the user interface using functional and Class Component architectures. |
| **React DOM** | `18.2.0` | The renderer that renders React's virtual DOM to the actual DOM in the web environment. |
| **Bootstrap** | `5.3.2` | Provides responsive layout, preset styles, and a foundational CSS framework. |
| **React Bootstrap** | `2.9.2` | Encapsulates Bootstrap components as React components, facilitating state-driven UI rendering. |

### 2. ⚙️ Backend Language and Runtime Environment

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Java** | `17` | The core backend programming language, providing strong ecosystem support and performance guarantees. |
| **Spring Boot** | `4.2.0` | Application scaffolding, enabling auto-configuration and rapid construction of production-grade RESTful API services. |
| **Spring AI** | `2.1.0` | Unified abstraction for interacting with LLMs and vector databases, supporting Ollama, OpenAI, and Milvus. |
| **Apache Lucene** | `9.11.1` | Powerful text search engine library, used to implement localized, high-performance BM25 keyword search. |
| **Apache POI** | `5.2.3` | Office document parsing library (`poi` and `poi-ooxml`), assisting in parsing imported structured documents. |

### 3. 🗄️ Database Systems and Infrastructure

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Milvus** | Client SDK `2.6.21` | High-performance vector database, storing high-dimensional embeddings and implementing fast Top-K semantic similarity search. The `milvus-sdk-java` client is provided transitively by `spring-ai-starter-vector-store-milvus`; `docker-compose.yml` deploys the `milvusdb/milvus:v2.6.7` standalone stack (etcd + MinIO + standalone), which is also the image the Testcontainers integration test uses. |
| **Redis** | Redis Stack Server | Used as a high-performance KV cache, and through Spring Data Redis, it persists the BM25 full-text index, supporting index recovery after application restarts. |
| **Ollama** | Latest Stable | Locally run LLM engine, used to execute language models like `qwen2.5:0.5b` and embedding models like `bge-m3:latest`. |

### 4. 🧰 Toolchain and Build/Deployment

| Technology/Component | Version | Core Role Description |
| --- | --- | --- |
| **Maven** | `3.6.3+` | Backend project dependency management and build tool. |
| **Node.js & npm** | Node `v18.16.0`+, npm `9.5.1`+ | Frontend runtime environment and package management tool. |
| **Frontend Maven Plugin**| `1.15.0` | Automatically builds the frontend project during the Maven build lifecycle (e.g., downloading Node and executing `npm run build`). |
| **Docker & Compose** | `20+` | Containerization technology, orchestrating the deployment environment of Redis, Milvus, and the application itself to resolve dependency conflicts. |
| **JaCoCo** | `0.8.16` | Code coverage tool, generating statistical reports during the testing phase to ensure code quality. |
| **Testcontainers** | `1.21.4` | Test-scope container runtime for the Milvus integration test. Spring Boot 4.2.0 no longer manages this version, so it is pinned explicitly in `pom.xml`. |

---

## 🤖 Chat, Embedding, and Rerank Model Configuration

The project separates the Chat Model, Embedding Model, and Rerank Model. Their provider and deployment location are configured independently.

| Role | Current default | API location | Main configuration |
| --- | --- | --- | --- |
| Chat Model | SiliconFlow `Qwen/Qwen3-8B` | Remote: `https://api.siliconflow.cn` | `chat.provider=openai`, `spring.ai.openai.*` |
| Embedding Model | Ollama `bge-m3:latest` | Local Ollama: `http://127.0.0.1:11434` | `spring.ai.ollama.base-url`, `spring.ai.ollama.embedding.options.model` |
| Rerank Model | SiliconFlow `BAAI/bge-reranker-v2-m3` | Remote: `https://api.siliconflow.cn/v1/rerank` | `rerank.enabled=true`, `rerank.model` |

All model settings are in [`src/main/resources/application.properties`](src/main/resources/application.properties). The Java provider selection is implemented by:

* [`ChatModelConfiguration.java`](src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java)
* [`RagConfiguration.java`](src/main/java/com/redis/demo/spring/ai/RagConfiguration.java)
* [`RerankService.java`](src/main/java/com/redis/demo/spring/ai/service/RerankService.java)

### 1. Chat Model

The `chat.provider` property switches the Chat Model implementation:

```properties
# ollama or openai
chat.provider=openai
```

* `chat.provider=ollama` uses the local Ollama Chat API. It is configured by `spring.ai.ollama.chat.options.model` and `spring.ai.ollama.base-url`.
* `chat.provider=openai` uses the OpenAI-compatible Chat Completions API. It can be set to official OpenAI or another compatible provider such as SiliconFlow.

The current remote SiliconFlow configuration is:

```properties
spring.ai.openai.api-key=${SILICONFLOW_KEY:}
spring.ai.openai.chat.model=Qwen/Qwen3-8B
spring.ai.openai.chat.options.model=Qwen/Qwen3-8B
spring.ai.openai.base-url=https://api.siliconflow.cn
spring.ai.openai.chat.options.temperature=0.7
```

Set the API key before starting the application:

```bash
# macOS / Linux
export SILICONFLOW_KEY=your_siliconflow_api_key

# Windows Command Prompt
set SILICONFLOW_KEY=your_siliconflow_api_key
```

To switch back to local Ollama chat generation:

```properties
chat.provider=ollama
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.chat.options.model=qwen2.5:0.5b
spring.ai.ollama.connection-timeout=60s
spring.ai.ollama.read-timeout=60s
```

Download the local model first:

```bash
ollama pull qwen2.5:0.5b
ollama serve
```

### 2. Embedding Model

The Embedding Model is currently independent of `chat.provider` and always uses Ollama:

```properties
spring.ai.ollama.base-url=http://127.0.0.1:11434
spring.ai.ollama.embedding.options.model=bge-m3:latest
spring.ai.ollama.embedding.connect-timeout=10s
spring.ai.ollama.embedding.read-timeout=300s
```

This means document ingestion and user query embedding are sent to Ollama on `127.0.0.1:11434`. Change `spring.ai.ollama.base-url` when Ollama runs in a container or on another host.

Since Spring Boot 4.2, the default request factory behind `OllamaApi` is Reactor Netty, which applies an effective read timeout of about 10 seconds, so the embedding timeouts above are set explicitly instead of relying on `spring.ai.ollama.read-timeout`.

```bash
ollama pull bge-m3:latest
ollama serve
```

The vector dimension must match the embedding model. The current Milvus configuration uses 1024 dimensions:

```properties
spring.ai.vectorstore.milvus.embeddingDimension=1024
```

### 3. Rerank Model

Reranking is disabled unless explicitly enabled:

```properties
rerank.enabled=true
rerank.model=BAAI/bge-reranker-v2-m3
rerank.topN=5
```

The rerank call is sent to the remote SiliconFlow endpoint `https://api.siliconflow.cn/v1/rerank` using `SILICONFLOW_KEY`. If the API call fails, `RerankService` falls back to the original candidate order and returns the configured `topN` documents.

The API key is resolved in this order:

1. `SILICONFLOW_KEY` environment variable
2. `siliconflow.api-key` property
3. The empty-key fallback, for which the service logs a warning

### DeepSeek-OCR

PDF OCR is a separate remote service and is not a Chat, Embedding, or Rerank Model role. It uses SiliconFlow's remote DeepSeek-OCR API:

```properties
deepseek-ocr.enabled=true
deepseek-ocr.model=deepseek-ai/DeepSeek-OCR
```

It also uses `SILICONFLOW_KEY`. PDF pages are rendered locally, sent as Base64 JPEG images to the remote API, and converted to Markdown before chunking.

---

## 💻 Environmental Dependency Requirements

To avoid environmental conflicts, please ensure your development and runtime environments meet the following minimum compatible versions:

* **Java**: `JDK 17` or higher (the minimum required by Spring Boot 4.2; the `Dockerfile` runtime image is Eclipse Temurin 21)
* **Maven**: `3.6.3` or higher
* **Node.js**: `v18.16.0` or higher (LTS version recommended)
* **npm**: `9.5.1` or higher
* **Docker**: Latest stable version
* **Docker Compose**: `v2.0.0` or higher

---

## 🚀 Local Deployment and Startup Steps

The directly executable instructions adapted for Windows, macOS, and Linux mainstream development environments are as follows.

### 1. Start Infrastructure Dependencies (Milvus, Redis, etc.)

Use Docker Compose to start the dependency components with one click:

```bash
cd spring-ai-redis-demo
docker-compose up -d
```

The Compose file ships the Milvus v2.6.7 standalone stack next to Redis:

| Service | Image | Ports |
| --- | --- | --- |
| `etcd` | `quay.io/coreos/etcd:v3.5.5` | `2379` (compose network only) |
| `minio` | `minio/minio:RELEASE.2023-03-20T20-16-18Z` | `9000` (S3) / `9001` (console) |
| `standalone` | `milvusdb/milvus:v2.6.7` | `19530` (gRPC) / `9091` (metrics) |

To start only the vector database:

```bash
docker-compose up -d etcd minio standalone
docker-compose logs -f standalone
```

`standalone` has no healthcheck, so `docker-compose ps` only reports `running`; wait until port `19530` is actually listening before launching the backend.

Set `spring.ai.vectorstore.milvus.client.host` to `127.0.0.1` when the backend runs on the host, or to the service name `standalone` when it runs inside this compose network (the `spring` service already overrides it through `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST`). The port is fixed at `19530` in `RagConfiguration`. Image mirrors, data volumes, credentials, and cleanup steps are documented in [DOCKER-README.md](DOCKER-README.md).

### 2. Download and Run Local Models (Ollama)

Ensure you have installed [Ollama](https://ollama.com/):

```bash
# Download and run the embedding model (1024-dimensional vectors)
ollama pull bge-m3:latest

# Download and run the optional local chat model
ollama pull qwen2.5:0.5b
```

### 3. Configure Environment Variables

For remote Chat, Rerank, and DeepSeek-OOCR usage through SiliconFlow:

```bash
# Windows
set SILICONFLOW_KEY=your_siliconflow_api_key

# macOS / Linux
export SILICONFLOW_KEY=your_siliconflow_api_key
```

For Redis Cloud or a remote Redis server:

```bash
export REDIS_HOST=your_redis_host
export REDIS_PORT=6379
export REDIS_PASSWORD=your_redis_password
```

### 4. Compile and Start Backend Service

Because the `frontend-maven-plugin` is used, Maven will automatically handle frontend downloading, dependency installation, and compilation during the build process.

```bash
# Windows
mvnw.cmd clean install -DskipTests
mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw clean install -DskipTests
./mvnw spring-boot:run
```

Once the application starts, it will automatically listen on port `8080`. Visit [http://localhost:8080](http://localhost:8080) to see the frontend React interface.

*(Appendix: Commands to start the frontend separately:)*

```bash
cd frontend
npm install
npm start
```

---

## 📂 Project Structure

```text
spring-ai-redis-demo/
├── frontend/                       # React frontend project directory
│   ├── public/                     # Static resources (HTML templates, icons, etc.)
│   ├── src/                        # Frontend source code
│   │   ├── Components/             # UI components (e.g., ChatWindow)
│   │   ├── App.js / api.js         # Frontend root component and API request encapsulation
│   └── package.json                # Frontend dependency configuration
├── src/
│   ├── main/
│   │   ├── java/com/redis/demo/    # Backend Java core business logic
│   │   │   ├── RagApplication.java # Spring Boot startup class
│   │   │   ├── RagController.java  # REST API routing
│   │   │   ├── RagConfiguration.java# AI and vector database Bean configuration
│   │   │   ├── RagDataLoader.java  # Pre-processing and injection of initial data on startup
│   │   │   └── service/            # RAG, rerank, BM25 search and other business services
│   │   └── resources/              # Backend configuration (application.properties, etc.)
│   └── test/                       # Unit and integration tests
├── Dockerfile                      # Application container build file
├── docker-compose.yml              # Dependency service orchestration file
└── pom.xml                         # Maven build and dependency management configuration file
```

---

## 📜 Development Guidelines

1. **Branch Management**:
    - `main`: Used for releasing stable versions, direct Push is not allowed.
    - `develop`: Main development branch.
    - `feature/*`: New feature branches, checked out from `develop` and merged back into `develop` upon completion.
2. **Commit Conventions**:
    - Adopt Angular specification formats: `<type>(<scope>): <subject>` (e.g., `feat(rag): add RRF hybrid search algorithm`, `fix(ui): fix chat bubble overflow issue`).
3. **Code Style**:
    - Java: Follow official Spring Boot coding guidelines, and utilize Lombok `@Slf4j`, `@Data` to simplify code.
    - Frontend: Follow ESLint + Prettier, using functional components and Hooks predominantly (while gradually migrating legacy Class components).
4. **Component Responsibilities**:
    - Maintain the single responsibility principle. The Controller layer should only handle HTTP request packaging; specific LLM prompt concatenation and retrieval processes should be handled by the Service layer.

---

## ❓ Troubleshooting

1. **Frontend compilation failed or Node not found**
    - **Symptom**: Error occurs at the `frontend-maven-plugin` step when running `mvn clean install`.
    - **Solution**: Check if Node.js (`v18+`) is correctly installed on your system, or try clearing the Maven cache and trying again. Alternatively, manually enter the `frontend` directory to execute `npm install` and `npm run build`.

2. **Milvus Connection Refused**
    - **Symptom**: Backend throws `Failed to connect to Milvus` exception upon startup.
    - **Solution**: Check if Docker Compose is fully started (`docker ps` to ensure milvus status is `healthy` or `running`); the `standalone` container needs extra time before it serves `19530`, so inspect `docker-compose logs standalone` as well. If the Milvus server is deployed elsewhere, make sure `spring.ai.vectorstore.milvus.client.host` in `application.properties` points to that reachable address.

3. **Ollama model pull failed or timed out**
    - **Symptom**: Backend logs indicate model call timeout or 404 during conversation.
    - **Solution**: Ensure you have successfully pulled the model (e.g., `qwen2.5`) locally beforehand via `ollama pull <model_name>`, and check if `http://127.0.0.1:11434` is alive.

4. **Remote API Key missing or rejected**
    - **Symptom**: Chat, rerank, or DeepSeek-OCR calls fail with authentication or authorization errors.
    - **Solution**: Set `SILICONFLOW_KEY` and verify that the key has access to the configured models.

5. **Redis OOM (Out Of Memory) Issues**
    - **Symptom**: Redis crashes when loading massive data to establish BM25 index persistence.
    - **Solution**: The backend application has a mechanism to automatically downgrade to a local Lucene in-memory index; please monitor Redis memory allocation.
