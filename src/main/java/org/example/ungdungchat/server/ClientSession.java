package org.example.ungdungchat.server;

import org.example.ungdungchat.shared.protocol.Frame;
import org.example.ungdungchat.shared.protocol.Packet;
import org.example.ungdungchat.shared.protocol.ProtocolUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.time.Instant;
import java.util.UUID;

public class ClientSession {
    private final String sessionId;
    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private final Instant connectedAt;

    private String userId;
    private String username;
    private String displayName;
    private String sessionToken;
    private boolean isAuthenticated = false;
    private Instant lastPing = Instant.now();

    public ClientSession(Socket socket) throws IOException {
        this.sessionId = UUID.randomUUID().toString();
        this.socket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.connectedAt = Instant.now();
    }

    public void sendPacket(Packet packet) {
        try {
            ProtocolUtils.writePacket(outputStream, packet);
        } catch (IOException e) {
            close();
        }
    }

    public void sendFrame(byte type, byte[] payload) {
        try {
            ProtocolUtils.writeFrame(outputStream, type, payload);
        } catch (IOException e) {
            close();
        }
    }

    public void close() {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public String getRemoteAddress() {
        if (socket != null && socket.getRemoteSocketAddress() != null) {
            return socket.getRemoteSocketAddress().toString();
        }
        return "unknown";
    }

    // Getters & Setters
    public String getSessionId() { return sessionId; }
    public Socket getSocket() { return socket; }
    public DataInputStream getInputStream() { return inputStream; }
    public DataOutputStream getOutputStream() { return outputStream; }
    public Instant getConnectedAt() { return connectedAt; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getSessionToken() { return sessionToken; }
    public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
    public boolean isAuthenticated() { return isAuthenticated; }
    public void setAuthenticated(boolean authenticated) { isAuthenticated = authenticated; }
    public Instant getLastPing() { return lastPing; }
    public void setLastPing(Instant lastPing) { this.lastPing = lastPing; }
}
