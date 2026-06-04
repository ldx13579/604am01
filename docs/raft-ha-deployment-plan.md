# 基于 Raft 协议的高可用部署方案

## 1. 架构概述

采用 3 节点 Raft 集群实现配置中心服务的强一致性高可用部署。

```
                    ┌─────────────┐
                    │  Nginx LB   │
                    │  (端口 80)   │
                    └──────┬──────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
    ┌───────▼──────┐ ┌────▼─────────┐ ┌──▼────────────┐
    │ config-server│ │ config-server│ │ config-server │
    │   Node-1     │ │   Node-2     │ │   Node-3      │
    │ (Leader)     │ │ (Follower)   │ │ (Follower)    │
    │ :8080/:9000  │ │ :8080/:9000  │ │ :8080/:9000   │
    └───────┬──────┘ └──────┬──────┘ └───────┬───────┘
            │    Raft 共识 (gRPC:9000)        │
            ├────────────────┼────────────────┤
            │              │              │
    ┌───────▼──────────────▼──────────────▼───────┐
    │            MySQL Group Replication            │
    │      (3节点, 多主/单主模式)                    │
    └──────────────────────────────────────────────┘
            │
    ┌───────▼──────────────────────────────────────┐
    │          RabbitMQ Mirror Queue Cluster        │
    │      (3节点, 镜像队列保证消息可靠性)           │
    └──────────────────────────────────────────────┘
```

## 2. 技术选型

| 组件 | 技术 | 理由 |
|------|------|------|
| Raft 共识 | Apache Ratis 2.5.x | Apache 开源、Java 原生、与 Spring Boot 易集成 |
| 负载均衡 | Nginx | 已有 Docker 部署经验，支持健康检查和会话保持 |
| 数据库 HA | MySQL Group Replication | 原生支持，无需额外组件 |
| 消息队列 HA | RabbitMQ Mirrored Queues | 已使用 RabbitMQ，镜像队列保证消息不丢失 |

## 3. 写入路径 (Write Path)

```
Client → Nginx → Any Node → Forward to Leader → Raft Propose
    → Log Replication (Majority Quorum)
    → Commit → Apply to MySQL → Notify RabbitMQ → Response
```

1. 客户端写请求通过 Nginx 到达任意节点
2. 非 Leader 节点将写请求转发给当前 Leader
3. Leader 将操作写入 Raft 日志并复制到多数节点
4. 达成多数共识后提交，应用到 MySQL
5. 通过 RabbitMQ 广播变更通知到所有节点的长轮询客户端

## 4. 读取路径 (Read Path)

```
Client → Nginx → Any Node → Local Read → Response
```

- 配置读取（长轮询）由任意节点直接服务
- 版本号最终一致性，客户端可容忍短暂延迟（< 100ms）
- 长轮询超时机制保证客户端最终获取最新配置

## 5. 实现要点

### 5.1 Maven 依赖

```xml
<dependency>
    <groupId>org.apache.ratis</groupId>
    <artifactId>ratis-server</artifactId>
    <version>2.5.1</version>
</dependency>
<dependency>
    <groupId>org.apache.ratis</groupId>
    <artifactId>ratis-grpc</artifactId>
    <version>2.5.1</version>
</dependency>
```

### 5.2 Raft 状态机

```java
public class ConfigRaftStateMachine extends BaseStateMachine {

    @Override
    public CompletableFuture<Message> applyTransaction(TransactionContext trx) {
        // 解析日志条目，执行配置写入操作
        // CREATE / UPDATE / DELETE / ROLLBACK
        ConfigChangeCommand cmd = deserialize(trx.getLogEntry());
        configService.applyChange(cmd);
        return CompletableFuture.completedFuture(Message.EMPTY);
    }

    @Override
    public CompletableFuture<Message> query(Message request) {
        // 读操作直接查询本地数据库
        return CompletableFuture.completedFuture(queryLocal(request));
    }
}
```

### 5.3 Leader 转发

```java
@Aspect
@Component
public class RaftLeaderForwardAspect {

    @Around("@annotation(WriteOperation)")
    public Object forwardToLeader(ProceedingJoinPoint pjp) {
        if (raftServer.isLeader()) {
            return pjp.proceed(); // 本地执行
        }
        // 转发到 Leader 节点
        return httpClient.forward(raftServer.getLeaderAddress(), request);
    }
}
```

## 6. Docker Compose 高可用部署

```yaml
version: '3.8'

services:
  nginx-lb:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx-ha.conf:/etc/nginx/nginx.conf
    depends_on:
      - config-server-1
      - config-server-2
      - config-server-3

  config-server-1:
    build: ./config-server
    environment:
      RAFT_NODE_ID: node-1
      RAFT_PORT: 9000
      RAFT_PEERS: node-1:config-server-1:9000,node-2:config-server-2:9000,node-3:config-server-3:9000
      SERVER_PORT: 8080
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-1:3306/config_center
    ports:
      - "8081:8080"
      - "9001:9000"

  config-server-2:
    build: ./config-server
    environment:
      RAFT_NODE_ID: node-2
      RAFT_PORT: 9000
      RAFT_PEERS: node-1:config-server-1:9000,node-2:config-server-2:9000,node-3:config-server-3:9000
      SERVER_PORT: 8080
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-2:3306/config_center
    ports:
      - "8082:8080"
      - "9002:9000"

  config-server-3:
    build: ./config-server
    environment:
      RAFT_NODE_ID: node-3
      RAFT_PORT: 9000
      RAFT_PEERS: node-1:config-server-1:9000,node-2:config-server-2:9000,node-3:config-server-3:9000
      SERVER_PORT: 8080
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql-3:3306/config_center
    ports:
      - "8083:8080"
      - "9003:9000"

  mysql-1:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: config_center
    command: >
      --server-id=1
      --log-bin=mysql-bin
      --gtid-mode=ON
      --enforce-gtid-consistency=ON
      --group-replication-group-name="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
      --group-replication-start-on-boot=ON
      --group-replication-local-address="mysql-1:33061"
      --group-replication-group-seeds="mysql-1:33061,mysql-2:33061,mysql-3:33061"
      --group-replication-bootstrap-group=ON

  mysql-2:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: config_center
    command: >
      --server-id=2
      --log-bin=mysql-bin
      --gtid-mode=ON
      --enforce-gtid-consistency=ON
      --group-replication-group-name="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
      --group-replication-start-on-boot=ON
      --group-replication-local-address="mysql-2:33061"
      --group-replication-group-seeds="mysql-1:33061,mysql-2:33061,mysql-3:33061"

  mysql-3:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: config_center
    command: >
      --server-id=3
      --log-bin=mysql-bin
      --gtid-mode=ON
      --enforce-gtid-consistency=ON
      --group-replication-group-name="aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
      --group-replication-start-on-boot=ON
      --group-replication-local-address="mysql-3:33061"
      --group-replication-group-seeds="mysql-1:33061,mysql-2:33061,mysql-3:33061"

  rabbitmq-1:
    image: rabbitmq:3.12-management
    hostname: rabbit-1
    environment:
      RABBITMQ_ERLANG_COOKIE: 'config-center-cluster-cookie'
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest

  rabbitmq-2:
    image: rabbitmq:3.12-management
    hostname: rabbit-2
    environment:
      RABBITMQ_ERLANG_COOKIE: 'config-center-cluster-cookie'
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest

  rabbitmq-3:
    image: rabbitmq:3.12-management
    hostname: rabbit-3
    environment:
      RABBITMQ_ERLANG_COOKIE: 'config-center-cluster-cookie'
      RABBITMQ_DEFAULT_USER: guest
      RABBITMQ_DEFAULT_PASS: guest
```

## 7. Nginx 负载均衡配置

```nginx
upstream config_cluster {
    server config-server-1:8080;
    server config-server-2:8080;
    server config-server-3:8080;
}

server {
    listen 80;

    location / {
        proxy_pass http://config_cluster;
        proxy_read_timeout 35s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /api/configs {
        # 写操作路由到 Leader（通过自定义 header 或 Raft 内部转发）
        proxy_pass http://config_cluster;
        proxy_next_upstream error timeout http_502;
    }
}
```

## 8. 故障场景与恢复

| 故障场景 | 影响 | 恢复时间 | 处理方式 |
|----------|------|----------|----------|
| Leader 宕机 | 写操作暂停 | ~2-5s | Raft 自动选举新 Leader |
| Follower 宕机 | 无影响（仍有多数派） | 即时 | 读流量自动分配到其他节点 |
| 2 节点同时宕机 | 集群不可用 | 需人工恢复 | 失去多数派，无法选举 |
| 网络分区 | 少数派无法写入 | 网络恢复后自动同步 | 多数派侧继续服务 |
| MySQL 主节点故障 | Group Replication 自动切换 | ~5-10s | 其他节点自动提升 |
| RabbitMQ 节点故障 | 镜像队列接管 | ~1-2s | 消息不丢失 |

## 9. 性能影响评估

| 指标 | 单节点 | 3 节点 Raft | 影响说明 |
|------|--------|-------------|----------|
| 写入延迟 | ~5ms | ~7-10ms | +2-5ms Raft 日志复制开销 |
| 读取延迟 | ~3ms | ~3ms | 本地读取，无额外开销 |
| 读取吞吐量 | 1x | ~3x | 三节点分担读请求 |
| 写入吞吐量 | 1x | ~0.8x | Leader 单点写入 + 共识开销 |
| 长轮询支持 | ~500 clients | ~1500 clients | 三节点分担连接 |

## 10. 容量规划 (基于压测结果)

基于 100 客户端并发压测数据推算：

| 客户端规模 | 推荐节点数 | CPU 配置 | 内存配置 | 网络 |
|-----------|-----------|----------|----------|------|
| < 200 | 3 节点 | 2 核 | 4 GB | 千兆 |
| 200-500 | 3 节点 | 4 核 | 8 GB | 千兆 |
| 500-2000 | 5 节点 | 8 核 | 16 GB | 万兆 |
| > 2000 | 5+ 节点 + 只读副本 | 16 核 | 32 GB | 万兆 |

## 11. 监控告警

结合已实现的 Prometheus + Grafana 监控：

- **Raft Leader 切换告警**: `changes(raft_leader_id[5m]) > 0`
- **节点离线告警**: `up{job="config-server"} == 0`
- **写入延迟告警**: `histogram_quantile(0.99, rate(config_polling_latency_seconds_bucket[5m])) > 1`
- **集群脑裂检测**: `count(raft_role{role="leader"}) > 1`

## 12. 部署步骤

1. 准备 3 台服务器（最小化部署，物理机或云主机）
2. 部署 MySQL Group Replication 集群
3. 部署 RabbitMQ 镜像集群
4. 部署 3 个 config-server 节点（配置 Raft peer 列表）
5. 部署 Nginx 负载均衡
6. 部署 Prometheus + Grafana 监控
7. 验证: Leader 选举、配置写入、故障切换
8. 压测: 逐步增加客户端数量，确认性能达标
