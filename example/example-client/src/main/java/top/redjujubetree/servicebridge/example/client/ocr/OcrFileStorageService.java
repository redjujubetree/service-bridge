
package top.redjujubetree.servicebridge.example.client.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class OcrFileStorageService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;

    private final Set<String> allowedExtensions;

    public OcrFileStorageService(
            JdbcTemplate jdbcTemplate,
            @Value("${ocr.upload.allowed-extensions}") String allowedExtensions) {
        this.jdbcTemplate = jdbcTemplate;
        this.allowedExtensions = parseExtensions(allowedExtensions);
    }

    private static Set<String> parseExtensions(String raw) {
        Set<String> extensions = new HashSet<>();
        if (raw == null) {
            return extensions;
        }
        for (String item : raw.split(",")) {
            String extension = item.trim().toLowerCase(Locale.ROOT);
            if (!extension.isEmpty()) {
                extensions.add(extension);
            }
        }
        return extensions;
    }

    public static class StoredFile {
        private final String fileId;
        private final String originalName;
        private final String extension;
        private final long fileSize;
        private final String sha256;

        public StoredFile(
                String fileId,
                String originalName,
                String extension,
                long fileSize,
                String sha256) {
            this.fileId = fileId;
            this.originalName = originalName;
            this.extension = extension;
            this.fileSize = fileSize;
            this.sha256 = sha256;
        }

        public String getFileId() { return fileId; }
        public String getOriginalName() { return originalName; }
        public String getExtension() { return extension; }
        public long getFileSize() { return fileSize; }
        public String getSha256() { return sha256; }
    }

    @Transactional
    public StoredFile save(MultipartFile file,byte[] bytes, String sha256) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        long size = file.getSize();
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过允许的大小限制");
        }
        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = getExtension(originalName);
        if (extension == null || !allowedExtensions.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }
        String type = extension.equals("pdf") ? "0" : "1";
        String fileId = UUID.randomUUID().toString().replace("-", "");

        jdbcTemplate.update(
                "INSERT INTO ocr_file_metadata " +
                        "(file_id, original_name, extension, type, " +
                        "file_size, file_data, sha256, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                fileId, originalName, extension , type,
                (long) bytes.length, bytes, sha256, 0
        );

        return new StoredFile(
                fileId, originalName, extension,
                bytes.length, sha256
        );
    }

    private String safeOriginalName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "upload.bin";
        }
        String normalized = name.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        return normalized.length() > 512
                ? normalized.substring(normalized.length() - 512)
                : normalized;
    }

    private String getExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
