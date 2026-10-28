package top.redjujubetree.servicebridge.example.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import top.redjujubetree.servicebridge.example.server.handler.OCRHandler;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;

@SpringBootTest
public class OCRHandlerTest {

    private static final Logger log =
            LoggerFactory.getLogger(OCRHandlerTest.class);

    @Autowired
    private OCRHandler ocrHandler;

    @Test
    void handleTest() throws Exception {
        ocrHandler.handle(new BridgeRequestContext() {{
            setPayload(new ObjectMapper().createObjectNode().put("fileId", "7ab32c24937445669b4a5ae1da596e3e"));
        }});
    }
}
