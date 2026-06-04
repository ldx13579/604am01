CREATE DATABASE IF NOT EXISTS config_center DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE config_center;

CREATE TABLE config_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key      VARCHAR(255) NOT NULL,
    config_value    TEXT NOT NULL,
    environment     VARCHAR(20) NOT NULL COMMENT 'dev / test / prod',
    namespace       VARCHAR(100) NOT NULL DEFAULT 'default',
    description     VARCHAR(500) DEFAULT '',
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
