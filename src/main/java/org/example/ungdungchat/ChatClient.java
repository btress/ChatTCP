package org.example.ungdungchat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.example.ungdungchat.shared.model.*;
import org.example.ungdungchat.shared.protocol.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChatClient {
    private static final Logger LOGGER = Logger.getLogger(ChatClient.class.getName());
    private static final Gson GSON = JsonUtils.GSON;

    public interface ClientListener {
        void onMessageReceived(Message message);
        void onMessageEdited(String messageId, String newContent);
        void onMessageRecalled(String messageId);
        void onMessageReacted(String messageId, Map<String, List<String>> reactions);
        void onMessageRead(String messageId, String userId);
        void onTyping(String conversationId, String userId, String userName, boolean isTyping);
        void onUserOnline(String userId, String username, String displayName);
        void onUserOffline(String userId);
        void onFriendRequestReceived(FriendRequest req);
        void onCallSignal(CallSession call, String signalType);
        void onAudioReceived(byte[] audioData);
        void onServerNotice(String message);
        void onDisconnected();
        void onReconnected();
    }

    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream is;
    private DataOutputStream os;

    private User currentUser;
    private String sessionToken;
    private volatile ClientListener listener;
    private volatile boolean running = false;

    private final Map<String, CompletableFuture<Packet>> pendingRequests = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public String getUserName() {
        return currentUser != null ? currentUser.getDisplayName() : "User";
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void connect() throws IOException {
        socket = new Socket(host, port);
        os = new DataOutputStream(socket.getOutputStream());
        is = new DataInputStream(socket.getInputStream());
        running = true;

        Thread receiveThread = new Thread(this::receiveLoop, "client-receive-thread");
        receiveThread.setDaemon(true);
        receiveThread.start();

        startHeartbeat();
    }

    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (isConnected()) {
                try {
                    ProtocolUtils.writeFrame(os, Frame.TYPE_HEARTBEAT_PING, new byte[0]);
                } catch (IOException ignored) {}
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    private void receiveLoop() {
        try {
            while (running && isConnected()) {
                Frame frame = ProtocolUtils.readFrame(is);
                if (frame == null) break;

                switch (frame.getType()) {
                    case Frame.TYPE_JSON -> {
                        Packet packet = ProtocolUtils.parsePacket(frame);
                        handleIncomingPacket(packet);
                    }
                    case Frame.TYPE_AUDIO_DATA -> {
                        if (listener != null) {
                            listener.onAudioReceived(frame.getPayload());
                        }
                    }
                    case Frame.TYPE_HEARTBEAT_PING -> {
                        ProtocolUtils.writeFrame(os, Frame.TYPE_HEARTBEAT_PONG, new byte[0]);
                    }
                    case Frame.TYPE_HEARTBEAT_PONG -> {
                        // Heartbeat ack
                    }
                }
            }
        } catch (IOException e) {
            // Socket disconnected
        } finally {
            running = false;
            if (listener != null) {
                listener.onDisconnected();
            }
        }
    }

    private void handleIncomingPacket(Packet packet) {
        // Complete any pending request futures
        if (packet.getRequestId() != null) {
            CompletableFuture<Packet> future = pendingRequests.remove(packet.getRequestId());
            if (future != null) {
                future.complete(packet);
                return;
            }
        }

        if (listener == null) return;

        String type = packet.getType();
        if (type == null) return;

        switch (type) {
            case EventType.MESSAGE_RECEIVE -> {
                Message msg = packet.getPayloadAs(Message.class);
                if (msg != null) listener.onMessageReceived(msg);
            }
            case EventType.MESSAGE_EDIT -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onMessageEdited(data.get("messageId"), data.get("content"));
            }
            case EventType.MESSAGE_RECALL -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onMessageRecalled(data.get("messageId"));
            }
            case EventType.MESSAGE_REACT -> {
                Map<String, Object> data = packet.getPayloadAs(new TypeToken<Map<String, Object>>() {}.getType());
                if (data != null) {
                    String msgId = (String) data.get("messageId");
                    Map<String, List<String>> reacts = GSON.fromJson(GSON.toJson(data.get("reactions")), new TypeToken<Map<String, List<String>>>() {}.getType());
                    listener.onMessageReacted(msgId, reacts);
                }
            }
            case EventType.MESSAGE_READ -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onMessageRead(data.get("messageId"), data.get("userId"));
            }
            case EventType.TYPING_START -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onTyping(data.get("conversationId"), data.get("userId"), data.get("userName"), true);
            }
            case EventType.TYPING_STOP -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onTyping(data.get("conversationId"), data.get("userId"), data.get("userName"), false);
            }
            case EventType.USER_ONLINE -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onUserOnline(data.get("userId"), data.get("username"), data.get("displayName"));
            }
            case EventType.USER_OFFLINE -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onUserOffline(data.get("userId"));
            }
            case EventType.FRIEND_REQUEST -> {
                FriendRequest req = packet.getPayloadAs(FriendRequest.class);
                if (req != null) listener.onFriendRequestReceived(req);
            }
            case EventType.CALL_REQUEST, EventType.CALL_ACCEPT, EventType.CALL_REJECT, EventType.CALL_END -> {
                CallSession call = packet.getPayloadAs(CallSession.class);
                if (call != null) listener.onCallSignal(call, type);
            }
            case EventType.SERVER_NOTICE -> {
                Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
                if (data != null) listener.onServerNotice(data.get("message"));
            }
        }
    }

    public CompletableFuture<Packet> sendRequest(Packet packet) {
        CompletableFuture<Packet> future = new CompletableFuture<>();
        if (!isConnected()) {
            future.completeExceptionally(new IOException("Chưa kết nối tới Server"));
            return future;
        }

        pendingRequests.put(packet.getRequestId(), future);
        try {
            if (sessionToken != null) packet.setToken(sessionToken);
            ProtocolUtils.writePacket(os, packet);
        } catch (IOException e) {
            pendingRequests.remove(packet.getRequestId());
            future.completeExceptionally(e);
        }
        return future;
    }

    // =========================================================================
    // Authentication APIs
    // =========================================================================

    public CompletableFuture<Packet> register(String username, String email, String password, String displayName) {
        Packet p = Packet.create(EventType.AUTH_REGISTER)
                .withPayload(Map.of(
                        "username", username,
                        "email", email != null ? email : "",
                        "password", password,
                        "displayName", displayName != null ? displayName : username
                ));
        return sendRequest(p);
    }

    public CompletableFuture<User> login(String identifier, String password) {
        Packet p = Packet.create(EventType.AUTH_LOGIN)
                .withPayload(Map.of("identifier", identifier, "password", password));

        return sendRequest(p).thenApply(resp -> {
            if (!resp.isOk()) {
                throw new RuntimeException(resp.getMessage());
            }
            Map<String, Object> data = resp.getPayloadAs(new TypeToken<Map<String, Object>>() {}.getType());
            this.sessionToken = (String) data.get("token");
            this.currentUser = GSON.fromJson(GSON.toJson(data.get("user")), User.class);
            return this.currentUser;
        });
    }

    public void logout() {
        if (isConnected()) {
            sendRequest(Packet.create(EventType.AUTH_LOGOUT));
        }
        this.currentUser = null;
        this.sessionToken = null;
    }

    // =========================================================================
    // Conversation & Message APIs
    // =========================================================================

    public CompletableFuture<List<Conversation>> getConversations() {
        return sendRequest(Packet.create(EventType.CONVERSATION_LIST)).thenApply(resp -> {
            if (!resp.isOk()) return Collections.emptyList();
            return GSON.fromJson(resp.getPayload(), new TypeToken<List<Conversation>>() {}.getType());
        });
    }

    public CompletableFuture<Conversation> createConversation(String type, String name, List<String> memberIds) {
        Packet p = Packet.create(EventType.CONVERSATION_CREATE)
                .withPayload(Map.of("type", type, "name", name != null ? name : "", "memberIds", memberIds));
        return sendRequest(p).thenApply(resp -> resp.getPayloadAs(Conversation.class));
    }

    public CompletableFuture<Message> sendMessage(Message message) {
        Packet p = Packet.create(EventType.MESSAGE_SEND).withPayload(message);
        return sendRequest(p).thenApply(resp -> {
            if (!resp.isOk()) {
                throw new CompletionException(new IOException(
                        resp.getMessage() != null ? resp.getMessage() : "Gửi tin nhắn thất bại"));
            }
            Message sentMessage = resp.getPayloadAs(Message.class);
            if (sentMessage == null) {
                throw new CompletionException(new IOException("Server không xác nhận tin nhắn."));
            }
            return sentMessage;
        });
    }

    public CompletableFuture<List<Message>> getMessageHistory(String conversationId, int limit) {
        Packet p = Packet.create(EventType.MESSAGE_HISTORY)
                .withPayload(Map.of("conversationId", conversationId, "limit", limit));
        return sendRequest(p).thenApply(resp -> {
            if (!resp.isOk()) return Collections.emptyList();
            return GSON.fromJson(resp.getPayload(), new TypeToken<List<Message>>() {}.getType());
        });
    }

    public void editMessage(String messageId, String newContent) {
        Packet p = Packet.create(EventType.MESSAGE_EDIT)
                .withPayload(Map.of("messageId", messageId, "content", newContent));
        sendRequest(p);
    }

    public void recallMessage(String messageId) {
        Packet p = Packet.create(EventType.MESSAGE_RECALL)
                .withPayload(Map.of("messageId", messageId));
        sendRequest(p);
    }

    public void reactMessage(String messageId, String emoji, String action) {
        Packet p = Packet.create(EventType.MESSAGE_REACT)
                .withPayload(Map.of("messageId", messageId, "emoji", emoji, "action", action));
        sendRequest(p);
    }

    public void markMessageRead(String conversationId, String messageId) {
        Packet p = Packet.create(EventType.MESSAGE_READ)
                .withPayload(Map.of("conversationId", conversationId, "messageId", messageId != null ? messageId : ""));
        sendRequest(p);
    }

    public void sendTyping(String conversationId, boolean isTyping) {
        Packet p = Packet.create(isTyping ? EventType.TYPING_START : EventType.TYPING_STOP)
                .withPayload(Map.of("conversationId", conversationId));
        sendRequest(p);
    }

    // =========================================================================
    // Chunked File Transfer API
    // =========================================================================

    public CompletableFuture<Void> sendFileChunked(String conversationId, File file, Consumer<Double> progressCallback) {
        return CompletableFuture.runAsync(() -> {
            try {
                long totalSize = file.length();
                int chunkSize = 64 * 1024; // 64KB per chunk
                int totalChunks = (int) Math.ceil((double) totalSize / chunkSize);
                String fileId = UUID.randomUUID().toString();

                try (FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[chunkSize];
                    int bytesRead;
                    int chunkIndex = 0;

                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] actualData = bytesRead == chunkSize ? buffer : Arrays.copyOf(buffer, bytesRead);
                        FileChunk chunk = new FileChunk(fileId, conversationId, file.getName(), totalSize, chunkIndex, totalChunks, actualData, "");

                        String json = GSON.toJson(chunk);
                        byte[] framePayload = json.getBytes(StandardCharsets.UTF_8);

                        ProtocolUtils.writeFrame(os, Frame.TYPE_FILE_CHUNK, framePayload);

                        chunkIndex++;
                        if (progressCallback != null) {
                            progressCallback.accept((double) chunkIndex / totalChunks);
                        }
                    }
                }
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    // =========================================================================
    // Voice Call Audio Streaming
    // =========================================================================

    public void sendAudioData(String targetUserId, byte[] audioData) {
        try {
            // First 36 bytes: targetUserId formatted to 36 chars
            String paddedTarget = String.format("%-36s", targetUserId);
            byte[] targetBytes = paddedTarget.getBytes(StandardCharsets.UTF_8);

            byte[] payload = new byte[36 + audioData.length];
            System.arraycopy(targetBytes, 0, payload, 0, 36);
            System.arraycopy(audioData, 0, payload, 36, audioData.length);

            ProtocolUtils.writeFrame(os, Frame.TYPE_AUDIO_DATA, payload);
        } catch (IOException ignored) {}
    }

    public void sendCallSignal(CallSession session, String signalType) {
        Packet p = Packet.create(signalType).withPayload(session);
        sendRequest(p);
    }

    // =========================================================================
    // Friends & Profile APIs
    // =========================================================================

    public CompletableFuture<List<User>> searchUsers(String query) {
        Packet p = Packet.create(EventType.USER_SEARCH).withPayload(Map.of("query", query));
        return sendRequest(p).thenApply(resp -> GSON.fromJson(resp.getPayload(), new TypeToken<List<User>>() {}.getType()));
    }

    public CompletableFuture<List<User>> getFriends() {
        return sendRequest(Packet.create(EventType.FRIEND_LIST)).thenApply(resp -> GSON.fromJson(resp.getPayload(), new TypeToken<List<User>>() {}.getType()));
    }

    public CompletableFuture<Packet> sendFriendRequest(String toUserId) {
        return sendRequest(Packet.create(EventType.FRIEND_REQUEST).withPayload(Map.of("toUserId", toUserId)));
    }

    public CompletableFuture<Packet> acceptFriendRequest(String requestId) {
        return sendRequest(Packet.create(EventType.FRIEND_ACCEPT).withPayload(Map.of("requestId", requestId)));
    }

    public CompletableFuture<Packet> rejectFriendRequest(String requestId) {
        return sendRequest(Packet.create(EventType.FRIEND_REJECT).withPayload(Map.of("requestId", requestId)));
    }

    public CompletableFuture<Packet> blockUser(String targetUserId) {
        return sendRequest(Packet.create(EventType.USER_BLOCK).withPayload(Map.of("targetUserId", targetUserId)));
    }

    public CompletableFuture<User> updateProfile(String displayName, String bio, String avatarUrl) {
        Packet p = Packet.create(EventType.USER_PROFILE_UPDATE).withPayload(Map.of(
                "displayName", displayName != null ? displayName : "",
                "bio", bio != null ? bio : "",
                "avatarUrl", avatarUrl != null ? avatarUrl : ""
        ));
        return sendRequest(p).thenApply(resp -> resp.getPayloadAs(User.class));
    }

    public void close() {
        running = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        heartbeatScheduler.shutdownNow();
    }
}
