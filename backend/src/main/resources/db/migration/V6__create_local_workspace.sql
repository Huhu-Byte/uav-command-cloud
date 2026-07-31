-- V6: 本地工作空间
CREATE TABLE IF NOT EXISTS local_workspace (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL DEFAULT '本机演示工作空间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO local_workspace (name) VALUES ('本机演示工作空间');
