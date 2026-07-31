CREATE TABLE inspection_results (
    id BIGINT NOT NULL AUTO_INCREMENT,
    result_type VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    task_name VARCHAR(80) NOT NULL,
    device VARCHAR(120) NOT NULL,
    captured_at DATETIME NULL,
    location VARCHAR(160) NOT NULL,
    result_status VARCHAR(20) NOT NULL,
    PRIMARY KEY (id)
);
