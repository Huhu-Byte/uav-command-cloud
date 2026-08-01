package com.uavcommand.realtime;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FlightRouteService {

    private final FlightRouteRepository flightRouteRepository;
    private final ObjectMapper objectMapper;

    public FlightRouteService(FlightRouteRepository flightRouteRepository, ObjectMapper objectMapper) {
        this.flightRouteRepository = flightRouteRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void createDemoRoutesWhenEmpty() {
        if (flightRouteRepository.count() != 0) return;

        LocalDateTime now = LocalDateTime.now();
        List<Waypoint> eastFence = List.of(
                new Waypoint("A1", "起飞点", 39.9042, 116.4074, 60.0, 8.0, "TAKE_PHOTO", "主相机", "单次拍摄"),
                new Waypoint("A2", "东侧围栏中段", 39.9052, 116.4094, 60.0, 10.0, "TAKE_PHOTO", "主相机", "间隔2秒连拍"),
                new Waypoint("A3", "东北角", 39.9062, 116.4114, 50.0, 6.0, "RECORD_VIDEO", "主相机", "4K视频录制"),
                new Waypoint("A4", "返回检查点", 39.9052, 116.4134, 60.0, 10.0, "TAKE_PHOTO", "主相机", "单次拍摄")
        );
        List<Waypoint> roofPv = List.of(
                new Waypoint("R1", "屋顶西北角", 39.9038, 116.4068, 80.0, 6.0, "TAKE_PHOTO", "红外相机", "热成像拍摄"),
                new Waypoint("R2", "屋顶光伏A区", 39.9042, 116.4076, 80.0, 5.0, "TAKE_PHOTO", "红外相机", "热成像拍摄"),
                new Waypoint("R3", "屋顶光伏B区", 39.9046, 116.4084, 80.0, 5.0, "TAKE_PHOTO", "红外相机", "热成像拍摄"),
                new Waypoint("R4", "屋顶东南角", 39.9048, 116.4092, 80.0, 6.0, "TAKE_PHOTO", "红外相机", "热成像拍摄")
        );
        List<Waypoint> northGate = List.of(
                new Waypoint("N1", "北门广场", 39.9070, 116.4060, 50.0, 8.0, "TAKE_PHOTO", "主相机", "单次拍摄"),
                new Waypoint("N2", "北门车行道", 39.9078, 116.4068, 40.0, 6.0, "RECORD_VIDEO", "主相机", "视频巡逻"),
                new Waypoint("N3", "北门人行道", 39.9082, 116.4076, 40.0, 6.0, "RECORD_VIDEO", "主相机", "视频巡逻")
        );

        try {
            flightRouteRepository.saveAll(List.of(
                    new FlightRouteEntity("东侧围栏巡检路线", "园区东侧", "WAYPOINT_FLIGHT", toJson(eastFence), "张晨", now, now),
                    new FlightRouteEntity("屋顶光伏巡检路线", "屋顶光伏区", "AERIAL_PHOTOGRAPHY", toJson(roofPv), "李然", now, now),
                    new FlightRouteEntity("北门周界巡检路线", "北门区域", "WAYPOINT_FLIGHT", toJson(northGate), "王敏", now, now)
            ));
        } catch (Exception ignored) {
        }
    }

    public List<RouteView> list() {
        return flightRouteRepository.findAllByOrderByModifiedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    public RouteView get(Long id) {
        FlightRouteEntity entity = flightRouteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到该航线"));
        return toView(entity);
    }

    public RouteView create(String operator, CreateRouteRequest request) {
        ValidatedRoute details = validate(request);
        FlightRouteEntity entity = flightRouteRepository.save(new FlightRouteEntity(
                details.name(), details.area(), details.mode(), details.waypointsJson(),
                operator, LocalDateTime.now(), LocalDateTime.now()
        ));
        return toView(entity);
    }

    public RouteView update(Long id, String operator, CreateRouteRequest request) {
        FlightRouteEntity entity = flightRouteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到该航线"));
        ValidatedRoute details = validate(request);
        if (entity.getUsedInTasks() > 0 && !request.allowUsedRouteModification()) {
            throw new IllegalStateException("该航线已被任务使用，确认修改可能影响后续任务。请勾选允许修改确认后再保存。");
        }
        entity.updateDetails(details.name(), details.area(), details.mode(), details.waypointsJson(), operator, LocalDateTime.now());
        return toView(flightRouteRepository.save(entity));
    }

    public void delete(Long id) {
        FlightRouteEntity entity = flightRouteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("未找到该航线"));
        if (entity.getUsedInTasks() > 0) {
            throw new IllegalStateException("该航线已被任务使用，不能删除");
        }
        flightRouteRepository.delete(entity);
    }

    public ValidateReport validateRoute(CreateRouteRequest request) {
        try {
            ValidatedRoute details = validate(request);
            List<Waypoint> waypoints = parseWaypoints(details.waypointsJson());
            int pointCount = waypoints.size();

            boolean valid = true;
            List<String> warnings = new java.util.ArrayList<>();
            List<String> errors = new java.util.ArrayList<>();

            if (pointCount < 3) {
                errors.add("至少需要 3 个航点才能形成可复用的航线");
                valid = false;
            }

            double totalDistance = 0;
            double totalAltitudeChange = 0;
            for (int i = 0; i < waypoints.size() - 1; i++) {
                Waypoint a = waypoints.get(i);
                Waypoint b = waypoints.get(i + 1);
                totalDistance += haversine(a.lat(), a.lng(), b.lat(), b.lng()) * 1000;
                totalAltitudeChange += Math.abs(b.altitude() - a.altitude());
            }

            for (int i = 0; i < waypoints.size(); i++) {
                Waypoint wp = waypoints.get(i);
                if (wp.altitude() < 20 || wp.altitude() > 120) {
                    errors.add(String.format("航点 %s（%s）高度 %.0f m 超出 20-120 m 范围", wp.code(), wp.label(), wp.altitude()));
                    valid = false;
                }
                if (wp.speed() < 2 || wp.speed() > 15) {
                    errors.add(String.format("航点 %s（%s）速度 %.0f m/s 超出 2-15 m/s 范围", wp.code(), wp.label(), wp.speed()));
                    valid = false;
                }
            }

            if (totalDistance > 5000) {
                warnings.add(String.format("总航线距离 %.0f m 较长，建议分段规划或确认电量充足", totalDistance));
            }
            if (totalAltitudeChange > 200) {
                warnings.add(String.format("全程高度变化 %.0f m，建议复核航线合理性", totalAltitudeChange));
            }

            if (totalDistance > 0 && pointCount >= 3) {
                warnings.add(String.format("航线已就绪：共 %d 个航点，总距离 %.0f m，预计 %.0f s 完成",
                        pointCount, totalDistance, estimateDuration(totalDistance, waypoints)));
            }

            return new ValidateReport(valid, errors, warnings, pointCount, Math.round(totalDistance), (int) estimateDuration(totalDistance, waypoints));
        } catch (IllegalArgumentException ex) {
            return new ValidateReport(false, List.of(ex.getMessage()), List.of(), 0, 0, 0);
        } catch (Exception ex) {
            return new ValidateReport(false, List.of("航点数据格式异常"), List.of(ex.getMessage()), 0, 0, 0);
        }
    }

    private double estimateDuration(double distanceMeters, List<Waypoint> waypoints) {
        if (waypoints.isEmpty()) return 0;
        double avgSpeed = waypoints.stream().mapToDouble(Waypoint::speed).average().orElse(8.0);
        if (avgSpeed < 1) avgSpeed = 8;
        double flightTime = distanceMeters / avgSpeed;
        double waypointPause = waypoints.size() * 2.0;
        return flightTime + waypointPause;
    }

    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private ValidatedRoute validate(CreateRouteRequest request) {
        String name = requireText(request.name(), "请填写航线名称", 80);
        String area = requireText(request.area(), "请选择所属区域", 60);
        String mode = requireText(request.mode(), "请选择飞行模式", 20);
        if (!"AERIAL_PHOTOGRAPHY".equals(mode) && !"WAYPOINT_FLIGHT".equals(mode)) {
            throw new IllegalArgumentException("飞行模式不合法");
        }
        String waypointsJson = requireText(request.waypointsJson(), "请先在地图上添加航点", Integer.MAX_VALUE);
        try {
            List<Waypoint> wps = parseWaypoints(waypointsJson);
            if (wps.isEmpty()) throw new IllegalArgumentException("请至少添加一个航点");
        } catch (Exception ex) {
            throw new IllegalArgumentException("航点数据格式异常");
        }
        return new ValidatedRoute(name, area, mode, waypointsJson);
    }

    private RouteView toView(FlightRouteEntity entity) {
        try {
            List<Waypoint> wps = parseWaypoints(entity.getWaypointsJson());
            return new RouteView(
                    entity.getId(), entity.getName(), entity.getArea(), entity.getMode(),
                    wps, wps.size(),
                    entity.getCreatedBy(), entity.getModifiedBy(),
                    entity.getCreatedAt(), entity.getModifiedAt(),
                    entity.getUsedInTasks()
            );
        } catch (Exception ex) {
            return new RouteView(
                    entity.getId(), entity.getName(), entity.getArea(), entity.getMode(),
                    List.of(), 0,
                    entity.getCreatedBy(), entity.getModifiedBy(),
                    entity.getCreatedAt(), entity.getModifiedAt(),
                    entity.getUsedInTasks()
            );
        }
    }

    public String toJson(List<Waypoint> waypoints) throws JsonProcessingException {
        return objectMapper.writeValueAsString(waypoints);
    }

    public List<Waypoint> parseWaypoints(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<List<Waypoint>>() {});
    }

    private String requireText(String value, String message, int maximumLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(message);
        if (normalized.length() > maximumLength) throw new IllegalArgumentException("填写内容过长，请缩短后重试");
        return normalized;
    }

    public record CreateRouteRequest(
            String name,
            String area,
            String mode,
            String waypointsJson,
            boolean allowUsedRouteModification
    ) { }

    public record Waypoint(
            String code,
            String label,
            double lat,
            double lng,
            double altitude,
            double speed,
            String action,
            String payload,
            String actionParam
    ) { }

    public record RouteView(
            Long id,
            String name,
            String area,
            String mode,
            List<Waypoint> waypoints,
            int waypointCount,
            String createdBy,
            String modifiedBy,
            LocalDateTime createdAt,
            LocalDateTime modifiedAt,
            int usedInTasks
    ) { }

    public record ValidateReport(
            boolean valid,
            List<String> errors,
            List<String> warnings,
            int waypointCount,
            long totalDistanceMeters,
            int estimatedDurationSeconds
    ) { }

    private record ValidatedRoute(String name, String area, String mode, String waypointsJson) { }
}
