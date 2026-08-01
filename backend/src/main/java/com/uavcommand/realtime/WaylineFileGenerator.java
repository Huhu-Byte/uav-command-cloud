package com.uavcommand.realtime;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DJI 航线文件生成器。
 *
 * <p>负责将前端编辑的航点数据（JSON 格式）转换为 DJI 标准航线文件：
 * <ul>
 *   <li>WPML (KML 扩展格式 XML)：包含航点坐标、高度、速度、动作等</li>
 *   <li>KMZ：将 WPML 和其他元数据打包为 ZIP 文件，供机场下载执行</li>
 * </ul>
 *
 * <p>KMZ 目录结构（DJI 规范）：
 * <pre>
 * route-{flightId}.kmz
 *   ├─ waylines/
 *   │   └─ wayline.wpml     航线 KML 文件
 *   └─ template.kml         KML 目录索引
 * </pre>
 */
@Service
public class WaylineFileGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(WaylineFileGenerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** 1 米 = 3.28084 英尺（DJI WPML 使用英尺表示高度，或者米需额外配置，这里直接用米） */
    private static final double DEFAULT_SPEED = 8.0;
    /** DJI WPML template.kml 最小规范：仅引用 waylines/wayline.wpml */
    private static final String TEMPLATE_KML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n"
            + "  <Document>\n"
            + "    <name>Wayline Mission</name>\n"
            + "    <open>1</open>\n"
            + "    <NetworkLink>\n"
            + "      <name>Wayline</name>\n"
            + "      <Link>\n"
            + "        <href>waylines/wayline.wpml</href>\n"
            + "      </Link>\n"
            + "    </NetworkLink>\n"
            + "  </Document>\n"
            + "</kml>\n";

    /**
     * 生成 KMZ 文件字节数组。
     *
     * @param flightId      任务 ID（用于 KMZ 文件名）
     * @param routeName     航线名称
     * @param waypointsJson 航点 JSON（前端保存的 waypoints）
     * @return 包含 KMZ 内容、WPML 内容、MD5 签名（fingerprint/sign）的结果
     */
    public GenerateResult generateKmz(String flightId, String routeName, String waypointsJson) throws IOException {
        List<Waypoint> waypoints = parseWaypoints(waypointsJson);
        if (waypoints.size() < 2) {
            throw new IllegalArgumentException("航线至少需要 2 个航点，当前只有 " + waypoints.size() + " 个");
        }

        String wpml = buildWpml(routeName, waypoints);
        byte[] kmzBytes = packKmz(wpml);
        String fingerprint = calcFingerprint(kmzBytes);

        LOGGER.info("生成 KMZ flightId={} waypoints={} size={}KB fingerprint={}",
                flightId, waypoints.size(), kmzBytes.length / 1024, fingerprint.substring(0, Math.min(8, fingerprint.length())));
        return new GenerateResult(kmzBytes, wpml, fingerprint, waypoints.size());
    }

    /** 解析前端航点 JSON，兼容多种格式。 */
    @SuppressWarnings("unchecked")
    private List<Waypoint> parseWaypoints(String waypointsJson) throws IOException {
        Object raw = MAPPER.readValue(waypointsJson, Object.class);
        List<Map<String, Object>> rawList;
        if (raw instanceof List) {
            rawList = (List<Map<String, Object>>) raw;
        } else if (raw instanceof Map m && m.containsKey("waypoints")) {
            rawList = (List<Map<String, Object>>) m.get("waypoints");
        } else if (raw instanceof Map m && m.containsKey("points")) {
            rawList = (List<Map<String, Object>>) m.get("points");
        } else {
            throw new IllegalArgumentException("无法解析的航点 JSON 格式");
        }

        List<Waypoint> result = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            Map<String, Object> p = rawList.get(i);
            double lat = getNum(p, "latitude", "lat");
            double lng = getNum(p, "longitude", "lng", "lon");
            double alt = getNum(p, "height", "altitude", "alt");
            double speed = p.containsKey("speed") ? getDouble(p, "speed", DEFAULT_SPEED) : DEFAULT_SPEED;
            result.add(new Waypoint(i + 1, lat, lng, alt, speed, null));
        }
        return result;
    }

    private double getNum(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            if (map.containsKey(k)) return getDouble(map, k, 0);
        }
        throw new IllegalArgumentException("航点缺少坐标字段（latitude/longitude/height）: " + map);
    }

    private double getDouble(Map<String, Object> map, String key, double fallback) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (Exception e) { return fallback; }
    }

    /**
     * 构建 DJI WPML 文件。
     *
     * <p>WPML 是 DJI 自定义的 KML 扩展，根节点是 Document，包含：
     * <ul>
     *   <li>Folder/Placemark — 每个航点一个 Placemark，Point/coordinates 存坐标</li>
     *   <li>ExtendedData/dji:WaylineMission — DJI 专有扩展，存速度、高度模式、动作等</li>
     *   <li>dji:waypoint — 每个航点的参数（高度、转向模式、悬停时间）</li>
     *   <li>dji:action — 航点动作（拍照、录像、悬停）</li>
     * </ul>
     */
    private String buildWpml(String routeName, List<Waypoint> waypoints) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:wpml=\"http://www.dji.com/wpml\">\n");
        sb.append("  <Document>\n");
        sb.append("    <name>").append(xmlEscape(routeName)).append("</name>\n");
        sb.append("    <wpml:missionConfig>\n");
        sb.append("      <wpml:flyToWaylineMode>safely</wpml:flyToWaylineMode>\n");
        sb.append("      <wpml:finishAction>goHome</wpml:finishAction>\n");
        sb.append("      <wpml:exitOnRCLost>executeLostAction</wpml:exitOnRCLost>\n");
        sb.append("      <wpml:executeRCLostAction>land</wpml:executeRCLostAction>\n");
        sb.append("      <wpml:safetyTakeoffHeight>30</wpml:safetyTakeoffHeight>\n");
        sb.append("      <wpml:globalTransitionalSpeed>").append(DEFAULT_SPEED).append("</wpml:globalTransitionalSpeed>\n");
        sb.append("      <wpml:droneInfo>\n");
        sb.append("        <wpml:droneEnumValue>67</wpml:droneEnumValue>\n");
        sb.append("        <wpml:droneSubEnumValue>0</wpml:droneSubEnumValue>\n");
        sb.append("      </wpml:droneInfo>\n");
        sb.append("    </wpml:missionConfig>\n");
        sb.append("    <Folder>\n");
        sb.append("      <wpml:templateType>waypoint</wpml:templateType>\n");
        sb.append("      <wpml:templateId>0</wpml:templateId>\n");
        sb.append("      <wpml:waylineCoordinateSysParam>\n");
        sb.append("        <wpml:coordinateMode>WGS84</wpml:coordinateMode>\n");
        sb.append("        <wpml:heightMode>relativeToGround</wpml:heightMode>\n");
        sb.append("        <wpml:positioningType>GPS</wpml:positioningType>\n");
        sb.append("      </wpml:waylineCoordinateSysParam>\n");
        sb.append("      <wpml:payloadInfo>\n");
        sb.append("        <wpml:payloadEnumValue>52</wpml:payloadEnumValue>\n");
        sb.append("        <wpml:payloadSubEnumValue>0</wpml:payloadSubEnumValue>\n");
        sb.append("        <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n");
        sb.append("      </wpml:payloadInfo>\n");
        sb.append("      <wpml:globalWaypointHeadingParam>\n");
        sb.append("        <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>\n");
        sb.append("      </wpml:globalWaypointHeadingParam>\n");
        sb.append("      <wpml:globalWaypointTurnParam>\n");
        sb.append("        <wpml:waypointTurnMode>toPointAndStopWithContinuityCurvature</wpml:waypointTurnMode>\n");
        sb.append("        <wpml:waypointTurnDampingDist>0</wpml:waypointTurnDampingDist>\n");
        sb.append("      </wpml:globalWaypointTurnParam>\n");
        sb.append("      <wpml:useGlobalTransitionalSpeed>1</wpml:useGlobalTransitionalSpeed>\n");
        sb.append("      <wpml:globalWaypointMaxSpeed>15.0</wpml:globalWaypointMaxSpeed>\n");
        sb.append("      <wpml:globalWaypointSpeed>").append(DEFAULT_SPEED).append("</wpml:globalWaypointSpeed>\n");

        for (Waypoint wp : waypoints) {
            sb.append("      <Placemark>\n");
            sb.append("        <name>waypoint-").append(wp.index()).append("</name>\n");
            sb.append("        <Point><coordinates>").append(wp.lng()).append(",").append(wp.lat()).append(",").append(wp.height()).append("</coordinates></Point>\n");
            sb.append("        <wpml:index>").append(wp.index() - 1).append("</wpml:index>\n");
            sb.append("        <wpml:waypointPointType>lineStop</wpml:waypointPointType>\n");
            sb.append("        <wpml:waypointSpeed>").append(wp.speed()).append("</wpml:waypointSpeed>\n");
            sb.append("        <wpml:waypointUseGlobalHeight>1</wpml:waypointUseGlobalHeight>\n");
            sb.append("        <wpml:waypointHeight>").append(wp.height()).append("</wpml:waypointHeight>\n");
            sb.append("        <wpml:useGlobalWaypointSpeed>1</wpml:useGlobalWaypointSpeed>\n");
            sb.append("        <wpml:waypointHeadingParam>\n");
            sb.append("          <wpml:waypointHeadingMode>followWayline</wpml:waypointHeadingMode>\n");
            sb.append("          <wpml:waypointHeadingAngle>0</wpml:waypointHeadingAngle>\n");
            sb.append("          <wpml:waypointPoiPoint>0.000000,0.000000,0.000000</wpml:waypointPoiPoint>\n");
            sb.append("          <wpml:waypointHeadingPoiIndex>0</wpml:waypointHeadingPoiIndex>\n");
            sb.append("        </wpml:waypointHeadingParam>\n");
            sb.append("        <wpml:waypointTurnParam>\n");
            sb.append("          <wpml:waypointTurnMode>toPointAndStopWithContinuityCurvature</wpml:waypointTurnMode>\n");
            sb.append("          <wpml:waypointTurnDampingDist>0</wpml:waypointTurnDampingDist>\n");
            sb.append("        </wpml:waypointTurnParam>\n");
            // 首末航点不悬停，中间航点停留 0 秒
            sb.append("        <wpml:waypointGimbalHeadingParam>\n");
            sb.append("          <wpml:waypointGimbalPitchAngle>-90</wpml:waypointGimbalPitchAngle>\n");
            sb.append("          <wpml:waypointGimbalYawAngle>0</wpml:waypointGimbalYawAngle>\n");
            sb.append("          <wpml:waypointGimbalRotateMode>rotateYaw</wpml:waypointGimbalRotateMode>\n");
            sb.append("          <wpml:waypointGimbalYawRotaEnable>0</wpml:waypointGimbalYawRotaEnable>\n");
            sb.append("        </wpml:waypointGimbalHeadingParam>\n");
            // 中间航点添加拍照动作
            if (wp.index() > 1 && wp.index() < waypoints.size()) {
                sb.append("        <wpml:actionGroup>\n");
                sb.append("          <wpml:actionGroupId>").append(wp.index() * 10).append("</wpml:actionGroupId>\n");
                sb.append("          <wpml:actionGroupStartIndex>").append(wp.index() - 1).append("</wpml:actionGroupStartIndex>\n");
                sb.append("          <wpml:actionGroupEndIndex>").append(wp.index() - 1).append("</wpml:actionGroupEndIndex>\n");
                sb.append("          <wpml:actionGroupMode>parallel</wpml:actionGroupMode>\n");
                sb.append("          <wpml:actionTrigger>\n");
                sb.append("            <wpml:actionTriggerType>reachPoint</wpml:actionTriggerType>\n");
                sb.append("          </wpml:actionTrigger>\n");
                sb.append("          <wpml:action>\n");
                sb.append("            <wpml:actionId>1</wpml:actionId>\n");
                sb.append("            <wpml:actionActuatorFunc>takePhoto</wpml:actionActuatorFunc>\n");
                sb.append("            <wpml:actionActuatorFuncParam>\n");
                sb.append("              <wpml:fileSuffix>wayline</wpml:fileSuffix>\n");
                sb.append("              <wpml:payloadPositionIndex>0</wpml:payloadPositionIndex>\n");
                sb.append("            </wpml:actionActuatorFuncParam>\n");
                sb.append("          </wpml:action>\n");
                sb.append("        </wpml:actionGroup>\n");
            }
            sb.append("      </Placemark>\n");
        }

        sb.append("    </Folder>\n");
        sb.append("  </Document>\n");
        sb.append("</kml>\n");
        return sb.toString();
    }

    /** 打包 KMZ（ZIP 格式，文件扩展名 .kmz）。 */
    private byte[] packKmz(String wpmlContent) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(16384);
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // template.kml
            zos.putNextEntry(new ZipEntry("template.kml"));
            zos.write(TEMPLATE_KML.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // waylines/wayline.wpml
            zos.putNextEntry(new ZipEntry("waylines/wayline.wpml"));
            zos.write(wpmlContent.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    /** 计算 KMZ 的 MD5 指纹作为 DJI flighttask create 的 sign/fingerprint 字段。 */
    private String calcFingerprint(byte[] bytes) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算 MD5 失败", e);
        }
    }

    private String xmlEscape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** 单个航点。 */
    private record Waypoint(int index, double lat, double lng, double height, double speed, List<String> actions) {}

    /** KMZ 生成结果。 */
    public record GenerateResult(
            byte[] kmzBytes,
            String wpmlXml,
            String fingerprint,
            int waypointCount
    ) {}
}
