package top.redjujubetree.servicebridge.example.client;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import top.redjujubetree.servicebridge.example.client.ocr.OcrFileStorageService;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OCRControllerTest {

    private static final Logger log =
            LoggerFactory.getLogger(OCRControllerTest.class);

    @Autowired
    private OcrFileStorageService fileStorageService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
//    @Transactional
    void saveStoresLocalFile() throws Exception {
        String LOCAL_FILE_PATH =
                "C:\\Users\\_Aurora\\Documents\\docker_space\\PPocr_v5\\input\\test8.png";
        File localFile = new File(LOCAL_FILE_PATH);
        assertThat(localFile)
                .as("本地测试文件不存在，请修改LOCAL_FILE_PATH: %s", LOCAL_FILE_PATH)
                .exists()
                .isFile();

        byte[] content = Files.readAllBytes(localFile.toPath());

        MockMultipartFile upload = new MockMultipartFile(
                "file",
                localFile.getName(),
                "image/png",
                content
        );

        String base64 =new String();
        String sha256 = sha256(content);
        String stored = fileStorageService.save(base64, sha256);
        log.info("stored: {}", stored);

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
