package com.uavcommand.realtime;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 组织设备待绑定资料实体，映射 organization_device_profiles 表。
 *
 * <p>用于管理 DJI Dock 机场和无人机的注册信息，包括组织归属、设备序列号、绑定状态等。
 * 设备入网前需先在此表注册待绑定资料，MQTT 握手时通过 gateway_sn 关联。</p>
 */
@Entity
@Table(name = "organization_device_profiles")
public class DeviceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, length = 100)
    private String organizationId;

    @Column(name = "organization_name", nullable = false, length = 200)
    private String organizationName;

    @Column(name = "master_sn", length = 100)
    private String masterSn;

    @Column(name = "nameplate_sn", length = 100)
    private String nameplateSn;

    @Column(name = "nameplate_serial_number", length = 100)
    private String nameplateSerialNumber;

    @Column(name = "gateway_serial_number", length = 100)
    private String gatewaySerialNumber;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "status", length = 50)
    private String status = "待绑定";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected DeviceEntity() {}

    public DeviceEntity(
            String organizationId,
            String organizationName,
            String masterSn,
            String deviceType,
            String displayName
    ) {
        this.organizationId = organizationId;
        this.organizationName = organizationName;
        this.masterSn = masterSn;
        this.gatewaySerialNumber = masterSn;
        this.deviceType = deviceType;
        this.displayName = displayName;
        this.status = "待绑定";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getOrganizationId() { return organizationId; }
    public String getOrganizationName() { return organizationName; }
    public String getMasterSn() { return masterSn; }
    public String getNameplateSn() { return nameplateSn; }
    public String getNameplateSerialNumber() { return nameplateSerialNumber; }
    public String getGatewaySerialNumber() { return gatewaySerialNumber; }
    public String getDisplayName() { return displayName; }
    public String getDeviceType() { return deviceType; }
    public String getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void bindDevice(String nameplateSn, String nameplateSerialNumber) {
        this.nameplateSn = nameplateSn;
        this.nameplateSerialNumber = nameplateSerialNumber;
        this.status = "已绑定";
    }

    public void updateStatus(String status) {
        this.status = status;
    }
}
