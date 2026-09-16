package com.lnsoft.erp.servicebridge.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.lnsoft.erp.servicebridge.client.core.ServiceBridgeClient;
import com.lnsoft.erp.servicebridge.client.model.BridgeHttpResponse;
import com.lnsoft.erp.servicebridge.client.model.BridgeStoredResult;
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

@RestController
@RequestMapping("/reports")
public class ReportController {

    private static final String BUSINESS_TYPE = "GENERATE_REPORT";

    private final ServiceBridgeClient serviceBridgeClient;

    public ReportController(ServiceBridgeClient serviceBridgeClient) {
        this.serviceBridgeClient = serviceBridgeClient;
    }

    @PostMapping
    public DeferredResult<ResponseEntity<BridgeHttpResponse>> create(
            @RequestBody JsonNode request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        if (StringUtils.hasText(traceId)) {
            return serviceBridgeClient.request(BUSINESS_TYPE, request, traceId);
        }
        return serviceBridgeClient.request(BUSINESS_TYPE, request);
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<BridgeStoredResult> query(@PathVariable String requestId) {
        return serviceBridgeClient.query(requestId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
