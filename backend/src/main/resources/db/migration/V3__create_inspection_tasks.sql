CREATE TABLE inspection_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    route VARCHAR(120) NOT NULL,
    device VARCHAR(120) NOT NULL,
    task_status VARCHAR(20) NOT NULL,
    progress INT NOT NULL,
    scheduled_at DATETIME NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    operator VARCHAR(80) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id)
);
