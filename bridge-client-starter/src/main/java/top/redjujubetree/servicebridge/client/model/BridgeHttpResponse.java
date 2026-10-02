package top.redjujubetree.servicebridge.client.model;

import com.fasterxml.jackson.databind.JsonNode;

public class BridgeHttpResponse {

    private String requestId;
    private String code;
    private String message;
    private JsonNode data;

    public static BridgeHttpResponse of(String requestId, String code, String message, JsonNode data) {
        BridgeHttpResponse response = new BridgeHttpResponse();
        response.requestId = requestId;
        response.code = code;
        response.message = message;
        response.data = data;
        return response;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
