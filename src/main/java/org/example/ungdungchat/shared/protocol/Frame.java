package org.example.ungdungchat.shared.protocol;

public class Frame {
    public static final byte TYPE_JSON = 0x01;
    public static final byte TYPE_FILE_CHUNK = 0x02;
    public static final byte TYPE_HEARTBEAT_PING = 0x03;
    public static final byte TYPE_HEARTBEAT_PONG = 0x04;
    public static final byte TYPE_AUDIO_DATA = 0x05;

    public static final int MAX_FRAME_SIZE = 10 * 1024 * 1024; // 10MB per single frame max

    private final byte type;
    private final byte[] payload;

    public Frame(byte type, byte[] payload) {
        this.type = type;
        this.payload = payload != null ? payload : new byte[0];
    }

    public byte getType() {
        return type;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getLength() {
        return 1 + payload.length; // 1 byte type + payload length
    }
}
