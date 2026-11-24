# Docker 环境配置指南

## 测试容器问题解决方案

在运行集成测试时，您可能会遇到以下错误：

```
java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
```

这是因为TestContainers需要一个可用的Docker环境来创建和管理测试容器。

## 解决方案

### 1. 安装Docker

首先，您需要安装Docker：

- Windows: 安装 [Docker Desktop for Windows](https://www.docker.com/products/docker-desktop)
- Mac: 安装 [Docker Desktop for Mac](https://www.docker.com/products/docker-desktop)
- Linux: 根据您的发行版安装 [Docker Engine](https://docs.docker.com/engine/install/)

### 2. 确保Docker正在运行

安装后，确保Docker服务已启动：

- Windows/Mac: 启动Docker Desktop应用
- Linux: 运行 `sudo systemctl start docker`

### 3. 验证Docker安装

打开命令行终端，运行以下命令验证Docker是否正确安装和运行：

```bash
docker --version
docker info
```

### 4. TestContainers配置

如果Docker已正确安装但TestContainers仍无法连接，可以尝试以下配置：

1. 创建或编辑 `src/test/resources/testcontainers.properties` 文件：

```properties
# Docker主机配置
docker.host=tcp://localhost:2375
# 如果使用Docker Desktop，可能需要以下配置
# docker.host=npipe:////./pipe/docker_engine
```

2. 确保Docker API已启用：
   - Docker Desktop: 在设置中启用"Expose daemon on tcp://localhost:2375 without TLS"选项

### 5. 替代方案

如果无法配置Docker环境，可以使用以下替代方案：

1. 使用模拟测试：参考 `RagDataLoaderMockTest.java`
2. 禁用依赖Docker的测试：使用 `@Disabled` 注解

## Milvus 单机版部署（v2.6.7）

根目录的 `docker-compose.yml` 已内置 Milvus 单机版所需的三个容器，与 Redis、应用服务一起编排，无需额外的 compose 文件：

- `etcd`：`quay.io/coreos/etcd:v3.5.5` —— Milvus 元数据存储，不对外开端口
- `minio`：`minio/minio:RELEASE.2023-03-20T20-16-18Z` —— 对象存储，`9000` 为 S3 API、`9001` 为控制台
- `standalone`：`milvusdb/milvus:v2.6.7` —— Milvus 单机版，`19530` 为 gRPC、`9091` 为 metrics

该镜像版本与 `RagDataLoaderIntegrationTest` 中 Testcontainers 使用的镜像一致，也与应用实际依赖的 `io.milvus:milvus-sdk-java:2.6.21` 同属 2.6 系列。

### 1. 拉取基础镜像

直接从官方仓库拉取：

```bash
docker pull quay.io/coreos/etcd:v3.5.5
docker pull minio/minio:RELEASE.2023-03-20T20-16-18Z
docker pull milvusdb/milvus:v2.6.7
```

网络受限时，可改用国内镜像源拉取后 `docker tag` 回标准镜像名：

```bash
docker pull registry.cn-hangzhou.aliyuncs.com/megadotnet/quay.io.coreos.etcd:v3.5.5 && docker tag registry.cn-hangzhou.aliyuncs.com/megadotnet/quay.io.coreos.etcd:v3.5.5 quay.io/coreos/etcd:v3.5.5

docker pull registry.cn-hangzhou.aliyuncs.com/megadotnet/minio.minio:RELEASE.2023-03-20T20-16-18Z && docker tag registry.cn-hangzhou.aliyuncs.com/megadotnet/minio.minio:RELEASE.2023-03-20T20-16-18Z minio/minio:RELEASE.2023-03-20T20-16-18Z

docker pull registry.cn-hangzhou.aliyuncs.com/megadotnet/milvusdb.milvus:v2.6.7 && docker tag registry.cn-hangzhou.aliyuncs.com/megadotnet/milvusdb.milvus:v2.6.7 milvusdb/milvus:v2.6.7
```

### 2. 启动并等待就绪

```bash
docker compose up -d etcd minio standalone
docker compose ps
docker compose logs -f standalone
```

`standalone` 没有配置 healthcheck，`docker compose ps` 只会显示 `running`；请等到日志中 `19530` 端口开始监听（proxy 启动完成）后再启动应用，否则后端会报 `Failed to connect to Milvus`。

### 3. 应用侧连接配置

`RagConfiguration.milvusServiceClient(...)` 只读取 `spring.ai.vectorstore.milvus.client.host`，端口在代码内固定为 `19530`（`spring.ai.vectorstore.milvus.client.port` 目前对该 Bean 不生效）。`application.properties` 默认值为 `192.168.0.218`，按实际运行位置调整：

- 后端在宿主机跑 `mvn spring-boot:run`：`spring.ai.vectorstore.milvus.client.host=127.0.0.1`
- 后端在同一 compose 网络内：用服务名 `standalone`（compose 已通过 `SPRING_AI_VECTORSTORE_MILVUS_CLIENT_HOST=${MILVUS_HOST:-standalone}` 覆盖，无需改配置文件）
- 使用外部 Milvus：设为对应 IP/域名，或先 `export MILVUS_HOST=<地址>` 再 `docker compose up -d`

### 4. 数据卷与安全提示

- 数据落在 `${DOCKER_VOLUME_DIRECTORY:-.}/volumes/{etcd,minio,milvus}`，默认为仓库根目录下的 `volumes/`，已加入 `.gitignore`；要放到其他磁盘可先 `export DOCKER_VOLUME_DIRECTORY=/data/milvus-demo`
- MinIO 使用 `minioadmin`/`minioadmin` 默认凭据，仅适用于本地演示；非本地环境请修改 `docker-compose.yml` 中的 `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`，并避免将 `9000`/`9001` 直接暴露到公网

### 5. 停止与清理

```bash
# 仅停止，保留向量数据
docker compose stop standalone minio etcd

# 停止并删除容器（bind mount 数据仍保留在 volumes/ 下）
docker compose down
```

需要彻底重置向量库时，先 `docker compose down`，再手动清理 `volumes/milvus`、`volumes/minio`、`volumes/etcd`；下一次启动会重建 collection，应用启动时 `RagDataLoader` 会重新导入并向量化文档。

## 更多资源

- [TestContainers文档](https://www.testcontainers.org/)
- [Docker文档](https://docs.docker.com/)
- [Milvus 单机部署文档](https://milvus.io/docs/install_standalone-docker.md)