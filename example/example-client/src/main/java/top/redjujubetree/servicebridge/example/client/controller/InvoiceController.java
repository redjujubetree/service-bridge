package top.redjujubetree.servicebridge.example.client.controller;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.client.core.ServiceBridgeClient;
import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final ServiceBridgeClient serviceBridgeClient;

    public InvoiceController(ServiceBridgeClient serviceBridgeClient) {
        this.serviceBridgeClient = serviceBridgeClient;
    }

    @PostMapping("/validate")
    public DeferredResult<ResponseEntity<BridgeHttpResponse>> validate(@RequestBody JsonNode request) {
        return serviceBridgeClient.request("VALIDATE_INVOICE", request);
    }
}
