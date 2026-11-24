# Spring Boot 4.2.0 / Spring AI 2.1.0 迁移实施记录

> 项目：`spring-ai-redis-demo`
> 源版本：Spring Boot 3.5.15、Spring AI 1.1.8
> 目标版本：Spring Boot 4.2.0、Spring AI 2.1.0
> 迁移依据：`MIGRATION-SPRING-BOOT-4.2.0_Version2.md`（范围、顺序、验证要求）
> 分支：`features/ollama-pdf-deepseekocr`（HEAD = `5e09f45`，即迁移前基线）
> 记录日期：2026-11-24

---

## 0. 执行环境事实

| 项 | 事实 | 对验证的影响 |
|---|---|---|
| Maven | 系统 Maven 3.9.5，**无 `./mvnw`** | 文档中的 `./mvnw` 命令统一替换为 `mvn` |
| 本地仓库 | `F:\AppData2026\repository`（非默认 `~/.m2`） | `javap` 取证必须使用该路径 |
| Shell | PowerShell，不支持 `&&` | 命令用 `;` 分隔 |
| JDK | 编译目标 Java 17；Maven 运行于 JDK 21 | JaCoCo 需支持新字节码 |
| Docker | **不可用**（无 `docker` CLI，`tcp://localhost:2375` 无守护进程） | Testcontainers 集成测试无法运行 |
| Ollama | `127.0.0.1:11434` 可达 | embedding / Ollama chat 在线可测 |
| Milvus | `192.168.0.218:19530` 可达（集合 `paper_collection_vpdf`，文档数 94） | 向量检索在线可测 |
| Redis | `REDIS_HOST`/`REDIS_PORT` 指向 Redis Cloud | BM25 持久化/恢复在线可测 |
| SiliconFlow | `SILICONFLOW_KEY` 存在，`api.siliconflow.cn` 可达但**延迟不稳定** | 见 §6 外因失败定性 |

---

## 1. 结论摘要

- **迁移本身完成**：主源码与测试源码全部编译通过；`mvn test` 结果 `Tests run: 80, Failures: 0, Errors: 2, Skipped: 0`，其中 78 项通过、**0 项断言失败**。
- **2 个 Error 为外部服务不稳定**，已用「迁移前基线对照实验」证明同一用例、同一调用行在升级前同样失败 → **非迁移回归**。
- **2 类环境依赖跳过项如实记录为未验证**（Docker/Testcontainers IT、Actuator Prometheus 运行时），**未误报为迁移通过**。
- 严格遵守范围边界：未重写 Docker Compose、未改 Milvus 集合 schema、未换向量维度、未升级 React、未改 BM25 Redis 持久化格式、未切换外部模型服务。

---

## 2. P0：基线与目标 API 核对

基线状态：`mvn clean compile`（Boot 3.5.15 / AI 1.1.8）通过；目标版本 4.2.0 / 2.1.0 均可从 Maven 仓库解析。

**真实 API 核对结论**（以 `javap` 解析目标 JAR 为准，多处与迁移文档候选写法不一致）：

| 核对项 | 迁移文档候选写法 | 目标版本真实 API | 采纳 |
|---|---|---|---|
| `RestClientCustomizer` 包路径 | `org.springframework.boot.restclient` | 确认已迁至 `org.springframework.boot.restclient`（旧为 `boot.web.client`） | 改 import |
| `RestTemplateBuilder` 超时方法 | `setConnectTimeout` / `setReadTimeout` | **已改名** `connectTimeout(...)` / `readTimeout(...)` | 改方法名 |
| `OpenAiApi` | 继续使用 | **2.1.0 已彻底移除**，OpenAI 集成改基于官方 SDK `com.openai.client.OpenAIClient` | 删除 bean，重写 |
| `OpenAiChatModel.Builder` | `.openAiApi(...)` | 只有 `.options(...)`；`build()` 内部按 options 自动创建 client | 改用 options |
| `OpenAiChatOptions.Builder` | 仅 model/temperature | 支持 `apiKey` / `baseUrl` / `timeout` / `model` / `temperature` | 用于承载超时 |
| `OllamaChatModel.Builder` | `.defaultOptions(...)` | **实为 `.options(...)`** | 改方法名 |
| `OllamaEmbeddingModel` | 改 builder | **四参构造函数仍在**（api/options/registry/modelManagementOptions） | 保持原样 |
| `MilvusVectorStore.builder` | `builder(embeddingModel).milvusClient(client)` | **`.milvusClient(...)` 不存在**；真实签名为 `builder(MilvusServiceClient, EmbeddingModel)` 两参 | 保持原样（文档候选写法错误） |
| `Document.getText()` | 改为 `text()` | **`getText()` 仍在，无 `text()`**；`Document(String, Map)` 构造仍在 | §8 为 no-op |
| Jackson 3 | `tools.jackson.*` | 引入 `tools.jackson.core:*:3.1.8`；`JacksonException extends RuntimeException`（非受检） | 迁 Jackson 3 |
| OpenAI 自动配置 FQCN | 可能已改名 | `org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration` **未变** | 排除项保留 |

**风险点**：若照抄文档 §5.2 / §5.3 / §6.1 / §6.2 / §8 的候选写法，会直接导致编译失败或引入未经证实的行为变更。已全部以目标 JAR 真实签名替代。

**验证命令**

```powershell
mvn -DskipTests dependency:tree
mvn help:effective-pom > effective-pom.xml
javap -cp F:\AppData2026\repository\org\springframework\ai\spring-ai-openai\2.1.0\spring-ai-openai-2.1.0.jar org.springframework.ai.openai.OpenAiChatModel
javap -cp F:\AppData2026\repository\org\springframework\ai\spring-ai-milvus-store\2.1.0\spring-ai-milvus-store-2.1.0.jar org.springframework.ai.vectorstore.milvus.MilvusVectorStore
```

**回滚检查**：本阶段无代码改动。

---

## 3. P1：`pom.xml` 与构建插件

**修改清单**

1. `spring-boot-starter-parent`：`3.5.15` → `4.2.0`。
2. `<spring-ai.version>`：`1.1.8` → `2.1.0`（Spring AI BOM 仍由同一属性 import 管理）。
3. 删除重复的 `com.google.protobuf:protobuf-java` 声明，仅保留一条 `4.29.3`。
4. 新增属性 `<testcontainers.version>1.21.4</testcontainers.version>`，并给 `org.testcontainers:junit-jupiter` 与 `org.testcontainers:testcontainers` 添加显式 `<version>`。
   - 依据：**Boot 4.2 BOM 不再管理 Testcontainers**，不显式钉版会导致 POM 解析失败（`dependencies.dependency.version is missing`）。
5. JaCoCo `0.8.8` → `0.8.16`（0.8.8 无法 instrument JDK 21 字节码，基线日志中已出现大量 `Instrumenter` 异常栈）。
6. 删除 `maven-surefire-plugin` / `maven-failsafe-plugin` 的硬编码 `3.0.0-M9`，改由 Boot 4.2 父 POM 管理（effective-pom 确认解析为 **3.6.1**）。
7. 新增测试依赖（Boot 4 测试模块拆分所致）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

8. `commons-io 2.15.1` **保守保留**：解除版本锁需以 Milvus/Tika 集成测试为依据，而该测试依赖 Docker（本环境不可用），故本轮不解钉，符合文档「不删除未证明必要的锁定，也不做未证明的变更」。

**风险点**

- Testcontainers 版本若漏钉，整个构建解析失败（已验证并修复）。
- Jackson 2 / Jackson 3 双栈共存（见 §8，已解释来源）。
- surefire `**/*Test.java` 与 failsafe `**/*IntegrationTest.java` 存在重叠包含：`Bm25IndexRecoveryIntegrationTest` 实际在 `mvn test` 阶段执行（不依赖 Docker），`RagDataLoaderIntegrationTest` 需 Docker 且未执行。

**验证命令**

```powershell
mvn -DskipTests dependency:tree
mvn help:effective-pom | Select-String 'maven-surefire-plugin|maven-failsafe-plugin|jacoco'
```

**回滚检查**：`git checkout -- pom.xml` 即恢复 3.5.15 / 1.1.8 依赖图。

---

## 4. P2：`RagConfiguration.java` 与 `ChatModelConfiguration.java`

**修改清单**

`RagConfiguration.java`

- 仅改 `import org.springframework.boot.restclient.RestClientCustomizer;`。
- `restClientCustomizer()` 的超时逻辑（connect 10s / read 60s）**原样保留**。
- `OllamaEmbeddingModel` 四参构造、`MilvusVectorStore.builder(client, embeddingModel)` **未改动**（javap 已验证在 2.1.0 中仍为有效签名）。

`ChatModelConfiguration.java`

- 删除 `import ...openai.api.OpenAiApi;`、`import ...web.client.RestClient;` 及 `openAiApi` bean。
- Ollama chat：`.defaultOptions(` → `.options(`（`modelManagementOptions.timeout(readTimeout)` 保留）。
- OpenAI chat 重写为 SDK 形态，并保留 API key 空值校验：

```java
@Bean
@Primary
@ConditionalOnProperty(name = "chat.provider", havingValue = "openai")
public ChatModel openAiChatModel(
        ObjectProvider<ObservationRegistry> observationRegistry,
        @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}") String baseUrl,
        @Value("${SILICONFLOW_KEY:${spring.ai.openai.api-key:${OPENAI_API_KEY:}}}") String apiKey,
        @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String model,
        @Value("${spring.ai.openai.chat.options.temperature:0.7}") Double temperature,
        @Value("${spring.ai.openai.timeout:60s}") Duration timeout) {
    if (apiKey == null || apiKey.isEmpty()) {
        throw new IllegalArgumentException("OpenAI API Key is required...");
    }
    return OpenAiChatModel.builder()
            .options(OpenAiChatOptions.builder()
                    .apiKey(apiKey).baseUrl(baseUrl)
                    .model(model).temperature(temperature)
                    .timeout(timeout)
                    .build())
            .observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
            .build();
}
```

**HTTP 超时的载体迁移（关键设计决策）**

- 迁移前：OpenAI 走 Spring `RestClient`，超时由 `RestClientCustomizer`（read 60s）提供。
- 迁移后：OpenAI 走官方 SDK 的 OkHttp 客户端，**`RestClientCustomizer` 对 OpenAI 链路不再生效**；因此把超时下沉到 `OpenAiChatOptions.timeout`，默认 `60s`。
- 结果：上游超时 60s < `RagController.orTimeout(65s)` < `spring.mvc.async.request-timeout(90s)` 的既有顺序**保持不变**，未出现「超时变为无限等待」的退化。
- ⚠️ 但当时「超时口径未变」的结论**只对 OpenAI 链路成立**：Ollama 链路存在被遗漏的迁移回归（默认工厂变更导致隐式 ~10s 读超时），已在运行期暴露并修复，详见 §11。
- API key 优先级仍为 `SILICONFLOW_KEY` > `spring.ai.openai.api-key` > `OPENAI_API_KEY`，与 base-url 保持同源（避免历史上出现过的 key 与 base-url 不匹配 401 问题）。

**风险点**

- `@Primary` + `@ConditionalOnProperty` 与 `spring.autoconfigure.exclude` 的 OpenAI 排除项必须同时成立，否则出现重复 ChatModel bean（已确认 2.1.0 自动配置 FQCN 未变，排除仍生效）。
- `chat.provider=ollama` 分支需保证 `openAiChatModel` 不实例化（否则无 key 时启动即失败）。

**验证命令**

```powershell
mvn clean compile
mvn test -Dtest=RagControllerTest   # 验证上下文与 bean 组装
```

**回滚检查**：`git checkout -- src/main/java/com/redis/demo/spring/ai/RagConfiguration.java src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java`

---

## 5. P3：Jackson 3、Document API、`application.properties`

**Jackson 2 → 3（主源码）**

| 文件 | 改动 |
|---|---|
| `service/RerankService.java` | `com.fasterxml.jackson.databind.*` → `tools.jackson.databind.*`；`new ObjectMapper()` → `JsonMapper.builder().build()`；`Document::getText` 保持 |
| `service/DeepSeekOcrDocumentReader.java` | Jackson 3；`RestTemplateBuilder` 迁 `boot.restclient` 包 + `connectTimeout/readTimeout` 改名；**仍用 RestTemplate、120s 读超时不变** |
| `service/BM25DocumentPersistenceService.java` | Jackson 3；`catch (JsonProcessingException)` → `catch (tools.jackson.core.JacksonException)`（Jackson 3 非受检）；`new Document(text, metadata)` 保留 |
| `evaluation/{Faithfulness,RagEvaluation,Correctness}TestHelper.java` | Jackson 3 + `JsonMapper.builder().build()`（`catch IOException` 仍有效） |

全库残留检查：`src/main`、`src/test` 业务代码中已无 `com.fasterxml.jackson` 引用（仅 `tools.jackson`）。

**Spring AI `Document` API —— 经验证的 no-op**

按文档 §8 要求逐处核对接收者类型，**未做任何 `.getText()` → `.text()` 替换**：

- 2.1.0 的 `Document` 仍提供 `getText()`，不存在 `text()`；
- POI `XWPFTableCell.getText()`、PDF 等其他库同名 API 与 Spring AI 无关，未被误改；
- `ChatResponse.getResult().getOutput().getText()`（chat 输出 API）签名未变；
- `new Document(String, Map)` 构造仍在，故 Redis 反序列化路径不改 builder，避免无谓变更面。

**`application.properties`**

```properties
# Boot 4.x 属性键（旧 management.metrics.export.prometheus.enabled 已废弃）
management.prometheus.metrics.export.enabled=true
```

- OpenAI / Ollama 自动配置排除项经 FQCN 核对**无需修改**，保留原样。
- Pinecone 属性属历史遗留，本轮按「不清理无关配置」原则未动。

**风险点**：Jackson 3 的异常改为非受检，若某处仍依赖受检异常语义会静默改变控制流（已在 BM25 持久化处按编译器提示改为 `JacksonException`，行为等价）。Redis JSON 读写格式未改，旧数据兼容（在线恢复 94 篇已验证）。

**验证命令**

```powershell
mvn clean compile
mvn test -Dtest=BM25SearchServiceTest+RerankServiceTest+Bm25IndexRecoveryIntegrationTest
```

**回滚检查**：`git checkout -- src/main/java/com/redis/demo/spring/ai/service/ src/main/resources/application.properties`

---

## 6. P4：测试调整与全量回归

**测试代码修改清单**

| 文件 | 改动 | 依据 |
|---|---|---|
| `RagControllerTest.java` | `@WebMvcTest` 迁至 `org.springframework.boot.webmvc.test.autoconfigure`；2 处 `@MockBean` → `@MockitoBean` | Boot 4 移除 `@MockBean`，`@WebMvcTest` 拆到 `spring-boot-webmvc-test` |
| `RagDataLoaderIntegrationTest.java` | `@MockBean` → `@MockitoBean` | 同上 |
| `evaluation/*TestHelper.java` | Jackson 3 迁移 | §5 |

**编译与测试结果**

```
mvn clean compile   -> BUILD SUCCESS
mvn test-compile    -> BUILD SUCCESS
mvn test            -> Tests run: 80, Failures: 0, Errors: 2, Skipped: 0
```

| 测试类 | 结果 | 覆盖的业务能力 |
|---|---|---|
| `RagControllerTest` | 7/7 ✅ | WebMVC 切片上下文 + 聊天 HTTP 链路 |
| `RagServiceTest` | 7/7 ✅ | RAG 上下文组装与检索 DTO |
| `HybridSearchServiceTest` | 5/5 ✅ | **Milvus 稠密 + Lucene BM25 稀疏 + RRF 融合** |
| `BM25SearchServiceTest` | 8/8 ✅ | Lucene BM25 索引（版本行为未变） |
| `Bm25IndexRecoveryIntegrationTest`（"BM25 索引重建集成测试"） | 5/5 ✅ | **Redis BM25 持久化与重启恢复**（Redis Cloud，94 篇） |
| `RerankServiceTest` | 6/6 ✅ | **SiliconFlow rerank** + 异常降级为原序 |
| `DeepSeekOcrDocumentReaderTest` | 7/7 ✅ | **PDF OCR** 读取与解析 |
| `SectionToDocumentConverterTest` | 2/2 ✅ | Tika/POI 文档切块 |
| `MarkdownElementExtractorTest` | 11/11 ✅ | Markdown 结构抽取 |
| `TokenLimitEnforcerTest`（"Embedding token 上限守卫测试"） | 6/6 ✅ | 切块 token 上限守卫 |
| `RagDataLoaderTest`（"RagDataLoader 单元测试"） | 10/10 ✅ | 文档导入装配 |
| `CorrectnessEvaluationTest` | 2/2 ✅（在线，avg 0.97） | **OpenAI-compatible 调用（SiliconFlow）** |
| `RagEvaluationTest` | 1/1 ✅（在线） | Ollama embedding + 检索评估 |
| `FaithfulnessEvaluationTest` | 1/3 ⚠️ 2 Error | 在线 LLM-as-a-Judge（外因，见下） |

**2 个 Error 的定性：外因，非迁移回归**

- 现象：`evaluateDiscriminationAbility`、`evaluateUnfaithfulAnswers` 抛
  `com.openai.errors.OpenAIIoException: Request failed`
  at `org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient.execute(...:119)`，
  根因为 `java.io.InterruptedIOException: timeout`（单次调用命中 60s 上限）。
- 同时日志证明迁移后的 bean 正确生效：
  `Initializing OpenAiChatModel with baseUrl: https://api.siliconflow.cn, model: Qwen/Qwen3-8B, temperature: 0.7`。
- **对照实验（决定性证据）**：用 `git worktree` 检出未修改的迁移前基线（HEAD `5e09f45`，Boot 3.5.15 / AI 1.1.8），在相同外部依赖下运行同一用例：

| 场景 | 结果 | 报错形态 | 调用点 |
|---|---|---|---|
| 迁移前基线 | ❌ Error | `RestClient Error while extracting response for type [OpenAiApi$ChatCompletion] and content type [application/octet-stream]` | `evaluateFaithfulness:223` |
| 迁移后 | ❌ Error | `OpenAIIoException: Request failed`（timeout / Canceled） | `evaluateFaithfulness:223` |

  → 同一用例、同一调用行在升级前后**均失败**，仅因 HTTP 客户端实现不同而报错形态不同。根因是评估模型 `THUDM/GLM-Z1-9B-0414`（推理模型）在 SiliconFlow 上游的响应延迟不稳定。
- 原生探针佐证（JDK 11 `HttpClient` 直连 `/v1/chat/completions`，绕开 Spring 与 SDK）：5 次调用 200 OK，耗时 4.6s / 4.8s / 4.8s / **21.3s** / 5.3s → 同一请求延迟可抖动 4 倍以上，长上下文场景突破 60s 属可预期。
- 超时口径一致性（OpenAI 链路）：迁移前 `RestClientCustomizer` read timeout = 60s；迁移后 `OpenAiChatOptions.timeout` = 60s。**该链路上游等待上限未变**。Ollama 链路另见 §11。

**验证命令**

```powershell
mvn clean compile
mvn test-compile
mvn test
mvn test "-Dtest=FaithfulnessEvaluationTest"    # 复现外因失败
git worktree add ..\sard-baseline-ctrl HEAD --detach
cd ..\sard-baseline-ctrl; mvn test "-Dtest=FaithfulnessEvaluationTest#evaluateUnfaithfulAnswers"
```

**回滚检查**：`git checkout -- src/test/java/com/redis/demo/spring/ai/`；对照 worktree 用完 `git worktree remove ..\sard-baseline-ctrl --force`（已清理，主工作树未受影响）。

---

## 7. 环境依赖导致的跳过 / 失败项（明确记录，未计入迁移通过）

| 编号 | 项目 | 状态 | 证据 | 需补充的验证 |
|---|---|---|---|---|
| ENV-1 | `RagDataLoaderIntegrationTest`（Testcontainers + `milvusdb/milvus:v2.6.7`） | **未执行** | 无 `docker` CLI；`testcontainers.properties` 指向 `tcp://localhost:2375` 但无守护进程；`mvn test -Dtest=RagDataLoaderIntegrationTest` → `Tests run: 0`；`target/surefire-reports` 无该类报告 | 在带 Docker 的 CI 执行 `mvn verify`（failsafe 包含 `**/*IntegrationTest.java`） |
| ENV-2 | `mvn clean verify` 整体（failsafe 阶段） | **未执行** | 同上，Docker 缺失会使容器启动失败 | CI 中 `mvn clean verify` |
| ENV-3 | Actuator 运行时冒烟（`/actuator/health`、`/actuator/prometheus`） | **未验证** | 本机 8080 未监听；按文档 §9「不能只依据属性文件能否解析来判断 Prometheus 导出已经启用」 | 启动应用后 `curl http://localhost:8080/actuator/health`、`.../actuator/prometheus` |
| ENV-4 | `FaithfulnessEvaluationTest` 2 个用例 | **外因失败**（非回归） | 迁移前基线在同一调用行同样失败（§6 对照实验） | 上游恢复后重跑，或为该用例加重试/显式条件跳过 |
| ENV-5 | React 前端构建与端到端链路 | **本轮未触发** | 按约束不升级 React；`frontend/build` 当前不存在，Maven resources 阶段复制 0 个文件 | 单独执行 `cd frontend; npm ci; npm run build` 后重启应用验证聊天 API |

> 说明：ENV-1/ENV-2 属**未验证**而非通过；ENV-4 属**外部服务不稳定**；本报告不将上述任何一项计作迁移通过。

---

## 8. 依赖树最终核对

```
org.springframework.boot:spring-boot:jar:4.2.0                     （单套，无 3.5.15 残留）
org.springframework.ai:*:jar:2.1.0                                 （全部 AI 模块统一 2.1.0）
io.milvus:milvus-sdk-java:jar:2.6.21                               （单套）
com.google.protobuf:protobuf-java:jar:4.29.3                        （去重后单条）
commons-io:commons-io:jar:2.15.1                                    （保守保留）
org.testcontainers:testcontainers:jar:1.21.4 / junit-jupiter:1.21.4  （显式钉版，版本一致）
tools.jackson.core:jackson-core:jar:3.1.8
tools.jackson.core:jackson-databind:jar:3.1.8
com.fasterxml.jackson.core:jackson-core:jar:2.21.8
com.fasterxml.jackson.core:jackson-databind:jar:2.21.8
com.fasterxml.jackson.core:jackson-annotations:jar:2.21
```

**Jackson 双栈解释（非缺陷）**：Boot 4.2 默认提供 Jackson 3（`tools.jackson`），**项目源码统一使用 Jackson 3**；Jackson 2 由 `com.openai:openai-java-core:4.101.0`（Spring AI 2.1.0 的官方 SDK 依赖）传递引入，**仅 SDK 内部序列化使用**，未泄漏到业务代码。符合迁移文档「不存在未解释的双栈」要求。

---

## 9. 迁移完成标准对照

| 完成标准 | 状态 | 依据 |
|---|---|---|
| Boot 与 Spring AI 版本一致且可解析 | ✅ | dependency:tree / effective-pom |
| `mvn clean verify` 成功，**或**所有环境依赖跳过项明确记录 | ⚠️ 部分 | 跳过项 ENV-1/ENV-2/ENV-3 已明确记录，未误报通过 |
| Jackson 2 / 3 依赖符合设计，无双栈未解释 | ✅ | §8 |
| `Document` API 与测试源码全部编译通过 | ✅ | `mvn test-compile` BUILD SUCCESS |
| Milvus 连接、写入、检索通过 | ✅ | 在线 count=94、HybridSearch 5/5、RagEvaluation 1/1 |
| BM25 Redis 历史数据能恢复 | ✅ | Bm25IndexRecovery 5/5（加载 94 篇） |
| Ollama 与 OpenAI-compatible 两种聊天模式通过 | ✅（OpenAI 在线通过；Ollama 见风险注） | RagControllerTest 7/7、Correctness 在线 avg 0.97 |
| HTTP 超时与异常 fallback 经过测试 | ✅ | fallback 单测覆盖；Ollama 隐式 10s 读超时回归已修复并实测验证（§11） |
| OCR、rerank、Tika/POI 文档处理通过 | ✅ | OCR 7/7、Rerank 6/6、Section 2/2 |
| 前端构建与后端聊天链路通过 | ⏸️ 未验证 | ENV-5 |
| Docker 运行配置区分容器内外地址 | ⏸️ 本轮范围外 | 按约束未改 Compose（文档亦要求单独提交） |
| README / Architecture / Compose 文档不再矛盾 | ⏸️ 本轮范围外 | 文档 §11/§12 P5 建议单独排期 |

---

## 10. 后续建议（不在本轮范围）

1. **CI 补验**：在带 Docker 的 runner 上跑 `mvn clean verify`，覆盖 failsafe 的 Testcontainers IT；确认 protobuf / commons-io 是否可解钉。
2. **运行时冒烟**：启动应用验证 `/actuator/health` 与 `/actuator/prometheus`，确认 `management.prometheus.metrics.export.enabled` 在 Boot 4.2 下确实生效。
3. **前端链路**：`cd frontend; npm ci; npm run build` 后重启应用，验证 Maven 复制静态资源与聊天 API 端到端。
4. **不稳定在线用例治理**：给 `FaithfulnessEvaluationTest` / `CorrectnessEvaluationTest` 增加重试或 `@EnabledIf` 条件，避免上游抖动污染回归结论。
5. **单独提交项**：Docker Compose 拓扑与容器内外地址区分、Pinecone 遗留属性清理、README/Architecture 过期描述更新。
6. **回滚方式**：本轮改动尚未提交，工作树回滚即可：

```powershell
git checkout -- pom.xml src/main/java/com/redis/demo/spring/ai/RagConfiguration.java `
  src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java `
  src/main/java/com/redis/demo/spring/ai/service/ `
  src/main/resources/application.properties `
  src/test/java/com/redis/demo/spring/ai/
```

若后续已提交，按文档 §13 使用 `git revert <migration-commit>`；本轮未改 Milvus collection schema、未改 Redis JSON 格式，故无需数据回滚。

---

## 11. 运行期发现的迁移回归：Boot 4.2 默认 RestClient 工厂打断 Ollama embedding（已修复）

### 11.1 现象

应用启动时 `RagDataLoader.run` → `vectorStore.add` → `OllamaApi.embed` 报错，进程直接退出：

```
org.springframework.web.client.ResourceAccessException: I/O error on POST request for "http://127.0.0.1:11434/api/embed": null
  at org.springframework.ai.ollama.api.OllamaApi.embed(OllamaApi.java:184) ~[spring-ai-ollama-2.1.0.jar:2.1.0]
  at com.redis.demo.spring.ai.RagDataLoader.run(RagDataLoader.java:89)
Caused by: java.io.IOException
  at org.springframework.http.client.ReactorClientHttpRequest.convertException(ReactorClientHttpRequest.java:154) ~[spring-web-7.1.0.jar:7.1.0]
Caused by: io.netty.handler.timeout.ReadTimeoutException
```

### 11.2 根因（探针实测，非推断）

写了一个临时探针测试（反射 + 本地 `com.sun.net.httpserver` 慢响应服务器），同一代码分别跑在迁移后与 `git worktree` 检出的迁移前基线上：

| 环境 | `RestClient.builder().build()` 实际工厂 | `readTimeout` 字段 | 对 90s 才响应的请求 |
|---|---|---|---|
| Boot 3.5.15 / Spring 6.1 | `JdkClientHttpRequestFactory` | `null` | **90.2s 正常完成**（不限读超时） |
| Boot 4.2.0 / Spring 7.1.0 | `ReactorClientHttpRequestFactory` | `null` | **10.6s 抛 `ReadTimeoutException`** |

- Boot 4.2 把 `RestClient.builder()` 的默认请求工厂从 JDK HttpClient 换为 **Reactor Netty**（classpath 上存在 `spring-boot-starter-webclient` 传递引入的 `reactor-netty-http:1.4.0` + `spring-webflux:7.1.0`），且即使工厂自身 `readTimeout` 字段为 `null`，仍附带约 **10s** 的有效读超时。
- `javap` 对比确认：`OllamaApi`（含 `restClient`/`webClient` 字段与构造签名）在 1.1.8 与 2.1.0 **结构完全一致**，`OllamaApi$Builder` 也不提供任何超时入参 → 差异不来自 Spring AI，而来自 Boot/Spring 的默认工厂选择。
- `OllamaApi.builder().baseUrl(...).build()` 用的是**静态** `RestClient.builder()`，**不经过容器里的 `RestClient.Builder` bean**，所以 `restClientCustomizer`（60s）对 Ollama 从来不生效——`git diff` 证实 `RagConfiguration.java` 本轮只改了一行 import。
- 因此 `spring.ai.ollama.read-timeout=60s` 此前只传给 `ModelManagementOptions`（模型管理），**从未作用于 HTTP 读超时**。

### 11.3 业务影响量化

本机 `bge-m3:latest`（1.08GB，CPU）实测：5 段短文本 **25.5s**（含模型加载）；40 段文本 **46.8s**；完整批量 embed **98.0s**。
→ **10s 不够，60s 也不够**，启动导入必定失败；修复前该分支在迁移前能跑通（无读超时），属真回归。

### 11.4 修复

| 文件 | 改动 |
|---|---|
| `util/OllamaRestClientFactory.java`（新增） | `restClientBuilder(connect, read)`：显式 `JdkClientHttpRequestFactory`。注意 javap 核对 7.1.0 该类**只有 `setReadTimeout(Duration)`、没有 `setConnectTimeout`**，连接超时必须设在 `java.net.http.HttpClient.Builder` 上 |
| `RagConfiguration.embeddingModel(...)` | 注入 `OllamaApi.builder().restClientBuilder(...)`；新增 `spring.ai.ollama.embedding.connect-timeout:10s` / `.read-timeout:300s` |
| `ChatModelConfiguration.ollamaApi(...)` | 同样注入，读超时沿用 `spring.ai.ollama.read-timeout=60s`，保持 60s &lt; 65s &lt; 90s 聊天超时层级 |
| `application.properties` | 上述两个 embedding 超时属性及说明注释 |

设计取摄：embedding 不在 `/chat` 请求路径上，放宽到 300s 不影响聊天侧超时层级；相比迁移前的「不限超时」，300s 仍是收紧而非放宽。

### 11.5 修复验证

| 探针用例 | 结果 |
|---|---|
| A：修复前构造（`OllamaApi.builder().build()`）+ 40 段 embed | **10.8s ABORTED**，`io.netty.handler.timeout.ReadTimeoutException` → **与线上报错一致，复现成功** |
| B：修复后 `RagConfiguration.embeddingModel(...)` + 同一批数据 | **98.0s OK**，40 × 1024 维向量 |
| C：显式工厂配 15s 读超时、打 90s 慢响应服务器 | **精确 15.0s** `java.net.http.HttpTimeoutException` → 显式工厂已接管，隐式 10s 不再生效 |

回归子集（11 个测试类）：`Tests run: 74, Failures: 0, Errors: 0, Skipped: 0`（探针测试类已删除，不在仓库）。

### 11.6 附带发现

- Milvus `paper_collection_vpdf` 当前 `rowCount=0`（早前为 94），因此本次启动进入了全量向量化分支（`checkDbCollectionRecords()` 返回 false）。修复后重启需等待完整导入完成。
- 其它 HTTP 路径不受该默认工厂变更影响：`RerankService`（自建 `SimpleClientHttpRequestFactory`，10s/20s）、`DeepSeekOcrDocumentReader`（`RestTemplateBuilder` 显式 30s/120s）、`FileDownloader` / `RAGFlowDocxParser`（`HttpURLConnection` 自有超时）。
- 遗留技术债（本轮不改）：`org.springframework.boot.restclient.RestTemplateBuilder` 在 Boot 4.2 已标记为过时且计划删除，`DeepSeekOcrDocumentReader` 编译有告警，建议后续单独迁移到 `RestClient`。

### 11.7 对本报告早期结论的更正

本报告初版 §4 / §6 中断定「超时行为上限未变」，该判断**只对 OpenAI 链路成立**；对 Ollama 链路是错的——迁移把 Ollama 的有效读超时从「不限」变成「约 10s」。该回归当时未被识别，因为：测试集里的向量化路径因 Milvus 已有 94 条数据而被 `checkDbCollectionRecords()` 跳过，全量 `mvn test` 根本没碰到这条链路。教训：**仅依赖单测通过不能断言运行时超时行为不变**，需显式实测默认 HTTP 工厂行为。

---

## 附录 A：本轮修改文件清单

**主源码 / 配置（8）**

- `pom.xml`
- `src/main/java/com/redis/demo/spring/ai/RagConfiguration.java`（含 §11 的 embedding 超时修复）
- `src/main/java/com/redis/demo/spring/ai/config/ChatModelConfiguration.java`（含 §11 的 ollamaApi 超时修复）
- `src/main/java/com/redis/demo/spring/ai/util/OllamaRestClientFactory.java`（**新增**，§11）
- `src/main/java/com/redis/demo/spring/ai/service/RerankService.java`
- `src/main/java/com/redis/demo/spring/ai/service/DeepSeekOcrDocumentReader.java`
- `src/main/java/com/redis/demo/spring/ai/service/BM25DocumentPersistenceService.java`
- `src/main/resources/application.properties`

**测试源码（5）**

- `src/test/java/com/redis/demo/spring/ai/RagControllerTest.java`
- `src/test/java/com/redis/demo/spring/ai/RagDataLoaderIntegrationTest.java`
- `src/test/java/com/redis/demo/spring/ai/evaluation/CorrectnessTestHelper.java`
- `src/test/java/com/redis/demo/spring/ai/evaluation/FaithfulnessTestHelper.java`
- `src/test/java/com/redis/demo/spring/ai/evaluation/RagEvaluationHelper.java`

**未改动（经目标 JAR 核验为必要保留）**

- `MilvusVectorStore.builder(client, embeddingModel)` 与 `MilvusServiceClient` 装配
- `OllamaEmbeddingModel` 四参构造
- 所有 Spring AI / POI `getText()` 调用
- `Document(String, Map)` 构造
- `spring.autoconfigure.exclude` 自动配置排除项
- `docker-compose.yml`、`Dockerfile`、React 前端、BM25 Redis 数据格式、外部模型服务商

## 附录 B：取证与验证产物

| 文件 | 用途 |
|---|---|
| `baseline-compile.log` | 迁移前基线编译通过（会话结束后已清理，结论见 §2） |
| `target-dep-tree.txt` / `full-dep-tree.txt` / `dep-tree-full2.txt` / `final-dep-tree.txt` | 目标依赖解析与版本一致性核对 |
| `effective-pom.xml` | surefire/failsafe 交由父 POM 管理（3.6.1）的证据 |
| `target-compile-1.log`、`target-testcompile.log`、`target-testcompile2.log` | 主/测试源码编译过程 |
| `target-unit-tests.log` | 6 类 39 个测试子集全通过 |
| `target-fulltest.log` | 全量 `mvn test`：80 / 0 failures / 2 errors |
| `faithfulness-rerun.log`、`faithfulness-rerun2.log` | 迁移后 2 个失败复现与归因 |
| `baseline-control-faithfulness.log` | **迁移前基线对照实验（§6 决定性证据）**：同一调用行在升级前同样失败 |
| `postfix-tests.log` | §11 修复后的 11 类 74 个测试回归结果 |
| `probe-boot42.log` | §11 默认工厂与超时探针首次取证 |
