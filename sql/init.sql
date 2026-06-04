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
