package top.redjujubetree.servicebridge.example;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class GenerateReportHandler implements BridgeRequestHandler {

    @Override
    public String businessType() {
        return "GENERATE_REPORT";
    }

    @Override
    public BridgeHandlerResult handle(BridgeRequestContext context) {
        JsonNode payload = context.getPayload();
        String reportName = payload.path("reportName").asText("").trim();
        if (reportName.isEmpty()) {
            return BridgeHandlerResult.failure(
                    "INVALID_REPORT_NAME", "reportName must not be blank");
        }

        // requestId 可作为真实业务操作的幂等键。
        Map<String, Object> report = new LinkedHashMap<String, Object>();
        report.put("requestId", context.getRequestId());
        report.put("reportName", reportName);
        report.put("requestedBy", payload.path("requestedBy").asText("anonymous"));
        report.put("generatedAt", Instant.now().toString());
        report.put("status", "GENERATED");
        report.put("key-bus", "value-bus");
        return BridgeHandlerResult.success(report);
    }
}
