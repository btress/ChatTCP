package org.example.ungdungchat.shared.protocol;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProtocolUtils {

    /**
     * Reads a framed message from the input stream.
     * Frame format:
     * [4 bytes Total Length N] [1 byte Frame Type] [N-1 bytes Payload]
     */
    public static Frame readFrame(DataInputStream in) throws IOException {
        int length;
        try {
            length = in.readInt();
        } catch (EOFException e) {
            return null; // Socket disconnected gracefully
        }

        if (length < 1 || length > Frame.MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length: " + length);
        }

        byte type = in.readByte();
        int payloadLength = length - 1;
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            in.readFully(payload);
        }

        return new Frame(type, payload);
    }

    /**
     * Writes a framed message to the output stream safely.
     */
    public static void writeFrame(DataOutputStream out, byte type, byte[] payload) throws IOException {
        if (payload == null) payload = new byte[0];
        int length = 1 + payload.length;

        synchronized (out) {
            out.writeInt(length);
            out.writeByte(type);
            if (payload.length > 0) {
                out.write(payload);
            }
            out.flush();
        }
    }

    /**
     * Writes a JSON Packet frame.
     */
    public static void writePacket(DataOutputStream out, Packet packet) throws IOException {
        String json = packet.toJson();
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        writeFrame(out, Frame.TYPE_JSON, data);
    }

    /**
     * Parses a Packet from a JSON Frame.
     */
    public static Packet parsePacket(Frame frame) throws IOException {
        if (frame.getType() != Frame.TYPE_JSON) {
            throw new IOException("Frame is not of TYPE_JSON");
        }
        String json = new String(frame.getPayload(), StandardCharsets.UTF_8);
        return Packet.fromJson(json);
    }
}
