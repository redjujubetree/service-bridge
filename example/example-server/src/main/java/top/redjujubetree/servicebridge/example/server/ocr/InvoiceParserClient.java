package top.redjujubetree.servicebridge.example.server.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 调用 python/invoice_parser.py 解析发票字段。
 * 机器上没装 Python 时，ProcessBuilder.start() 会直接抛 IOException。
 */
@Component
public class InvoiceParserClient {

    private static final Logger log = LoggerFactory.getLogger(InvoiceParserClient.class);

    @Value("${ocr.invoice-parser-script:python/invoice_parser.py}")
    private String scriptPath;

    private static final long TIMEOUT_SECONDS = 30;

    public String parse(String ocrBody) throws IOException, InterruptedException {

        if (ocrBody == null || ocrBody.trim().isEmpty()) {
            throw new IllegalArgumentException("OCR结果为空，无法解析");
        }

        Path script = Paths.get(scriptPath);
        if (!Files.isRegularFile(script)) {
            throw new IOException("找不到python脚本: " + script.toAbsolutePath());
        }

        List<String> command = new ArrayList<>();
        command.add(pythonCommand());
        command.add(script.toAbsolutePath().toString());

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(false);
        // 强制脚本按 UTF-8 输出
        builder.environment().put("PYTHONIOENCODING", "utf-8");

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new IOException("无法启动 " + command.get(0) + "，请确认已安装 Python 并加入 PATH", e);
        }

        // stderr 单独线程读取，避免管道写满后阻塞子进程
        StringBuilder stderr = new StringBuilder();
        Thread stderrReader = new Thread(() -> {
            try {
                stderr.append(readAll(process.getErrorStream()));
            } catch (IOException e) {
                log.warn("读取invoice_parser.py的stderr失败: {}", e.getMessage());
            }
        });
        stderrReader.setDaemon(true);
        stderrReader.start();

        // 脚本先把 stdin 读完再输出结果，这里写完后关闭流
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(ocrBody.getBytes(StandardCharsets.UTF_8));
        }

        String stdout = readAll(process.getInputStream());

        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("invoice_parser.py 执行超时(" + TIMEOUT_SECONDS + "s)");
        }

        stderrReader.join(TimeUnit.SECONDS.toMillis(2));

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IOException("invoice_parser.py 执行失败, exitCode=" + exitCode
                    + ", stderr=" + stderr.toString().trim());
        }

        return stdout;
    }

    /** 按操作系统选择 python 命令：Windows 用 python，其他平台用 python3。 */
    private static String pythonCommand() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "python" : "python3";
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, n);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
