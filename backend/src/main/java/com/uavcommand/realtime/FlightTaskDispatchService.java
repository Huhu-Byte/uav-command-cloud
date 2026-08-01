package com.uavcommand.realtime;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 航线任务调度服务：航线任务端到端下发。
 *
 * <p>整合 4 步流程：
 *   1. 根据航线 ID 加载航点 JSON
 *   2. WaylineFileGenerator 生成 KMZ/WPML 文件
 *   3. OssService 上传 KMZ 到对象存储，获取下载 URL + MD5
 *   4. MqttPublisher 发布 flighttask_create/prepare/execute 到机场
 *
 * <p>MQTT 未启用或 OSS 未启用时：走完所有流程，仅在 MQTT 发布步骤打日志跳过，
 * 便于本地开发联调前端完整流程。</p>
 */
@Service
public class FlightTaskDispatchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlightTaskDispatchService.class);

    private final FlightRouteService flightRouteService;
    private final WaylineFileGenerator waylineFileGenerator;
    private final OssService ossService;
    private final DjiMqttCommandPublisher mqttPublisher;
    private final DjiMqttProperties mqttProperties;
    private final InspectionTaskService inspectionTaskService;

    public FlightTaskDispatchService(
            FlightRouteService flightRouteService,
            WaylineFileGenerator waylineFileGenerator,
            OssService ossService,
            DjiMqttCommandPublisher mqttPublisher,
            DjiMqttProperties mqttProperties,
            InspectionTaskService inspectionTaskService
    ) {
        this.flightRouteService = flightRouteService;
        this.waylineFileGenerator = waylineFileGenerator;
        this.ossService = ossService;
        this.mqttPublisher = mqttPublisher;
        this.mqttProperties = mqttProperties;
        this.inspectionTaskService = inspectionTaskService;
    }

    /**
     * 下发航线任务到指定机场。
     *
     * @param routeId    航线 ID
     * @param gatewaySn  机场网关 SN
     * @param operator   操作者
     * @param taskName   任务名称（可为空，默认用航线名）
     * @return 任务 ID 和各步骤结果
     */
    @Transactional
    public DispatchResult dispatch(Long routeId, String gatewaySn, String operator, String taskName) {
        long startAt = System.currentTimeMillis();
        FlightRouteService.RouteView route = flightRouteService.get(routeId);
        String flightId = "UAV" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();

        // 步骤 1：生成 KMZ
        WaylineFileGenerator.GenerateResult kmz;
        try {
            kmz = waylineFileGenerator.generateKmz(flightId, route.name(), route.waypointsJson());
        } catch (Exception e) {
            LOGGER.error("生成 KMZ 失败 routeId={}", routeId, e);
            throw new RuntimeException("生成航线文件失败：" + e.getMessage(), e);
        }

        // 步骤 2：上传到对象存储
        OssService.UploadResult upload = ossService.uploadKmz(kmz.kmzBytes(), flightId, kmz.fingerprint());

        // 步骤 3：在任务库创建一条记录（如果传入了 taskName）
        if (taskName != null && !taskName.isBlank()) {
            try {
                inspectionTaskService.create(operator, new InspectionTaskService.CreateTaskRequest(
                        taskName, route.name(), "待下发",
                        null, null, null, "自动下发", flightId
                ));
            } catch (Exception e) {
                LOGGER.warn("创建任务记录失败，不影响下发 flightId={}", flightId, e);
            }
        }

        // 步骤 4：MQTT 三步下发（异常捕获避免前端 500，返回结果带报错）
        DispatchStepResult createResult = doPublish("flighttask_create",
                () -> mqttPublisher.publishFlighttaskCreate(gatewaySn, flightId, upload.url(), upload.fingerprint()));
        DispatchStepResult prepareResult = DispatchStepResult.skip("等待 create 成功再执行 prepare");
        DispatchStepResult executeResult = DispatchStepResult.skip("等待 prepare 成功再执行 execute");

        if (createResult.success) {
            prepareResult = doPublish("flighttask_prepare",
                    () -> mqttPublisher.publishFlighttaskPrepare(gatewaySn, flightId));
            if (prepareResult.success) {
                executeResult = doPublish("flighttask_execute",
                        () -> mqttPublisher.publishFlighttaskExecute(gatewaySn, flightId));
            }
        }

        long durationMs = System.currentTimeMillis() - startAt;
        LOGGER.info("航线任务下发完成 flightId={} routeId={} gatewaySn={} create={} prepare={} execute={} 耗时={}ms",
                flightId, routeId, gatewaySn,
                createResult.success ? "OK" : createResult.error,
                prepareResult.success ? "OK" : prepareResult.error,
                executeResult.success ? "OK" : executeResult.error,
                durationMs);

        return new DispatchResult(
                flightId,
                gatewaySn,
                routeId,
                route.name(),
                taskName,
                upload.url(),
                kmz.fingerprint(),
                kmz.waypointCount(),
                upload.sizeBytes(),
                durationMs,
                createResult,
                prepareResult,
                executeResult
        );
    }

    /** 取消任务。 */
    public Map<String, Object> cancel(String flightId, String gatewaySn) {
        return mqttPublisher.publishServiceAndWait(gatewaySn, "flighttask_undo", Map.of("flight_id", flightId));
    }

    private DispatchStepResult doPublish(String stepName, java.util.function.Supplier<Map<String, Object>> action) {
        try {
            Map<String, Object> reply = action.get();
            Object code = reply == null ? null : reply.get("code");
            boolean ok = reply != null && (code == null || "0".equals(String.valueOf(code)) || 0 == ((Number) code).intValue());
            return new DispatchStepResult(ok, null, reply);
        } catch (Exception e) {
            LOGGER.warn("下发步骤失败 step={}", stepName, e);
            // MQTT 未启用时的 mock 成功
            if (!mqttProperties.isEnabled()) {
                LOGGER.info("MQTT 未启用，mock step={} 成功", stepName);
                return new DispatchStepResult(true, "MQTT_DISABLED_MOCK", Map.of("code", 0, "mock", true));
            }
            return new DispatchStepResult(false, e.getMessage(), null);
        }
    }

    /** 下发总结果。 */
    public record DispatchResult(
            String flightId,
            String gatewaySn,
            Long routeId,
            String routeName,
            String taskName,
            String kmzUrl,
            String fingerprint,
            int waypointCount,
            long kmzSizeBytes,
            long durationMs,
            DispatchStepResult create,
            DispatchStepResult prepare,
            DispatchStepResult execute
    ) {
        public boolean overallSuccess() {
            return create.success && prepare.success && execute.success;
        }
    }

    /** 单步骤下发结果。 */
    public record DispatchStepResult(
            boolean success,
            String error,
            Map<String, Object> reply
    ) {
        public static DispatchStepResult skip(String reason) {
            return new DispatchStepResult(false, "SKIPPED: " + reason, null);
        }
    }
}
