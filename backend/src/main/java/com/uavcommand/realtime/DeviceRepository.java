package com.uavcommand.realtime;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** 设备资料数据访问接口。 */
public interface DeviceRepository extends JpaRepository<DeviceEntity, Long> {

    /** 按网关序列号查找设备（MQTT 握手时使用）。 */
    Optional<DeviceEntity> findByGatewaySerialNumber(String gatewaySerialNumber);

    /** 按组织 ID 查找设备列表。 */
    List<DeviceEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

    /** 检查网关序列号是否已注册。 */
    boolean existsByGatewaySerialNumber(String gatewaySerialNumber);
}
