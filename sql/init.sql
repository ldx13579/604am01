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

-- 默认管理员账户 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (username, password_hash, display_name)
VALUES ('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Administrator');

INSERT INTO sys_user_role (user_id, role, environment, namespace)
VALUES (1, 'ADMIN', '*', '*');

-- ===================== 审计日志 =====================

CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50) NOT NULL,
    action          VARCHAR(30) NOT NULL COMMENT 'CREATE_CONFIG / UPDATE_CONFIG / DELETE_CONFIG / ROLLBACK / ...',
    resource_type   VARCHAR(30) NOT NULL COMMENT 'CONFIG / USER / ROLE / GRAYSCALE_RULE / VALIDATION_SCRIPT',
    resource_id     VARCHAR(100),
    environment     VARCHAR(20),
    namespace       VARCHAR(100),
    old_value       TEXT,
    new_value       TEXT,
    ip_address      VARCHAR(50),
    result          VARCHAR(10) NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS / FAILED',
    error_message   TEXT,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_action (action),
    INDEX idx_resource (resource_type, resource_id),
    INDEX idx_created_at (created_at),
    INDEX idx_env_ns (environment, namespace)
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
