CREATE DATABASE IF NOT EXISTS config_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE config_center;

CREATE TABLE config_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key      VARCHAR(255) NOT NULL,
    config_value    TEXT NOT NULL,
    environment     VARCHAR(20) NOT NULL COMMENT 'dev / test / prod',
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    description     VARCHAR(500) DEFAULT '',
    encrypted       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否加密存储',
    version         BIGINT NOT NULL DEFAULT 1,
    last_pulled_at  DATETIME DEFAULT NULL COMMENT '最近被客户端拉取时间',
    zombie          TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为僵尸配置',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_key_env_ns (config_key, environment, namespace)
) ENGINE=InnoDB;

CREATE TABLE config_version (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_item_id  BIGINT NOT NULL,
    config_key      VARCHAR(255) NOT NULL,
    config_value    TEXT NOT NULL,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    description     VARCHAR(500) DEFAULT '',
    version         BIGINT NOT NULL,
    operation       VARCHAR(20) NOT NULL COMMENT 'CREATE / UPDATE / ROLLBACK / DELETE',
    operator        VARCHAR(100) DEFAULT 'system',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_item_id (config_item_id),
    INDEX idx_env_ns_version (environment, namespace, version)
) ENGINE=InnoDB;

CREATE TABLE version_counter (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    current_version BIGINT NOT NULL DEFAULT 0,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_env_ns (environment, namespace)
) ENGINE=InnoDB;

INSERT INTO version_counter (environment, namespace, current_version) VALUES
('dev', 'default', 0),
('test', 'default', 0),
('prod', 'default', 0);

CREATE TABLE grayscale_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    rule_name       VARCHAR(200) NOT NULL,
    target_version  BIGINT NOT NULL COMMENT '灰度目标版本号',
    ip_list         TEXT NOT NULL COMMENT '灰度IP列表，逗号分隔',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / FULL_RELEASE / CANCELLED',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_env_ns (environment, namespace),
    INDEX idx_status (status)
) ENGINE=InnoDB;

CREATE TABLE client_instance (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_ip       VARCHAR(50) NOT NULL,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    current_version BIGINT NOT NULL DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ONLINE' COMMENT 'ONLINE / OFFLINE',
    last_heartbeat  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ip_env_ns (client_ip, environment, namespace),
    INDEX idx_env_ns (environment, namespace),
    INDEX idx_status (status)
) ENGINE=InnoDB;

-- ===================== RBAC 权限管理 =====================

CREATE TABLE sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    force_password_change TINYINT(1) NOT NULL DEFAULT 0 COMMENT '首次登录强制修改密码',
    failed_login_attempts INT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until    DATETIME COMMENT '账户锁定截止时间',
    last_login_at   DATETIME,
    last_login_ip   VARCHAR(50),
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE sys_user_role (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    role            VARCHAR(20) NOT NULL COMMENT 'ADMIN / DEVELOPER / VIEWER',
    environment     VARCHAR(20) NOT NULL COMMENT 'dev / test / prod / * for all',
    namespace       VARCHAR(100) NOT NULL DEFAULT '*' COMMENT 'namespace scope or * for all',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role_env_ns (user_id, role, environment, namespace),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB;

-- 默认管理员账户 (初始密码: Cfg$2024#Adm!nX9zK, BCrypt加密, 首次登录强制修改)
INSERT INTO sys_user (username, password_hash, display_name, force_password_change)
VALUES ('admin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'Administrator', 1);

INSERT INTO sys_user_role (user_id, role, environment, namespace)
VALUES (1, 'ADMIN', '*', '*');

-- ===================== 审计日志 =====================

CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) NOT NULL,
    action          VARCHAR(30) NOT NULL COMMENT 'CREATE_CONFIG / UPDATE_CONFIG / DELETE_CONFIG / ROLLBACK / LOGIN / CHANGE_PASSWORD / ...',
    resource_type   VARCHAR(30) NOT NULL COMMENT 'CONFIG / USER / ROLE / GRAYSCALE_RULE / VALIDATION_SCRIPT / SESSION',
    resource_id     VARCHAR(100),
    environment     VARCHAR(20),
    namespace       VARCHAR(100),
    old_value       TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(50),
    user_agent      VARCHAR(500),
    request_method  VARCHAR(10),
    request_uri     VARCHAR(500),
    session_id      VARCHAR(100),
    duration_ms     BIGINT COMMENT '操作耗时(毫秒)',
    result          VARCHAR(10) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILURE',
    error_message   TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_action (action),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_at (created_at),
    INDEX idx_env_ns (environment, namespace),
    INDEX idx_result (result)
) ENGINE=InnoDB;

-- ===================== 加密密钥管理 =====================

CREATE TABLE encryption_key (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment         VARCHAR(20) NOT NULL,
    namespace           VARCHAR(100) NOT NULL DEFAULT 'default',
    aes_key_encrypted   TEXT NOT NULL COMMENT 'AES-256 key encrypted with master key, Base64 encoded',
    rsa_public_key      TEXT NOT NULL COMMENT 'RSA public key PEM',
    key_version         INT NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_env_ns_ver (environment, namespace, key_version)
) ENGINE=InnoDB;

-- ===================== 配置校验脚本 =====================

CREATE TABLE validation_script (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    script_name     VARCHAR(200) NOT NULL,
    script_content  TEXT NOT NULL COMMENT 'JavaScript validation code',
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_env_ns_name (environment, namespace, script_name)
) ENGINE=InnoDB;

-- ===================== 客户端重连日志 =====================

CREATE TABLE client_reconnect_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    client_id       VARCHAR(100) NOT NULL COMMENT 'Unique identifier for the client',
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    event_type      VARCHAR(30) NOT NULL COMMENT 'DISCONNECT / RECONNECT_ATTEMPT / RECONNECT_SUCCESS / RECONNECT_FAILED',
    attempt_number  INT,
    version_before  BIGINT,
    version_after   BIGINT,
    error_message   TEXT,
    duration_ms     BIGINT COMMENT 'Time taken for reconnection',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_client_id (client_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- ===================== 接口认证豁免配置 =====================

CREATE TABLE security_public_path (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    path_pattern    VARCHAR(255) NOT NULL COMMENT '接口路径模式，支持Ant风格: /api/polling/**',
    description     VARCHAR(500) DEFAULT '' COMMENT '豁免原因说明',
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_path (path_pattern)
) ENGINE=InnoDB;

-- ===================== 运行时安全规则 =====================

CREATE TABLE security_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_type       VARCHAR(30) NOT NULL COMMENT 'BLOCKED_KEYWORD / SUSPICIOUS_PATTERN / BLOCKED_FUNCTION',
    rule_value      VARCHAR(500) NOT NULL COMMENT '规则内容：关键词或正则表达式',
    description     VARCHAR(500) DEFAULT '' COMMENT '规则说明',
    severity        VARCHAR(20) NOT NULL DEFAULT 'HIGH' COMMENT 'HIGH / MEDIUM / LOW',
    enabled         TINYINT(1) NOT NULL DEFAULT 1,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_type_value (rule_type, rule_value)
) ENGINE=InnoDB;

-- 预置安全规则（从硬编码迁移到数据库）
INSERT INTO security_rule (rule_type, rule_value, description, severity) VALUES
('BLOCKED_KEYWORD', 'java.lang.Runtime', '阻止访问Java运行时', 'HIGH'),
('BLOCKED_KEYWORD', 'java.lang.ProcessBuilder', '阻止进程创建', 'HIGH'),
('BLOCKED_KEYWORD', 'java.io.File', '阻止文件系统访问', 'HIGH'),
('BLOCKED_KEYWORD', 'java.net.Socket', '阻止网络Socket访问', 'HIGH'),
('BLOCKED_KEYWORD', 'java.net.URL', '阻止URL访问', 'HIGH'),
('BLOCKED_KEYWORD', 'java.net.HttpURLConnection', '阻止HTTP连接', 'HIGH'),
('BLOCKED_KEYWORD', 'Packages', '阻止Java包访问', 'HIGH'),
('BLOCKED_KEYWORD', 'java.lang.System', '阻止System类访问', 'HIGH'),
('BLOCKED_KEYWORD', 'java.lang.Thread', '阻止线程操作', 'HIGH'),
('BLOCKED_KEYWORD', 'eval(', '阻止动态代码执行', 'HIGH'),
('BLOCKED_KEYWORD', 'Function(', '阻止动态函数创建', 'HIGH'),
('BLOCKED_KEYWORD', 'new Function', '阻止动态函数创建', 'HIGH'),
('BLOCKED_KEYWORD', 'require(', '阻止模块加载', 'MEDIUM'),
('BLOCKED_KEYWORD', 'import(', '阻止动态导入', 'MEDIUM'),
('BLOCKED_KEYWORD', 'globalThis.Deno', '阻止Deno运行时访问', 'MEDIUM'),
('BLOCKED_KEYWORD', 'globalThis.process', '阻止Node进程访问', 'MEDIUM'),
('SUSPICIOUS_PATTERN', '(\\bwhile\\s*\\(\\s*true\\s*\\))', '检测无限while循环', 'HIGH'),
('SUSPICIOUS_PATTERN', '(\\bfor\\s*\\(\\s*;\\s*;)', '检测无限for循环', 'HIGH'),
('SUSPICIOUS_PATTERN', '(\\bProcess\\b)', '检测进程关键词', 'MEDIUM'),
('SUSPICIOUS_PATTERN', '(\\bexec\\s*\\()', '检测exec调用', 'HIGH'),
('SUSPICIOUS_PATTERN', '(\\b__proto__\\b)', '检测原型链操纵', 'HIGH'),
('SUSPICIOUS_PATTERN', '(\\bconstructor\\b\\s*\\[)', '检测constructor利用', 'HIGH');

-- ===================== 配置拉取记录 =====================

CREATE TABLE config_pull_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_item_id  BIGINT NOT NULL,
    client_ip       VARCHAR(50) NOT NULL,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    pulled_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_config_item (config_item_id),
    INDEX idx_pulled_at (pulled_at)
) ENGINE=InnoDB;

-- ===================== 配置变更测试日志 =====================

CREATE TABLE config_change_test_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_item_id  BIGINT NOT NULL,
    config_key      VARCHAR(255) NOT NULL,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    new_value       TEXT NOT NULL,
    test_result     VARCHAR(20) NOT NULL COMMENT 'PASS / FAIL / ERROR',
    error_message   TEXT,
    rolled_back     TINYINT(1) NOT NULL DEFAULT 0,
    rollback_version BIGINT COMMENT '回滚到的版本号',
    duration_ms     BIGINT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_config_item (config_item_id),
    INDEX idx_result (test_result),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;

-- ===================== 告警规则与历史 =====================

CREATE TABLE alert_rule (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name           VARCHAR(200) NOT NULL,
    metric_name         VARCHAR(100) NOT NULL COMMENT '监控指标名: polling.active_clients / zombie.count / polling.failure_rate',
    operator            VARCHAR(20) NOT NULL COMMENT 'GT / GTE / LT / LTE / EQ / NEQ',
    threshold           DOUBLE NOT NULL,
    severity            VARCHAR(20) NOT NULL DEFAULT 'WARNING' COMMENT 'CRITICAL / WARNING / INFO',
    notify_channels     VARCHAR(500) NOT NULL DEFAULT 'LOG' COMMENT '通知渠道: LOG,WEBHOOK,EMAIL',
    webhook_url         VARCHAR(500) COMMENT 'Webhook推送地址',
    email_to            VARCHAR(500) COMMENT '邮件接收人,逗号分隔',
    enabled             TINYINT(1) NOT NULL DEFAULT 1,
    cooldown_minutes    INT NOT NULL DEFAULT 5 COMMENT '告警冷却时间(分钟)',
    last_triggered_at   DATETIME,
    trigger_count       BIGINT NOT NULL DEFAULT 0,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE alert_history (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_rule_id       BIGINT NOT NULL,
    rule_name           VARCHAR(200) NOT NULL,
    metric_name         VARCHAR(100) NOT NULL,
    metric_value        DOUBLE NOT NULL,
    threshold           DOUBLE NOT NULL,
    severity            VARCHAR(20) NOT NULL,
    notify_channels     VARCHAR(500),
    notify_status       VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / PARTIAL_FAILURE / FAILED',
    error_message       TEXT,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rule_id (alert_rule_id),
    INDEX idx_created_at (created_at),
    INDEX idx_severity (severity)
) ENGINE=InnoDB;

INSERT INTO alert_rule (rule_name, metric_name, operator, threshold, severity, notify_channels, cooldown_minutes) VALUES
('活跃客户端过多', 'polling.active_clients', 'GT', 200, 'WARNING', 'LOG,WEBHOOK', 10),
('僵尸配置过多', 'zombie.count', 'GT', 20, 'WARNING', 'LOG', 60),
('拉取失败率过高', 'polling.failure_rate', 'GT', 0.1, 'CRITICAL', 'LOG,WEBHOOK,EMAIL', 5);

-- ===================== 僵尸配置清理历史 =====================

CREATE TABLE zombie_cleanup_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_item_id  BIGINT NOT NULL,
    config_key      VARCHAR(255) NOT NULL,
    environment     VARCHAR(20) NOT NULL,
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    last_pulled_at  DATETIME,
    cleanup_action  VARCHAR(20) NOT NULL COMMENT 'ARCHIVED / DELETED',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_env_ns (environment, namespace),
    INDEX idx_cleanup_at (created_at)
) ENGINE=InnoDB;

-- ===================== 告警升级策略 =====================

CREATE TABLE alert_escalation_policy (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_name              VARCHAR(200) NOT NULL,
    severity                 VARCHAR(20) NOT NULL COMMENT 'CRITICAL / WARNING / INFO',
    role_group               VARCHAR(50) NOT NULL COMMENT '角色组: OPS_ONCALL / TEAM_LEAD / DEVELOPER',
    escalation_level         INT NOT NULL COMMENT '升级层级，从1开始',
    escalation_delay_minutes INT NOT NULL DEFAULT 0 COMMENT '升级前等待时间',
    notify_channels          VARCHAR(500) NOT NULL COMMENT '通知渠道: LOG,WEBHOOK,EMAIL,SMS',
    enabled                  TINYINT(1) NOT NULL DEFAULT 1,
    created_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_severity_level (severity, escalation_level)
) ENGINE=InnoDB;

CREATE TABLE alert_subscriber (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(100) NOT NULL,
    role_group          VARCHAR(50) NOT NULL COMMENT '角色组: OPS_ONCALL / TEAM_LEAD / DEVELOPER',
    email               VARCHAR(200),
    webhook_url         VARCHAR(500),
    phone_number        VARCHAR(20),
    preferred_channels  VARCHAR(200) NOT NULL DEFAULT 'LOG' COMMENT '偏好渠道: LOG,EMAIL,WEBHOOK,SMS',
    quiet_hours_start   VARCHAR(5) COMMENT '免打扰开始时间 HH:mm',
    quiet_hours_end     VARCHAR(5) COMMENT '免打扰结束时间 HH:mm',
    enabled             TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role_group (role_group)
) ENGINE=InnoDB;

-- 预置升级策略: INFO→DEVELOPER, WARNING→TEAM_LEAD, CRITICAL→OPS_ONCALL
INSERT INTO alert_escalation_policy (policy_name, severity, role_group, escalation_level, escalation_delay_minutes, notify_channels) VALUES
('INFO级-开发人员通知', 'INFO', 'DEVELOPER', 1, 0, 'LOG,EMAIL'),
('WARNING级-团队负责人通知', 'WARNING', 'TEAM_LEAD', 1, 0, 'LOG,EMAIL,WEBHOOK'),
('WARNING级-升级到运维', 'WARNING', 'OPS_ONCALL', 2, 15, 'LOG,EMAIL,WEBHOOK,SMS'),
('CRITICAL级-运维值班通知', 'CRITICAL', 'OPS_ONCALL', 1, 0, 'LOG,EMAIL,WEBHOOK,SMS'),
('CRITICAL级-升级到团队负责人', 'CRITICAL', 'TEAM_LEAD', 2, 5, 'LOG,EMAIL,WEBHOOK');

-- 预置订阅人
INSERT INTO alert_subscriber (name, role_group, email, preferred_channels) VALUES
('开发人员A', 'DEVELOPER', 'dev-a@example.com', 'LOG,EMAIL'),
('开发人员B', 'DEVELOPER', 'dev-b@example.com', 'LOG,EMAIL'),
('团队负责人', 'TEAM_LEAD', 'lead@example.com', 'LOG,EMAIL,WEBHOOK'),
('运维值班-张工', 'OPS_ONCALL', 'ops-zhang@example.com', 'LOG,EMAIL,WEBHOOK,SMS'),
('运维值班-李工', 'OPS_ONCALL', 'ops-li@example.com', 'LOG,EMAIL,WEBHOOK,SMS');

-- ===================== 清理策略组合规则 =====================

CREATE TABLE cleanup_policy (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    policy_name         VARCHAR(200) NOT NULL,
    environment         VARCHAR(20) COMMENT '匹配的环境，NULL表示所有',
    namespace           VARCHAR(100) COMMENT '匹配的命名空间，NULL表示所有',
    key_pattern         VARCHAR(200) COMMENT '配置键匹配模式（支持*通配符）',
    min_age_days        INT COMMENT '最小未使用天数',
    max_value_size_bytes INT COMMENT '配置值超过此大小才匹配',
    min_value_size_bytes INT COMMENT '配置值低于此大小才匹配',
    require_zombie      TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否要求已标记为僵尸',
    condition_logic     VARCHAR(10) NOT NULL DEFAULT 'AND' COMMENT 'AND: 所有条件都满足 / OR: 任一条件满足',
    cleanup_action      VARCHAR(20) NOT NULL DEFAULT 'ARCHIVED' COMMENT 'ARCHIVED / DELETED',
    priority            INT NOT NULL DEFAULT 0 COMMENT '优先级，高优先匹配',
    enabled             TINYINT(1) NOT NULL DEFAULT 1,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 预置清理策略
INSERT INTO cleanup_policy (policy_name, environment, key_pattern, min_age_days, require_zombie, condition_logic, cleanup_action, priority) VALUES
('开发环境90天未用配置归档', 'dev', NULL, 90, 1, 'AND', 'ARCHIVED', 10),
('临时配置60天清理', NULL, 'tmp.*', 60, 0, 'AND', 'DELETED', 20),
('测试环境大配置清理', 'test', NULL, 30, 1, 'AND', 'DELETED', 5);

-- ===================== 测试结果智能分析 =====================

CREATE TABLE test_analysis_result (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    test_log_id         BIGINT NOT NULL,
    problem_category    VARCHAR(50) NOT NULL COMMENT 'VALIDATION_ERROR / FORMAT_ERROR / OVERFLOW_ERROR / ENCODING_ERROR / TIMEOUT_ERROR / SCRIPT_ERROR / SECURITY_ERROR / DEPENDENCY_ERROR / INFRASTRUCTURE_ERROR / UNKNOWN',
    root_cause          VARCHAR(500) COMMENT '推测的根因',
    suggestion          VARCHAR(1000) COMMENT '修复建议',
    confidence          DOUBLE NOT NULL COMMENT '置信度 0.0~1.0',
    matched_pattern     VARCHAR(200) COMMENT '匹配的分析规则名称',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_test_log (test_log_id),
    INDEX idx_category (problem_category),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB;
