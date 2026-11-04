package top.redjujubetree.servicebridge.example.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.redjujubetree.servicebridge.example.server.handler.OCRHandler;
import top.redjujubetree.servicebridge.example.server.ocr.InvoiceParserClient;
import top.redjujubetree.servicebridge.example.server.ocr.OcrBodyTrimmer;
import top.redjujubetree.servicebridge.example.server.ocr.OcrEngineClient;
import top.redjujubetree.servicebridge.example.server.ocr.OcrFileStorageService;
import top.redjujubetree.servicebridge.example.server.ocr.OcrResultService;
import top.redjujubetree.servicebridge.server.core.BridgeHandlerResult;
import top.redjujubetree.servicebridge.server.core.BridgeRequestContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class OCRHandlerTest {

    @Test
    void missingFileIdReturnsFailureWithoutCallingExternalServices() throws Exception {
        OcrFileStorageService files = mock(OcrFileStorageService.class);
        OcrEngineClient engine = mock(OcrEngineClient.class);
        OcrResultService results = mock(OcrResultService.class);
        InvoiceParserClient parser = mock(InvoiceParserClient.class);
        OcrBodyTrimmer trimmer = mock(OcrBodyTrimmer.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OCRHandler handler = new OCRHandler(files, engine, results, parser, trimmer, objectMapper);
        BridgeRequestContext context = new BridgeRequestContext();
        context.setPayload(objectMapper.createObjectNode());

        BridgeHandlerResult result = handler.handle(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("OCR_FILE_ID_MISSING");
        verifyNoInteractions(files, engine, results, parser, trimmer);
    }
}
