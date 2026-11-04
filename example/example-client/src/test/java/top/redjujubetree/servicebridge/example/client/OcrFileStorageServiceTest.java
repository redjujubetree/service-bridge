package top.redjujubetree.servicebridge.example.client;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import top.redjujubetree.servicebridge.example.client.ocr.OcrFileStorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OcrFileStorageServiceTest {

    @Test
    void saveStoresBase64AndHash() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        OcrFileStorageService storage = new OcrFileStorageService(jdbcTemplate);
        String base64 = "dGVzdCBpbWFnZQ==";
        String sha256 = "example-sha256";

        String fileId = storage.save(base64, sha256);

        assertThat(fileId).matches("[0-9a-f]{32}");
        verify(jdbcTemplate).update(anyString(), eq(fileId),
                eq((long) base64.length()), eq(base64), eq(sha256), eq(0));
    }
}
