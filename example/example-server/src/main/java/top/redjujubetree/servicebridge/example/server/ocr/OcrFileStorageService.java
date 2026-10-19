
package top.redjujubetree.servicebridge.example.server.ocr;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.List;

@Service
public class OcrFileStorageService {

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
        private final byte[] bytes;

        public StoredFile(String fileId, String originalName, String extension,
                          String contentType, long fileSize, String sha256,
                          byte[] bytes) {
            this.fileId = fileId;
            this.originalName = originalName;
            this.extension = extension;
            this.contentType = contentType;
            this.fileSize = fileSize;
            this.sha256 = sha256;
            this.bytes = bytes;
        }

        public String getFileId() { return fileId; }
        public String getOriginalName() { return originalName; }
        public String getExtension() { return extension; }
        public String getContentType() { return contentType; }
        public long getFileSize() { return fileSize; }
        public String getSha256() { return sha256; }
        public byte[] getBytes() { return bytes; }
    }

    public StoredFile load(String fileId) {
        List<StoredFile> metadata = jdbcTemplate.query(
                "SELECT file_id, original_name, extension, content_type, " +
                        "file_size, sha256 FROM ocr_file_metadata WHERE file_id = ?",
                (rs, rowNum) -> new StoredFile(
                        rs.getString("file_id"),
                        rs.getString("original_name"),
                        rs.getString("extension"),
                        rs.getString("content_type"),
                        rs.getLong("file_size"),
                        rs.getString("sha256"),
                        null
                ),
                fileId
        );

        if (metadata.isEmpty()) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }

        StoredFile m = metadata.get(0);

        List<byte[]> chunks = jdbcTemplate.query(
                "SELECT chunk_data FROM ocr_file_chunk " +
                        "WHERE file_id = ? ORDER BY chunk_index ASC",
                (rs, rowNum) -> rs.getBytes("chunk_data"),
                fileId
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            for (byte[] chunk : chunks) {
                output.write(chunk);
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取文件分块失败", e);
        }

        byte[] bytes = output.toByteArray();

        if (bytes.length != m.getFileSize()) {
            throw new IllegalStateException("文件大小校验失败");
        }

        String actualSha256 = sha256(bytes);
        if (!actualSha256.equalsIgnoreCase(m.getSha256())) {
            throw new IllegalStateException("文件SHA-256校验失败");
        }

        return new StoredFile(
                m.getFileId(), m.getOriginalName(), m.getExtension(),
                m.getContentType(), m.getFileSize(), m.getSha256(), bytes
        );
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                result.append(String.format("%02x", b & 0xff));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("计算SHA-256失败", e);
        }
    }
}
