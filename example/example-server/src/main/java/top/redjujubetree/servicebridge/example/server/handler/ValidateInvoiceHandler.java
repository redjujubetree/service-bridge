package top.redjujubetree.servicebridge.example.server.handler;

import com.fasterxml.jackson.databind.JsonNode;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;
import top.redjujubetree.servicebridge.server.core.BridgeRequestHandler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ValidateInvoiceHandler implements BridgeRequestHandler {

    @Override
    public String businessType() {
        return "VALIDATE_INVOICE";
    }

    @Override
    public BridgeHandlerResult handle(BridgeRequestContext context) {
        JsonNode payload = context.getPayload();
        String invoiceNumber = payload.path("invoiceNumber").asText("").trim();
        JsonNode amountNode = payload.path("amount");
        if (invoiceNumber.isEmpty() || !amountNode.isNumber()
                || amountNode.decimalValue().compareTo(BigDecimal.ZERO) <= 0) {
            return BridgeHandlerResult.failure(
                    "INVALID_INVOICE", "invoiceNumber and a positive amount are required");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", context.getRequestId());
        result.put("invoiceNumber", invoiceNumber);
        result.put("amount", amountNode.decimalValue());
        result.put("valid", true);
        return BridgeHandlerResult.success(result);
    }
}
