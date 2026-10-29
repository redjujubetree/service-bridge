
package top.redjujubetree.servicebridge.example.client.ocr;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class OcrFileStorageService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;

    public OcrFileStorageService(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public String save(String base64, String sha256) throws IOException {
        if (base64 == null || base64.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        byte[] stored = base64.getBytes(StandardCharsets.UTF_8);
        long size = stored.length;
        if (size > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件超过允许的大小限制");
        }
        String fileId = UUID.randomUUID().toString().replace("-", "");

        jdbcTemplate.update(
                "INSERT INTO ocr_file_metadata " +
                        "(file_id, file_size, file_data, sha256, deleted) " +
                        "VALUES ( ?, ?, ?, ?, ?)",
                fileId, (long) stored.length, base64, sha256, 0
        );

        return fileId;
    }
}
