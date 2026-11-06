package top.redjujubetree.servicebridge.example.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;
import top.redjujubetree.servicebridge.client.core.ServiceBridgeClient;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.example.client.handler.ReportResponseHandler;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ReportResponseHandler handler = new ReportResponseHandler(objectMapper);

    @Test
    void createUsesCustomHandlerAndPassesTraceId() throws Exception {
        ServiceBridgeClient client = mock(ServiceBridgeClient.class);
        JsonNode request = objectMapper.readTree("{\"reportName\":\"monthly\"}");
        DeferredResult<ResponseEntity<JsonNode>> result = new DeferredResult<>();
        when(client.request("GENERATE_REPORT", request, "trace-1", handler)).thenReturn(result);

        assertSame(result, new ReportController(client, handler).create(request, "trace-1"));
        verify(client).request("GENERATE_REPORT", request, "trace-1", handler);
    }

    @Test
    void createOmitsBlankTraceId() throws Exception {
        ServiceBridgeClient client = mock(ServiceBridgeClient.class);
        JsonNode request = objectMapper.readTree("{\"reportName\":\"monthly\"}");
        DeferredResult<ResponseEntity<JsonNode>> result = new DeferredResult<>();
        when(client.request("GENERATE_REPORT", request, null, handler)).thenReturn(result);

        assertSame(result, new ReportController(client, handler).create(request, "  "));
        verify(client).request("GENERATE_REPORT", request, null, handler);
    }

    @Test
    void handlerReturnsReportJsonAndStructuredErrors() throws Exception {
        BridgeStoredResult stored = new BridgeStoredResult();
        stored.setRequestId("request-1");
        stored.setProcessStatus(BridgeProcessStatus.SUCCESS);
        stored.setResultPayload("{\"status\":\"GENERATED\"}");

        ResponseEntity<JsonNode> success = handler.fromStored(stored);
        assertEquals(HttpStatus.OK, success.getStatusCode());
        assertEquals("GENERATED", success.getBody().path("status").asText());

        stored.setProcessStatus(BridgeProcessStatus.FAILED);
        stored.setErrorCode("INVALID_REPORT_NAME");
        stored.setErrorMessage("reportName must not be blank");
        ResponseEntity<JsonNode> failure = handler.fromStored(stored);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatusCode());
        assertEquals("request-1", failure.getBody().path("requestId").asText());
        assertEquals("INVALID_REPORT_NAME", failure.getBody().path("code").asText());

        ResponseEntity<JsonNode> timeout = handler.timeout("request-2");
        assertEquals(HttpStatus.REQUEST_TIMEOUT, timeout.getStatusCode());
        assertEquals("TIMEOUT", timeout.getBody().path("code").asText());

        ResponseEntity<JsonNode> unavailable = handler.unavailable("request-3", "broker unavailable");
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getStatusCode());
        assertEquals("broker unavailable", unavailable.getBody().path("message").asText());
    }
}
