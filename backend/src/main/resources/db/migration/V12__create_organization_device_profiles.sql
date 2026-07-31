-- V12: 组织设备待绑定资料
CREATE TABLE IF NOT EXISTS organization_device_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(100) NOT NULL,
    organization_name VARCHAR(200) NOT NULL,
    master_sn VARCHAR(100),
    nameplate_sn VARCHAR(100),
    display_name VARCHAR(200),
    device_type VARCHAR(50),
    status VARCHAR(50) DEFAULT '待绑定',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
