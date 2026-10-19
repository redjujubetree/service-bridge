package top.redjujubetree.servicebridge.example.server.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateInvoiceHandlerTest {

    private final ValidateInvoiceHandler handler = new ValidateInvoiceHandler();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validatesInvoice() throws Exception {
        BridgeHandlerResult result = handler.handle(context("{\"invoiceNumber\":\"INV-001\",\"amount\":128.50}"));

        assertThat(result.isSuccess()).isTrue();
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertThat(data.get("invoiceNumber")).isEqualTo("INV-001");
        assertThat((BigDecimal) data.get("amount")).isEqualByComparingTo("128.50");
    }

    @Test
    void rejectsInvalidAmount() throws Exception {
        BridgeHandlerResult result = handler.handle(context("{\"invoiceNumber\":\"INV-001\",\"amount\":0}"));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_INVOICE");
    }

    private BridgeRequestContext context(String json) throws Exception {
        BridgeRequestContext context = new BridgeRequestContext();
        context.setRequestId("request-1");
        context.setBusinessType("VALIDATE_INVOICE");
        context.setPayload(mapper.readTree(json));
        return context;
    }
}
