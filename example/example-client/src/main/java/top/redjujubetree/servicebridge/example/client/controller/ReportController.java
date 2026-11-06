package top.redjujubetree.servicebridge.example.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.client.core.ServiceBridgeClient;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import top.redjujubetree.servicebridge.example.client.handler.ReportResponseHandler;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final String BUSINESS_TYPE = "GENERATE_REPORT";

    private final ServiceBridgeClient serviceBridgeClient;
    private final ReportResponseHandler responseHandler;

    public ReportController(ServiceBridgeClient serviceBridgeClient, ReportResponseHandler responseHandler) {
        this.serviceBridgeClient = serviceBridgeClient;
        this.responseHandler = responseHandler;
    }

    @PostMapping
    public DeferredResult<ResponseEntity<JsonNode>> create(
            @RequestBody JsonNode request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return serviceBridgeClient.request(
                BUSINESS_TYPE, request, StringUtils.hasText(traceId) ? traceId : null, responseHandler);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<BridgeStoredResult> query(@PathVariable String requestId) {
        return serviceBridgeClient.query(requestId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
