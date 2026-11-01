package org.example.ungdungchat.shared.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import java.util.UUID;

public class Packet {
    private static final Gson GSON = JsonUtils.GSON;

    private int version = 1;
    private String type;
    private String requestId;
    private String token;
    private String status = "OK"; // OK, ERROR, UNAUTHORIZED, FORBIDDEN, NOT_FOUND
    private String message;
    private JsonElement payload;

    public Packet() {
        this.requestId = UUID.randomUUID().toString();
    }

    public Packet(String type) {
        this();
        this.type = type;
    }

    public static Packet create(String type) {
        return new Packet(type);
    }

    public static Packet ok(String type, String requestId) {
        Packet p = new Packet(type);
        p.setRequestId(requestId);
        p.setStatus("OK");
        return p;
    }

    public static Packet error(String type, String requestId, String message) {
        Packet p = new Packet(type);
        p.setRequestId(requestId);
        p.setStatus("ERROR");
        p.setMessage(message);
        return p;
    }

    public static Packet fromJson(String json) {
        return GSON.fromJson(json, Packet.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public <T> T getPayloadAs(Class<T> clazz) {
        if (payload == null) return null;
        return GSON.fromJson(payload, clazz);
    }

    public <T> T getPayloadAs(java.lang.reflect.Type type) {
        if (payload == null) return null;
        return GSON.fromJson(payload, type);
    }

    public Packet withPayload(Object obj) {
        this.payload = GSON.toJsonTree(obj);
        return this;
    }

    // Getters and Setters
    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public JsonElement getPayload() {
        return payload;
    }

    public void setPayload(JsonElement payload) {
        this.payload = payload;
    }

    public boolean isOk() {
        return "OK".equalsIgnoreCase(status);
    }
}
