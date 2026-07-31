package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operation_records")
public class OperationRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String operator;

    @Column(nullable = false)
    private LocalDateTime operatedAt;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(nullable = false, length = 500)
    private String reason;

    protected OperationRecordEntity() {
    }

    public OperationRecordEntity(String operator, LocalDateTime operatedAt, String action, String result, String reason) {
        this.operator = operator;
        this.operatedAt = operatedAt;
        this.action = action;
        this.result = result;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public String getOperator() {
        return operator;
    }

    public LocalDateTime getOperatedAt() {
        return operatedAt;
    }

    public String getAction() {
        return action;
    }

    public String getResult() {
        return result;
    }

    public String getReason() {
        return reason;
    }
}
