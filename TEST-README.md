# 测试指南

## 测试类型

本项目包含两种类型的测试：

1. **模拟测试**：不依赖Docker环境，使用模拟对象进行测试
2. **集成测试**：需要Docker环境，使用TestContainers启动真实的Redis容器进行测试

## 如何运行测试

### 运行所有测试

```bash
mvn test
```

### 仅运行模拟测试

```bash
mvn test -Dtest=*MockTest
```

### 仅运行集成测试（需要Docker环境）

```bash
mvn test -Dtest=*IntegrationTest
```

## 测试配置

测试配置位于 `src/test/resources/application-test.yml` 文件中。

## BM25 索引运维修复与验证

混合检索（`hybrid.search.enabled=true`）下，BM25 全文索引基于内存 Lucene，并通过 Redis 持久化。
当出现「Redis 曾连接失败、现已恢复，且 Milvus 中已有数据」时，启动流程不会自动补建 BM25 索引
（`RagDataLoader.run()` 在 Milvus 已有数据时会提前返回），此时用以下两个运维接口处理。

### 接口一：状态自检 `GET /admin/bm25/status`

返回内存 / Redis / Milvus 三处计数与健康判定，用于定位「BM25 静默退化为纯向量」：

```bash
curl http://localhost:8080/admin/bm25/status
```

响应示例：

```json
{
  "hybridEnabled": true,
  "indexBuilt": false,
  "memCount": 0,
  "redisConfigured": true,
  "redisAvailable": true,
  "redisCount": 0,
  "milvusCount": 128,
  "healthy": false,
  "consistentWithMilvus": false,
  "recommendation": "BM25 index missing while Milvus has data; call POST /admin/bm25/rebuild"
}
```

字段含义：

| 字段 | 说明 |
| --- | --- |
| `hybridEnabled` | `hybrid.search.enabled` 是否启用（BM25 Bean 是否存在） |
| `indexBuilt` / `memCount` | 当前进程内存 BM25 索引是否已建、文档数 |
| `redisConfigured` / `redisAvailable` / `redisCount` | Redis 持久化是否配置、可用、已存文档数 |
| `milvusCount` | Milvus collection 文档数（查询失败时为 -1） |
| `healthy` | `indexBuilt` 且 `memCount > 0` |
| `consistentWithMilvus` | 内存计数与 Milvus 计数偏差是否在阈值内（±5%） |
| `recommendation` | 直接给出下一步动作建议 |

### 接口二：索引重建 `POST /admin/bm25/rebuild`

全量清空内存索引与 Redis 持久化后，从**原始数据源**重建 BM25 索引并重新持久化。
仅读取文本、不做 embedding、不写 Milvus，因此不会重复消耗向量资源或产生重复索引：

```bash
curl -X POST http://localhost:8080/admin/bm25/rebuild
```

响应示例：

```json
{ "success": true, "bm25DocumentCount": 128, "message": "BM25 index rebuilt" }
```

> 注意：rebuild 依赖 `classpath:/data/` 下的原始数据文件；请确保 `chunking.*` 配置与最初
> 写入 Milvus 时一致，否则 docId 漂移会影响 RRF 融合质量。

### 三种故障场景验证步骤

#### 场景一：正常恢复（Redis 中已有完整 BM25 数据）

1. 在 Redis 含完整 BM25 数据的情况下重启应用。
2. 观察日志：应出现 `Restored BM25 index from Redis with N documents`。
3. 调 `GET /admin/bm25/status`：预期 `indexBuilt=true`、`redisCount≈memCount≈milvusCount`、`consistentWithMilvus=true`、`recommendation="OK"`。
4. 此场景**无需**调用 rebuild。

#### 场景二：Redis 无历史 BM25 数据（Milvus 也为空）

1. 清空 Redis 的 BM25 key：`redis-cli --scan --pattern 'bm25:*' | xargs redis-cli del`。
2. 在 Milvus collection 为空时启动应用，`RagDataLoader.run()` 会加载数据并构建 BM25。
3. 观察日志：`Building BM25 index for hybrid search...` 与 `BM25 index built successfully with N documents`，且无 `skipping BM25 search`。
4. 调 `GET /admin/bm25/status`：预期 `indexBuilt=true`、三处计数一致、`recommendation="OK"`。

#### 场景三：Milvus 已有数据但 BM25 索引缺失（本运维重点场景）

1. 保持 Milvus 有数据，清空 Redis 的 BM25 key（同场景二第 1 步）。
2. 重启应用：日志会出现 `Embeddings already loaded ... Skipping`，且 BM25 检索打印 `BM25 index not built, skipping BM25 search` —— 这正是「启动不会自动补建」的根因。
3. 调 `GET /admin/bm25/status`：预期 `indexBuilt=false`、`memCount=0`、`milvusCount>0`、`recommendation` 提示需要 rebuild。
4. 调 `POST /admin/bm25/rebuild`：观察日志 `Manual BM25 index rebuild requested ...` 与 `BM25 index rebuilt with N documents`。
5. 再次调 `GET /admin/bm25/status`：预期 `healthy=true`、`consistentWithMilvus=true`、`recommendation="OK"`。
6. 发一次 `/retrieve` 混合查询，确认返回文档 `metadata.sources` 含 `"bm25"`，证明 BM25 已真正参与召回而非纯向量降级。

### 相关自动化测试

- `Bm25IndexRecoveryIntegrationTest`：串联真实 `RagDataLoader` + `HybridSearchService` + `BM25SearchService`（真实 Lucene），覆盖 rebuild 可用性、幂等性、空数据源、混合禁用与委托状态查询，**不依赖 Docker**。
- `RagControllerTest`：通过 `@WebMvcTest` + MockMvc 覆盖 `/admin/bm25/status` 与 `/admin/bm25/rebuild` 的 HTTP 契约。

```bash
mvn test -Dtest=Bm25IndexRecoveryIntegrationTest,RagControllerTest
```

## 常见问题

### 集成测试失败，提示"Could not find a valid Docker environment"

这表明TestContainers无法找到有效的Docker环境。请参考 `DOCKER-README.md` 文件了解如何解决此问题。

### 如何在没有Docker的环境中运行测试？

如果您无法配置Docker环境，可以：

1. 仅运行模拟测试：`mvn test -Dtest=*MockTest`
2. 或者使用提供的批处理脚本：`run-tests.bat`