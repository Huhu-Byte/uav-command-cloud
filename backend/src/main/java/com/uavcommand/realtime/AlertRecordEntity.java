package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "alert_records")
public class AlertRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String handler;

    @Column(nullable = false)
    private LocalDateTime handledAt;

    @Column(nullable = false, length = 500)
    private String result;

    @Column(nullable = false, length = 20)
    private String alertLevel;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false, length = 20)
    private String occurredAt;

    @Column(nullable = false, length = 120)
    private String device;

    protected AlertRecordEntity() {
    }

    public AlertRecordEntity(
            String handler,
            LocalDateTime handledAt,
            String result,
            String alertLevel,
            String title,
            String detail,
            String occurredAt,
            String device
    ) {
        this.handler = handler;
        this.handledAt = handledAt;
        this.result = result;
        this.alertLevel = alertLevel;
        this.title = title;
        this.detail = detail;
        this.occurredAt = occurredAt;
        this.device = device;
    }

    public Long getId() {
        return id;
    }

    public String getHandler() {
        return handler;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public String getResult() {
        return result;
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public String getDevice() {
        return device;
    }
}
