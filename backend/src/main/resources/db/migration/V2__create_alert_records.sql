CREATE TABLE alert_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    handler VARCHAR(80) NOT NULL,
    handled_at DATETIME NOT NULL,
    result VARCHAR(500) NOT NULL,
    alert_level VARCHAR(20) NOT NULL,
    title VARCHAR(120) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    occurred_at VARCHAR(20) NOT NULL,
    device VARCHAR(120) NOT NULL,
    PRIMARY KEY (id)
);
