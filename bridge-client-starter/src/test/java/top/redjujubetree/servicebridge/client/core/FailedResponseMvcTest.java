package top.redjujubetree.servicebridge.client.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import top.redjujubetree.servicebridge.client.model.BridgeHttpResponse;
import top.redjujubetree.servicebridge.client.model.BridgeStoredResult;
import top.redjujubetree.servicebridge.protocol.status.BridgeProcessStatus;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FailedResponseMvcTest {

    @Test
    void failedStoredResultIsHttp422AfterAsyncDispatch() throws Exception {
        TestController controller = new TestController();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        MvcResult pending = mvc.perform(get("/test-response"))
                .andExpect(request().asyncStarted())
                .andReturn();

        BridgeStoredResult stored = new BridgeStoredResult();
        stored.setRequestId("request-1");
        stored.setProcessStatus(BridgeProcessStatus.FAILED);
        stored.setErrorCode("BUSINESS_ERROR");
        stored.setErrorMessage("Processing failed");
        controller.result.setResult(new DefaultBridgeResponseHandler(new ObjectMapper()).fromStored(stored));

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.requestId").value("request-1"))
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Processing failed"));
    }

    @RestController
    private static class TestController {
        private final DeferredResult<ResponseEntity<BridgeHttpResponse>> result = new DeferredResult<>();

        @GetMapping("/test-response")
        public DeferredResult<ResponseEntity<BridgeHttpResponse>> respond() {
            return result;
        }
    }
}
