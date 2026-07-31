CREATE TABLE operation_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator VARCHAR(80) NOT NULL,
    operated_at DATETIME NOT NULL,
    action VARCHAR(80) NOT NULL,
    result VARCHAR(20) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    PRIMARY KEY (id)
);
