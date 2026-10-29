
package top.redjujubetree.servicebridge.example.server.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OcrEngineClient {

    private final RestTemplate restTemplate;

    @Value("${ocr.url:http://localhost:8080/ocr}")
    private String ocrUrl;

    public OcrEngineClient(
            @Value("${ocr.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${ocr.read-timeout-ms:120000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 这里的JSON报文只是当前OCR引擎的协议适配。
     * 数据库本身保存的是原始二进制，不保存请求报文。
     */
    public OcrResponse recognize(OcrFileStorageService.StoredFile file)
            throws IOException {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("file", file.getFileData());
        body.put("fileType", 1);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(
                ocrUrl,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        return new OcrResponse(
                response.getStatusCodeValue(),
                response.getHeaders().getContentType() == null
                        ? null
                        : response.getHeaders().getContentType().toString(),
                response.getBody()
        );
    }

    public static class OcrResponse {
        private final int httpStatus;
        private final String contentType;
        private final String body;

        public OcrResponse(int httpStatus, String contentType, String body) {
            this.httpStatus = httpStatus;
            this.contentType = contentType;
            this.body = body;
        }

        public int getHttpStatus() { return httpStatus; }
        public String getContentType() { return contentType; }
        public String getBody() { return body; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("httpStatus", httpStatus);
            map.put("contentType", contentType);
            map.put("body", body);
            return map;
        }
    }
}
