
package top.redjujubetree.servicebridge.example.server.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OcrEngineClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ocr.url:http://localhost:8000/ocr}")
    private String ocrUrl;

    /**
     * 这里的multipart只是当前OCR引擎的协议适配。
     * 数据库本身保存的是原始二进制，不保存multipart报文。
     */
    public OcrResponse recognize(OcrFileStorageService.StoredFile file)
            throws IOException {

        String suffix = safeSuffix(file.getExtension());
        Path tempFile = Files.createTempFile("service-bridge-ocr-", suffix);

        try {
            Files.write(tempFile, file.getBytes());

            File localFile = tempFile.toFile();
            FileSystemResource resource = new FileSystemResource(localFile) {
                @Override
                public String getFilename() {
                    // 保留扩展名，避免某些OCR引擎依赖扩展名识别格式。
                    return safeFilename(file.getOriginalName());
                }
            };

            HttpHeaders partHeaders = new HttpHeaders();
            if (file.getContentType() != null &&
                    !file.getContentType().trim().isEmpty()) {
                partHeaders.setContentType(
                        MediaType.parseMediaType(file.getContentType())
                );
            } else {
                partHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            }

            HttpEntity<FileSystemResource> filePart =
                    new HttpEntity<>(resource, partHeaders);

            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", filePart);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ResponseEntity<String> response = restTemplate.exchange(
                    ocrUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    String.class
            );

            return new OcrResponse(
                    response.getStatusCodeValue(),
                    response.getHeaders().getContentType() == null
                            ? null
                            : response.getHeaders().getContentType().toString(),
                    response.getBody()
            );
        } finally {
            // 无论OCR成功还是失败，都尽量删除临时文件。
            Files.deleteIfExists(tempFile);
        }
    }

    private String safeSuffix(String extension) {
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,10}")) {
            return ".bin";
        }
        return "." + extension;
    }

    private String safeFilename(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "upload.bin";
        }
        String name = originalName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        // 只把文件名放入multipart，不允许路径穿越。
        return name.isEmpty() ? "upload.bin" : name;
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
