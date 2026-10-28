
package top.redjujubetree.servicebridge.example.server.ocr;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
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
        private final int type;
        private final long fileSize;
        private final byte[] fileData;
        private final String sha256;
        private final int deleted;

        public StoredFile(
                String fileId,
                String originalName,
                String extension,
                int type,
                long fileSize,
                byte[] fileData,
                String sha256,
                int deleted) {
            this.fileId = fileId;
            this.originalName = originalName;
            this.extension = extension;
            this.type = type;
            this.fileSize = fileSize;
            this.fileData = fileData;
            this.sha256 = sha256;
            this.deleted = deleted;
        }

        public String getFileId() { return fileId; }
        public String getOriginalName() { return originalName; }
        public String getExtension() { return extension; }
        public int getType() { return type; }
        public long getFileSize() { return fileSize; }
        public byte[] getFileData() { return fileData; }
        public String getSha256() { return sha256; }
        public int getDeleted() { return deleted; }
    }

    public StoredFile load(String fileId) {
        List<StoredFile> metadata = jdbcTemplate.query(
                "SELECT file_id, original_name, extension, type, " +
                        "file_size, file_data, sha256, deleted FROM ocr_file_metadata WHERE file_id = ?",
                (rs, rowNum) -> new StoredFile(
                        rs.getString("file_id"),
                        rs.getString("original_name"),
                        rs.getString("extension"),
                        rs.getInt("type"),
                        rs.getLong("file_size"),
                        rs.getBytes("file_data"),
                        rs.getString("sha256"),
                        rs.getInt("deleted")
                ),
                fileId
        );

        if (metadata.isEmpty()) {
            throw new IllegalArgumentException("文件不存在: " + fileId);
        }

        StoredFile m = metadata.get(0);

        if(m.getDeleted()==1){
            return new StoredFile(
                    m.getFileId(), m.getOriginalName(), m.getExtension(), m.getType(),
                    m.getFileSize(), m.getFileData(), m.getSha256(), m.getDeleted()
            );
        }

        if (m.getFileData().length != m.getFileSize()) {
            throw new IllegalStateException("文件大小校验失败");
        }

        String actualSha256 = sha256(m.getFileData());
        if (!actualSha256.equalsIgnoreCase(m.getSha256())) {
            throw new IllegalStateException("文件SHA-256校验失败");
        }

        return new StoredFile(
                m.getFileId(), m.getOriginalName(), m.getExtension(), m.getType(),
                m.getFileSize(), m.getFileData(), m.getSha256(), m.getDeleted()
        );
    }

    public void deleteData(String fileId) {
        jdbcTemplate.update(
                "UPDATE ocr_file_metadata " +
                        "SET file_data = NULL, deleted = 1 " +
                        "WHERE file_id = ?",
                fileId
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
