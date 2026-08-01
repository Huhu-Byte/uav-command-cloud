CREATE TABLE media_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flight_id VARCHAR(64),
    file_name VARCHAR(200) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    object_key VARCHAR(500) NOT NULL,
    download_url VARCHAR(1000),
    gateway_sn VARCHAR(100),
    task_name VARCHAR(80),
    captured_at DATETIME,
    uploaded_at DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '已上传',
    PRIMARY KEY (id)
);
