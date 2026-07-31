-- V9: 地图标注
CREATE TABLE IF NOT EXISTS map_elements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL DEFAULT 1,
    element_type VARCHAR(50) NOT NULL,
    title VARCHAR(300),
    coordinates TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
