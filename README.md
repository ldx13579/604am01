# 分布式配置中心与动态热更新系统

一个基于 Spring Boot + Vue3 的分布式配置中心，支持多环境配置管理、版本追踪、一键回滚和 HTTP 长轮询热更新。

## 系统架构

```
┌─────────────┐       ┌─────────────────┐       ┌──────────┐
│  Vue3 前端   │──────▶│  Spring Boot    │──────▶│  MySQL   │
│  Element Plus│  HTTP │  配置服务端      │  JPA  │  8.0     │
└─────────────┘       └────────┬────────┘       └──────────┘
                               │
                      长轮询(30s) │
                               ▼
                      ┌─────────────────┐
                      │  Config Client  │
                      │  (SDK)          │
                      └─────────────────┘
```

## 核心特性

- **多环境管理**: 支持 dev/test/prod 环境隔离
- **版本追踪**: 每次配置变更自动生成版本号，完整审计日志
- **一键回滚**: 支持回滚到任意历史版本
- **长轮询热更新**: 客户端通过 HTTP 长轮询实时感知配置变更
- **即时推送**: 服务端配置变更时立即通知所有等待的客户端
- **Docker 一键部署**: `docker-compose up` 即可运行完整系统

## 技术栈

| 组件 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2, Spring Data JPA, Java 17 |
| 数据库 | MySQL 8.0 |
| 前端 | Vue 3, Element Plus, Vite 5, Axios |
| 部署 | Docker, Docker Compose, Nginx |

## 快速开始

### Docker Compose 一键启动

```bash
docker-compose up --build
```

启动后访问:
- 前端管理界面: http://localhost
- 后端 API: http://localhost:8080/api

### 本地开发

**后端:**
```bash
cd config-server
mvn spring-boot:run
```

**前端:**
```bash
cd config-frontend
npm install
npm run dev
```

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/configs?env={env}&ns={ns} | 获取配置列表 |
| POST | /api/configs | 创建配置项 |
| PUT | /api/configs/{id} | 更新配置值 |
| DELETE | /api/configs/{id} | 删除配置项 |
| GET | /api/configs/{id}/versions | 获取版本历史 |
| POST | /api/configs/{id}/rollback?targetVersion={v} | 回滚到指定版本 |
| GET | /api/polling?env={env}&ns={ns}&clientVersion={v} | 长轮询接口 |
| GET | /api/version?env={env}&ns={ns} | 获取当前版本号 |

## 客户端 SDK 使用

```java
@Autowired
private ConfigClient configClient;

// 获取配置
String value = configClient.getConfig("app.name");

// 监听变更
configClient.addListener((configs, version) -> {
    System.out.println("Config updated to version " + version);
});
```

## 数据库设计

- **config_item**: 当前生效的配置项
- **config_version**: 版本历史（不可变审计日志）
- **version_counter**: 全局版本计数器（per env + namespace）

## 运行测试

```bash
cd config-server
mvn test
```

## 项目结构

```
├── docker-compose.yml          # Docker 编排
├── sql/init.sql                # 数据库初始化脚本
├── config-server/              # Spring Boot 后端
├── config-client/              # 客户端 SDK
└── config-frontend/            # Vue3 前端
```

## License

MIT
