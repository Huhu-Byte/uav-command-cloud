-- V15: 航线规划表（修复 MySQL 兼容性，H2 下 Hibernate 自动建表可正常工作，
-- 但切到 MySQL + ddl-auto=validate 时缺少此脚本会导致启动失败）
CREATE TABLE IF NOT EXISTS flight_routes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    area VARCHAR(60) NOT NULL,
    mode VARCHAR(20) NOT NULL,
    waypoints_json CLOB NOT NULL,
    created_by VARCHAR(80) NOT NULL,
    modified_by VARCHAR(80) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_in_tasks INT NOT NULL DEFAULT 0
);
