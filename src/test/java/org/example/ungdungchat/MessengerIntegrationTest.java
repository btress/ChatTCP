package org.example.ungdungchat;

import org.example.ungdungchat.shared.model.CallSession;
import org.example.ungdungchat.shared.model.Conversation;
import org.example.ungdungchat.shared.model.FriendRequest;
import org.example.ungdungchat.shared.model.Message;
import org.example.ungdungchat.shared.model.User;
import org.example.ungdungchat.shared.protocol.Packet;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MessengerIntegrationTest {

    private static TCPServer server;
    private static Thread serverThread;

    private static ChatClient client1;
    private static ChatClient client2;

    private static User user1;
    private static User user2;

    @BeforeAll
    public static void setup() throws Exception {
        // Start TCP Server
        server = new TCPServer();
        serverThread = new Thread(server::run, "test-server-thread");
        serverThread.setDaemon(true);
        serverThread.start();

        // Give server 500ms to bind port
        Thread.sleep(500);

        client1 = new ChatClient("localhost", TCPServer.PORT);
        client1.connect();

        client2 = new ChatClient("localhost", TCPServer.PORT);
        client2.connect();
    }

    @AfterAll
    public static void teardown() {
        if (client1 != null) client1.close();
        if (client2 != null) client2.close();
        if (server != null) server.shutdown();
    }

    @Test
    @Order(1)
    public void testRegisterAndLogin() throws Exception {
        // Register user1
        Packet reg1 = client1.register("testuser1", "user1@test.com", "password123", "User One").get(5, TimeUnit.SECONDS);
        assertTrue(reg1.isOk(), "Đăng ký user1 phải thành công: " + reg1.getMessage());

        // Login user1
        user1 = client1.login("testuser1", "password123").get(5, TimeUnit.SECONDS);
        assertNotNull(user1);
        assertEquals("testuser1", user1.getUsername());

        // Register user2
        Packet reg2 = client2.register("testuser2", "user2@test.com", "password123", "User Two").get(5, TimeUnit.SECONDS);
        assertTrue(reg2.isOk(), "Đăng ký user2 phải thành công: " + reg2.getMessage());

        // Login user2
        user2 = client2.login("testuser2", "password123").get(5, TimeUnit.SECONDS);
        assertNotNull(user2);
        assertEquals("testuser2", user2.getUsername());
    }

    @Test
    @Order(2)
    public void testSendMessageAndReceiveInPublicRoom() throws Exception {
        CompletableFuture<Message> receivedFuture = new CompletableFuture<>();

        client2.setListener(new ChatClient.ClientListener() {
            @Override
            public void onMessageReceived(Message message) {
                receivedFuture.complete(message);
            }
            @Override public void onMessageEdited(String id, String content) {}
            @Override public void onMessageRecalled(String id) {}
            @Override public void onMessageReacted(String id, Map<String, List<String>> reactions) {}
            @Override public void onMessageRead(String id, String userId) {}
            @Override public void onTyping(String convId, String uid, String name, boolean typing) {}
            @Override public void onUserOnline(String uid, String u, String d) {}
            @Override public void onUserOffline(String uid) {}
            @Override public void onFriendRequestReceived(FriendRequest req) {}
            @Override public void onCallSignal(CallSession call, String signal) {}
            @Override public void onAudioReceived(byte[] audioData) {}
            @Override public void onServerNotice(String msg) {}
            @Override public void onDisconnected() {}
            @Override public void onReconnected() {}
        });

        // Client 1 sends message to public room
        Message msg = new Message("public_room", user1.getId(), user1.getDisplayName(), "Xin chào từ Client 1!");
        client1.sendMessage(msg).get(5, TimeUnit.SECONDS);

        // Client 2 must receive it
        Message received = receivedFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("Xin chào từ Client 1!", received.getContent());
        assertEquals(user1.getId(), received.getSenderId());

        Message secondMessage = new Message(
                "public_room", user1.getId(), user1.getDisplayName(), "Tin nhắn thứ hai");
        client1.sendMessage(secondMessage).get(5, TimeUnit.SECONDS);
        List<Message> history = client1.getMessageHistory("public_room", 50).get(5, TimeUnit.SECONDS);
        assertTrue(history.stream().anyMatch(message ->
                "Tin nhắn thứ hai".equals(message.getContent())),
                "Tin nhắn thứ hai phải được lưu và xuất hiện trong lịch sử.");
    }

    @Test
    @Order(3)
    public void testFriendRequestAndDirectChat() throws Exception {
        CompletableFuture<FriendRequest> reqFuture = new CompletableFuture<>();

        client2.setListener(new ChatClient.ClientListener() {
            @Override public void onMessageReceived(Message message) {}
            @Override public void onMessageEdited(String id, String content) {}
            @Override public void onMessageRecalled(String id) {}
            @Override public void onMessageReacted(String id, Map<String, List<String>> reactions) {}
            @Override public void onMessageRead(String id, String userId) {}
            @Override public void onTyping(String convId, String uid, String name, boolean typing) {}
            @Override public void onUserOnline(String uid, String u, String d) {}
            @Override public void onUserOffline(String uid) {}
            @Override
            public void onFriendRequestReceived(FriendRequest req) {
                reqFuture.complete(req);
            }
            @Override public void onCallSignal(CallSession call, String signal) {}
            @Override public void onAudioReceived(byte[] audioData) {}
            @Override public void onServerNotice(String msg) {}
            @Override public void onDisconnected() {}
            @Override public void onReconnected() {}
        });

        // Client 1 sends friend request to Client 2
        Packet sentReq = client1.sendFriendRequest(user2.getId()).get(5, TimeUnit.SECONDS);
        assertTrue(sentReq.isOk());

        FriendRequest receivedReq = reqFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(receivedReq);
        assertEquals(user1.getId(), receivedReq.getFromUserId());

        // Client 2 accepts request
        Packet accepted = client2.acceptFriendRequest(receivedReq.getId()).get(5, TimeUnit.SECONDS);
        assertTrue(accepted.isOk());

        // Create direct conversation
        Conversation conv = client1.createConversation(Conversation.TYPE_DIRECT, "Direct", List.of(user1.getId(), user2.getId())).get(5, TimeUnit.SECONDS);
        assertNotNull(conv);
        assertTrue(conv.getMemberIds().contains(user1.getId()));
        assertTrue(conv.getMemberIds().contains(user2.getId()));

        CompletableFuture<Message> directMessageFuture = new CompletableFuture<>();
        client2.setListener(new ChatClient.ClientListener() {
            @Override
            public void onMessageReceived(Message message) {
                if (conv.getId().equals(message.getConversationId())) {
                    directMessageFuture.complete(message);
                }
            }
            @Override public void onMessageEdited(String id, String content) {}
            @Override public void onMessageRecalled(String id) {}
            @Override public void onMessageReacted(String id, Map<String, List<String>> reactions) {}
            @Override public void onMessageRead(String id, String userId) {}
            @Override public void onTyping(String convId, String uid, String name, boolean typing) {}
            @Override public void onUserOnline(String uid, String u, String d) {}
            @Override public void onUserOffline(String uid) {}
            @Override public void onFriendRequestReceived(FriendRequest req) {}
            @Override public void onCallSignal(CallSession call, String signal) {}
            @Override public void onAudioReceived(byte[] audioData) {}
            @Override public void onServerNotice(String msg) {}
            @Override public void onDisconnected() {}
            @Override public void onReconnected() {}
        });

        Message directMessage = new Message(
                conv.getId(), user1.getId(), user1.getDisplayName(), "Tin nhắn direct realtime");
        client1.sendMessage(directMessage).get(5, TimeUnit.SECONDS);

        Message receivedDirectMessage = directMessageFuture.get(5, TimeUnit.SECONDS);
        assertEquals(conv.getId(), receivedDirectMessage.getConversationId());
        assertEquals("Tin nhắn direct realtime", receivedDirectMessage.getContent());
        assertTrue(client2.getConversations().get(5, TimeUnit.SECONDS).stream()
                .anyMatch(existing -> conv.getId().equals(existing.getId())),
                "Client nhận phải thấy cuộc trò chuyện direct mới trong danh sách.");
    }

    @Test
    @Order(4)
    public void testMessageEditRecallAndReactions() throws Exception {
        CompletableFuture<String> editedFuture = new CompletableFuture<>();
        CompletableFuture<String> recalledFuture = new CompletableFuture<>();
        CompletableFuture<Map<String, List<String>>> reactFuture = new CompletableFuture<>();

        client2.setListener(new ChatClient.ClientListener() {
            @Override public void onMessageReceived(Message message) {}
            @Override public void onMessageEdited(String id, String content) { editedFuture.complete(content); }
            @Override public void onMessageRecalled(String id) { recalledFuture.complete(id); }
            @Override public void onMessageReacted(String id, Map<String, List<String>> reactions) { reactFuture.complete(reactions); }
            @Override public void onMessageRead(String id, String userId) {}
            @Override public void onTyping(String convId, String uid, String name, boolean typing) {}
            @Override public void onUserOnline(String uid, String u, String d) {}
            @Override public void onUserOffline(String uid) {}
            @Override public void onFriendRequestReceived(FriendRequest req) {}
            @Override public void onCallSignal(CallSession call, String signal) {}
            @Override public void onAudioReceived(byte[] audioData) {}
            @Override public void onServerNotice(String msg) {}
            @Override public void onDisconnected() {}
            @Override public void onReconnected() {}
        });

        // 1. Send initial message
        Message msg = new Message("public_room", user1.getId(), user1.getDisplayName(), "Bản tin gốc");
        Message sent = client1.sendMessage(msg).get(5, TimeUnit.SECONDS);
        assertNotNull(sent);

        // 2. Client 1 edits message
        client1.editMessage(sent.getId(), "Bản tin đã chỉnh sửa");
        String editedContent = editedFuture.get(5, TimeUnit.SECONDS);
        assertEquals("Bản tin đã chỉnh sửa", editedContent);

        // 3. Client 2 reacts with ❤️
        client2.reactMessage(sent.getId(), "❤️", "ADD");
        Map<String, List<String>> reacts = reactFuture.get(5, TimeUnit.SECONDS);
        assertNotNull(reacts);
        assertTrue(reacts.containsKey("❤️"));
        assertTrue(reacts.get("❤️").contains(user2.getId()));

        // 4. Client 1 recalls message
        client1.recallMessage(sent.getId());
        String recalledId = recalledFuture.get(5, TimeUnit.SECONDS);
        assertEquals(sent.getId(), recalledId);
    }
}
