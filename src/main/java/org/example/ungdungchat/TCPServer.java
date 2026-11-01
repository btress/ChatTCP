package org.example.ungdungchat;

import com.google.gson.Gson;
import org.example.ungdungchat.server.ClientSession;
import org.example.ungdungchat.server.PacketRouter;
import org.example.ungdungchat.server.SessionManager;
import org.example.ungdungchat.server.db.UserRepository;
import org.example.ungdungchat.shared.model.Message;
import org.example.ungdungchat.shared.model.User;
import org.example.ungdungchat.shared.protocol.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TCPServer {
    private static final Logger LOGGER = Logger.getLogger(TCPServer.class.getName());
    private static final Gson GSON = JsonUtils.GSON;

    public static final int PORT = 8888;
    private volatile ExecutorService threadPool = Executors.newCachedThreadPool();
    private final SessionManager sessionManager = SessionManager.getInstance();
    private final UserRepository userRepo = new UserRepository();
    private final PacketRouter router = new PacketRouter(userRepo);

    private final File storageDir = new File("server_storage/files");
    private final Map<String, FileOutputStream> openUploads = new ConcurrentHashMap<>();
    private volatile boolean running;
    private volatile ServerSocket serverSocket;

    public TCPServer() {
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
    }

    public void run() {
        try {
            start();
            while (running) {
                Thread.sleep(250);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi server socket: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Server đang chạy.");
        }
        if (threadPool.isShutdown()) {
            threadPool = Executors.newCachedThreadPool();
        }

        ServerSocket listener = new ServerSocket(PORT);
        serverSocket = listener;
        running = true;
        LOGGER.info("TCP server started on port " + PORT);

        Thread acceptThread = new Thread(() -> acceptConnections(listener), "server-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptConnections(ServerSocket listener) {
        try {
            while (running) {
                Socket socket = listener.accept();
                threadPool.execute(() -> handleClientSocket(socket));
            }
        } catch (IOException e) {
            if (running) {
                LOGGER.log(Level.SEVERE, "Lỗi server socket: " + e.getMessage(), e);
            }
        }
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public boolean broadcastNotice(String message) {
        if (message == null || message.isBlank() || !isRunning()) {
            return false;
        }
        Packet packet = Packet.create(EventType.SERVER_NOTICE)
                .withPayload(Map.of("message", "[THÔNG BÁO HỆ THỐNG] " + message.trim()));
        sessionManager.broadcastToAll(packet);
        LOGGER.info("Broadcast sent.");
        return true;
    }

    public boolean kickUserByUsername(String username) {
        User user = userRepo.findByUsername(username);
        if (user == null) {
            return false;
        }
        sessionManager.kickUser(user.getId(), "Bị kick bởi quản trị viên");
        LOGGER.info("Kicked user: " + username);
        return true;
    }

    public boolean setUserBanned(String username, boolean banned) {
        User user = userRepo.findByUsername(username);
        if (user == null) {
            return false;
        }
        userRepo.setBanned(user.getId(), banned);
        if (banned) {
            sessionManager.kickUser(user.getId(), "Tài khoản bị cấm bởi quản trị viên");
        }
        LOGGER.info((banned ? "Banned" : "Unbanned") + " user: " + username);
        return true;
    }

    private void handleClientSocket(Socket socket) {
        ClientSession session = null;
        try {
            session = new ClientSession(socket);
            sessionManager.addSession(session);
            LOGGER.info("New connection from: " + session.getRemoteAddress());

            while (session.isConnected() && running) {
                Frame frame = ProtocolUtils.readFrame(session.getInputStream());
                if (frame == null) {
                    break; // Client closed connection
                }

                switch (frame.getType()) {
                    case Frame.TYPE_JSON -> {
                        Packet packet = ProtocolUtils.parsePacket(frame);
                        router.route(session, packet);
                    }
                    case Frame.TYPE_FILE_CHUNK -> {
                        handleFileChunk(session, frame.getPayload());
                    }
                    case Frame.TYPE_AUDIO_DATA -> {
                        handleAudioRelay(session, frame.getPayload());
                    }
                    case Frame.TYPE_HEARTBEAT_PING -> {
                        session.sendFrame(Frame.TYPE_HEARTBEAT_PONG, new byte[0]);
                    }
                    case Frame.TYPE_HEARTBEAT_PONG -> {
                        session.setLastPing(java.time.Instant.now());
                    }
                    default -> LOGGER.warning("Unknown frame type: " + frame.getType());
                }
            }
        } catch (IOException e) {
            // Disconnection
        } finally {
            if (session != null) {
                cleanUpSession(session);
            }
        }
    }

    private void handleFileChunk(ClientSession session, byte[] payload) {
        try {
            String jsonChunk = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
            FileChunk chunk = GSON.fromJson(jsonChunk, FileChunk.class);
            if (chunk == null) return;

            File targetFile = new File(storageDir, chunk.getFileId() + "_" + chunk.getFileName());
            FileOutputStream fos = openUploads.computeIfAbsent(chunk.getFileId(), k -> {
                try {
                    return new FileOutputStream(targetFile, true);
                } catch (IOException e) {
                    LOGGER.severe("Cannot open file stream for upload: " + e.getMessage());
                    return null;
                }
            });

            if (fos != null && chunk.getData() != null) {
                fos.write(chunk.getData());
                fos.flush();

                if (chunk.getChunkIndex() + 1 >= chunk.getTotalChunks()) {
                    fos.close();
                    openUploads.remove(chunk.getFileId());
                    LOGGER.info("File upload complete: " + chunk.getFileName() + " (" + chunk.getTotalSize() + " bytes)");

                    // Broadcast file message
                    Message msg = new Message(chunk.getConversationId(), session.getUserId(), session.getDisplayName(), chunk.getFileName());
                    boolean isImg = isImageExtension(chunk.getFileName());
                    msg.setMessageType(isImg ? Message.TYPE_IMAGE : Message.TYPE_FILE);
                    msg.setAttachmentName(chunk.getFileName());
                    msg.setAttachmentSize(chunk.getTotalSize());
                    msg.setAttachmentUrl(targetFile.getAbsolutePath());

                    Packet msgPacket = Packet.create(EventType.MESSAGE_SEND).withPayload(msg);
                    router.route(session, msgPacket);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error processing file chunk: " + e.getMessage());
        }
    }

    private boolean isImageExtension(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private void handleAudioRelay(ClientSession session, byte[] payload) {
        // Voice Call Audio Relay: forwards audio data to other participants
        // First 36 bytes can be target userId (or callId), rest is raw PCM audio
        if (payload == null || payload.length <= 36) return;
        String targetUserId = new String(payload, 0, 36, java.nio.charset.StandardCharsets.UTF_8).trim();
        byte[] audioBytes = Arrays.copyOfRange(payload, 36, payload.length);

        for (ClientSession target : sessionManager.getSessionsForUser(targetUserId)) {
            target.sendFrame(Frame.TYPE_AUDIO_DATA, audioBytes);
        }
    }

    private void cleanUpSession(ClientSession session) {
        session.close();
        sessionManager.removeSession(session);
        if (session.getUserId() != null) {
            userRepo.updateStatus(session.getUserId(), "OFFLINE");
            Packet offline = Packet.create(EventType.USER_OFFLINE)
                    .withPayload(Map.of("userId", session.getUserId(), "lastSeen", java.time.Instant.now().toString()));
            sessionManager.broadcastToAll(offline);
            LOGGER.info("User logged out/disconnected: " + session.getUsername());
        }
    }

    public void shutdown() {
        synchronized (this) {
            boolean wasRunning = running;
            running = false;
            ServerSocket listener = serverSocket;
            serverSocket = null;
            if (listener != null && !listener.isClosed()) {
                try {
                    listener.close();
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Could not close server socket.", e);
                }
            }

            if (wasRunning) {
                for (ClientSession session : sessionManager.getAllActiveSessions()) {
                    session.close();
                    sessionManager.removeSession(session);
                    if (session.getUserId() != null) {
                        userRepo.updateStatus(session.getUserId(), "OFFLINE");
                    }
                }
            }
            threadPool.shutdownNow();
            LOGGER.info("TCP server stopped.");
        }
    }

    public static void main(String[] args) {
        TCPServer server = new TCPServer();
        server.run();
    }
}
