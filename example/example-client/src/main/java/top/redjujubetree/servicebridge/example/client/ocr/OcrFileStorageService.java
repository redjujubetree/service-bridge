
package top.redjujubetree.servicebridge.example.client.ocr;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

@Service
public class OcrFileStorageService {

    /**
     * 每个数据库分块默认 1 MiB。
     * 将来可抽取到配置文件，不属于 ServiceBridge 协议配置。
     */
    public static final int CHUNK_SIZE = 1024 * 1024;

    /**
     * 示例限制：单个文件最多 50 MiB。
     * 需要与 Spring multipart 上传限制保持一致。
     */
    public static final long MAX_FILE_SIZE = 50L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;

    public OcrFileStorageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static class StoredFile {
        private final String fileId;
        private final String originalName;
        private final String extension;
        private final String contentType;
        private final long fileSize;
        private final String sha256;

        public StoredFile(
                String fileId,
                String originalName,
                String extension,
                String contentType,
                long fileSize,
                String sha256) {
            this.fileId = fileId;
            this.originalName = originalName;
            this.extension = extension;
            this.contentType = contentType;
            this.fileSize = fileSize;
            this.sha256 = sha256;
        }

        public String getFileId() { return fileId; }
        public String getOriginalName() { return originalName; }
        public String getExtension() { return extension; }
        public String getContentType() { return contentType; }
        public long getFileSize() { return fileSize; }
        public String getSha256() { return sha256; }
    }

    @Transactional
    public StoredFile save(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        long size = file.getSize();
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过允许的大小限制");
        }

        // 将原始文件读取为字节。示例限制了最大文件大小。
        byte[] bytes = file.getBytes();
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String originalName = safeOriginalName(file.getOriginalFilename());
        String extension = getExtension(originalName);
        String contentType = file.getContentType();
        String sha256 = sha256(bytes);

        int chunkCount = (bytes.length + CHUNK_SIZE - 1) / CHUNK_SIZE;

        jdbcTemplate.update(
                "INSERT INTO ocr_file_metadata " +
                        "(file_id, original_name, extension, content_type, " +
                        "file_size, chunk_size, chunk_count, sha256) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                fileId, originalName, extension, contentType,
                (long) bytes.length, CHUNK_SIZE, chunkCount, sha256
        );

        for (int index = 0; index < chunkCount; index++) {
            int start = index * CHUNK_SIZE;
            int end = Math.min(start + CHUNK_SIZE, bytes.length);
            byte[] chunk = new byte[end - start];
            System.arraycopy(bytes, start, chunk, 0, chunk.length);

            jdbcTemplate.update(
                    "INSERT INTO ocr_file_chunk " +
                            "(file_id, chunk_index, chunk_data, chunk_size) " +
                            "VALUES (?, ?, ?, ?)",
                    fileId, index, chunk, chunk.length
            );
        }

        return new StoredFile(
                fileId, originalName, extension, contentType,
                bytes.length, sha256
        );
    }

    /**
     * 不允许客户端传来的文件名直接作为服务器上的路径。
     */
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

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            // Java 标准环境必须提供 SHA-256。
            throw new IllegalStateException("当前环境不支持SHA-256", e);
        }
    }
}
