package com.lnsoft.erp.servicebridge.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lnsoft.erp.servicebridge.server.core.BridgeHandlerResult;
import com.lnsoft.erp.servicebridge.server.core.BridgeRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateReportHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GenerateReportHandler handler = new GenerateReportHandler();

    @Test
    void generatesReport() {
        BridgeRequestContext context = context("monthly-sales", "alice");

        BridgeHandlerResult result = handler.handle(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isInstanceOf(Map.class);
        Map<?, ?> report = (Map<?, ?>) result.getData();
        assertThat(report.get("requestId")).isEqualTo("request-1");
        assertThat(report.get("reportName")).isEqualTo("monthly-sales");
        assertThat(report.get("requestedBy")).isEqualTo("alice");
        assertThat(report.get("status")).isEqualTo("GENERATED");
    }

    @Test
    void rejectsBlankReportName() {
        BridgeRequestContext context = context("", "alice");

        BridgeHandlerResult result = handler.handle(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_REPORT_NAME");
    }

    private BridgeRequestContext context(String reportName, String requestedBy) {
        BridgeRequestContext context = new BridgeRequestContext();
        context.setRequestId("request-1");
        context.setBusinessType("GENERATE_REPORT");
        context.setPayload(objectMapper.valueToTree(new Request(reportName, requestedBy)));
        return context;
    }

    private static final class Request {
        private final String reportName;
        private final String requestedBy;

        private Request(String reportName, String requestedBy) {
            this.reportName = reportName;
            this.requestedBy = requestedBy;
        }

        public String getReportName() {
            return reportName;
        }

        public String getRequestedBy() {
            return requestedBy;
        }
    }
}
