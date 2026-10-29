package top.redjujubetree.servicebridge.example.server.ocr;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;

/**
 * OCR 服务会把图片以 base64 回传（如 ocrResults[].ocrImage / inputImage），
 * 体积往往是原始文件的十几倍，而发票字段解析只需要文本和坐标。
 *
 * 裁剪规则：
 * 1. 字段名包含 image/img（忽略大小写）的整段丢弃；
 * 2. 字符串长度超过阈值的一律替换成占位符，兜底防止图片换了字段名。
 */
@Component
public class OcrBodyTrimmer {

    private static final Logger log = LoggerFactory.getLogger(OcrBodyTrimmer.class);

    /** 单个字符串超过这个长度就不再搬运。正常文本项远小于此值。 */
    private static final int MAX_STRING_LENGTH = 8192;

    private static final String OMITTED = "<omitted>";

    private final JsonFactory jsonFactory;

    public OcrBodyTrimmer(ObjectMapper objectMapper) {
        this.jsonFactory = objectMapper.getFactory();
    }

    /**
     * 返回裁剪后的JSON。body不是合法JSON时原样返回，交给下游报错。
     */
    public String trim(String body) {

        if (body == null || body.isEmpty()) {
            return body;
        }

        StringWriter out = new StringWriter(Math.min(body.length(), 64 * 1024));
        try (JsonParser parser = jsonFactory.createParser(body);
             JsonGenerator generator = jsonFactory.createGenerator(out)) {

            if (parser.nextToken() == null) {
                return body;
            }
            copy(parser, generator);

        } catch (IOException e) {
            log.warn("OCR body裁剪失败，按原文传递: {}", e.getMessage());
            return body;
        }

        String trimmed = out.toString();
        if (trimmed.length() < body.length()) {
            log.info("OCR body裁剪: {} -> {} 字符", body.length(), trimmed.length());
        }
        return trimmed;
    }

    /** 递归搬运当前token，图片类字段直接跳过。 */
    private void copy(JsonParser parser, JsonGenerator generator) throws IOException {

        JsonToken token = parser.currentToken();

        if (token == JsonToken.START_OBJECT) {
            generator.writeStartObject();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String name = parser.currentName();
                generator.writeFieldName(name);
                if (parser.nextToken() == null) {
                    break;
                }
                if (shouldDrop(name)) {
                    generator.writeString(OMITTED);
                    parser.skipChildren();
                } else {
                    copy(parser, generator);
                }
            }
            generator.writeEndObject();

        } else if (token == JsonToken.START_ARRAY) {
            generator.writeStartArray();
            while (true) {
                JsonToken current = parser.nextToken();
                if (current == null || current == JsonToken.END_ARRAY) {
                    break;
                }
                copy(parser, generator);
            }
            generator.writeEndArray();

        } else if (token == JsonToken.VALUE_STRING) {
            if (parser.getTextLength() > MAX_STRING_LENGTH) {
                generator.writeString(OMITTED);
            } else {
                generator.writeString(parser.getText());
            }

        } else {
            generator.copyCurrentStructure(parser);
        }
    }

    private boolean shouldDrop(String fieldName) {
        String name = fieldName.toLowerCase();
        return name.contains("image") || name.contains("img");
    }
}
