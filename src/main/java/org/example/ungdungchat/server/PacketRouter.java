package org.example.ungdungchat.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.example.ungdungchat.server.db.*;
import org.example.ungdungchat.server.security.PasswordUtils;
import org.example.ungdungchat.shared.model.*;
import org.example.ungdungchat.shared.protocol.EventType;
import org.example.ungdungchat.shared.protocol.JsonUtils;
import org.example.ungdungchat.shared.protocol.Packet;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

public class PacketRouter {
    private static final Logger LOGGER = Logger.getLogger(PacketRouter.class.getName());
    private static final Gson GSON = JsonUtils.GSON;

    private final UserRepository userRepo;
    private final ConversationRepository convRepo = new ConversationRepository();
    private final MessageRepository msgRepo = new MessageRepository();
    private final FriendRepository friendRepo = new FriendRepository();
    private final AuditLogRepository auditRepo = new AuditLogRepository();
    private final SessionManager sessionManager = SessionManager.getInstance();

    public PacketRouter() {
        this(new UserRepository());
    }

    public PacketRouter(UserRepository userRepo) {
        this.userRepo = userRepo;
        // Ensure at least one default public room exists
        Conversation pub = convRepo.findById("public_room");
        if (pub == null) {
            Conversation c = new Conversation("public_room", Conversation.TYPE_PUBLIC, "Phòng Chat Chung");
            c.setLastMessageSnippet("Chào mừng bạn đến với Phòng Chat Chung!");
            convRepo.save(c);
        }
    }

    public void route(ClientSession session, Packet packet) {
        String type = packet.getType();
        String reqId = packet.getRequestId();

        if (type == null) return;

        switch (type) {
            case EventType.AUTH_REGISTER -> handleRegister(session, packet);
            case EventType.AUTH_LOGIN -> handleLogin(session, packet);
            case EventType.AUTH_LOGOUT -> handleLogout(session, packet);

            case EventType.CONVERSATION_LIST -> handleConversationList(session, packet);
            case EventType.CONVERSATION_CREATE -> handleConversationCreate(session, packet);

            case EventType.MESSAGE_SEND -> handleMessageSend(session, packet);
            case EventType.MESSAGE_HISTORY -> handleMessageHistory(session, packet);
            case EventType.MESSAGE_EDIT -> handleMessageEdit(session, packet);
            case EventType.MESSAGE_RECALL -> handleMessageRecall(session, packet);
            case EventType.MESSAGE_REACT -> handleMessageReact(session, packet);
            case EventType.MESSAGE_READ -> handleMessageRead(session, packet);

            case EventType.TYPING_START, EventType.TYPING_STOP -> handleTyping(session, packet);

            case EventType.USER_SEARCH -> handleUserSearch(session, packet);
            case EventType.USER_PROFILE_UPDATE -> handleProfileUpdate(session, packet);

            case EventType.FRIEND_REQUEST -> handleFriendRequest(session, packet);
            case EventType.FRIEND_ACCEPT -> handleFriendAccept(session, packet);
            case EventType.FRIEND_REJECT -> handleFriendReject(session, packet);
            case EventType.FRIEND_LIST -> handleFriendList(session, packet);
            case EventType.USER_BLOCK -> handleUserBlock(session, packet);

            case EventType.CALL_REQUEST, EventType.CALL_ACCEPT, EventType.CALL_REJECT, EventType.CALL_END -> handleCall(session, packet);

            case EventType.ADMIN_STATS -> handleAdminStats(session, packet);
            case EventType.ADMIN_CLIENT_LIST -> handleAdminClientList(session, packet);
            case EventType.ADMIN_KICK -> handleAdminKick(session, packet);
            case EventType.ADMIN_BAN -> handleAdminBan(session, packet);

            default -> {
                LOGGER.warning("Unknown packet type: " + type);
                session.sendPacket(Packet.error(type, reqId, "Unknown packet type: " + type));
            }
        }
    }

    private void handleRegister(ClientSession session, Packet packet) {
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String username = data != null ? data.get("username") : null;
        String email = data != null ? data.get("email") : null;
        String password = data != null ? data.get("password") : null;
        String displayName = data != null ? data.get("displayName") : null;

        if (username == null || username.trim().length() < 3) {
            session.sendPacket(Packet.error(EventType.AUTH_REGISTER, packet.getRequestId(), "Username phải từ 3 ký tự trở lên"));
            return;
        }
        if (password == null || password.length() < 6) {
            session.sendPacket(Packet.error(EventType.AUTH_REGISTER, packet.getRequestId(), "Mật khẩu phải từ 6 ký tự trở lên"));
            return;
        }

        username = username.trim();
        if (userRepo.findByUsername(username) != null) {
            session.sendPacket(Packet.error(EventType.AUTH_REGISTER, packet.getRequestId(), "Tên đăng nhập đã tồn tại"));
            return;
        }
        if (email != null && !email.isBlank() && userRepo.findByEmail(email) != null) {
            session.sendPacket(Packet.error(EventType.AUTH_REGISTER, packet.getRequestId(), "Email đã được sử dụng"));
            return;
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(email != null ? email.trim() : "");
        newUser.setPasswordHash(PasswordUtils.hashPassword(password));
        newUser.setDisplayName(displayName != null && !displayName.isBlank() ? displayName.trim() : username);
        newUser.setStatus("OFFLINE");

        boolean saved = userRepo.save(newUser);
        if (saved) {
            // Automatically add to public room
            Conversation pub = convRepo.findById("public_room");
            if (pub != null) {
                if (!pub.getMemberIds().contains(newUser.getId())) {
                    pub.getMemberIds().add(newUser.getId());
                    convRepo.save(pub);
                }
            }

            auditRepo.log("USER_REGISTER", newUser.getId(), null, "Đăng ký thành công", session.getRemoteAddress());
            session.sendPacket(Packet.ok(EventType.AUTH_REGISTER, packet.getRequestId())
                    .withPayload(Map.of("message", "Đăng ký thành công", "username", username)));
        } else {
            session.sendPacket(Packet.error(EventType.AUTH_REGISTER, packet.getRequestId(), "Không thể lưu tài khoản"));
        }
    }

    private void handleLogin(ClientSession session, Packet packet) {
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String identifier = data != null ? data.get("identifier") : null;
        String password = data != null ? data.get("password") : null;

        if (identifier == null || password == null) {
            session.sendPacket(Packet.error(EventType.AUTH_LOGIN, packet.getRequestId(), "Vui lòng nhập tài khoản và mật khẩu"));
            return;
        }

        identifier = identifier.trim();
        User user = userRepo.findByUsername(identifier);
        if (user == null) {
            user = userRepo.findByEmail(identifier);
        }

        if (user == null || !PasswordUtils.checkPassword(password, user.getPasswordHash())) {
            auditRepo.log("LOGIN_FAILED", null, identifier, "Sai thông tin đăng nhập", session.getRemoteAddress());
            session.sendPacket(Packet.error(EventType.AUTH_LOGIN, packet.getRequestId(), "Tài khoản hoặc mật khẩu không chính xác"));
            return;
        }

        if (user.isBanned()) {
            auditRepo.log("LOGIN_BLOCKED", user.getId(), null, "Tài khoản bị cấm", session.getRemoteAddress());
            session.sendPacket(Packet.error(EventType.AUTH_LOGIN, packet.getRequestId(), "Tài khoản của bạn đã bị khoá bởi Admin"));
            return;
        }

        String token = UUID.randomUUID().toString();
        session.setSessionToken(token);
        session.setUsername(user.getUsername());
        session.setDisplayName(user.getDisplayName());
        sessionManager.registerAuthenticatedSession(session, user.getId());

        userRepo.updateStatus(user.getId(), "ONLINE");
        user.setStatus("ONLINE");

        // Add to public room if not there
        Conversation pub = convRepo.findById("public_room");
        if (pub != null && !pub.getMemberIds().contains(user.getId())) {
            pub.getMemberIds().add(user.getId());
            convRepo.save(pub);
        }

        auditRepo.log("LOGIN_SUCCESS", user.getId(), null, "Đăng nhập thành công", session.getRemoteAddress());

        // Notify friends / all
        Packet onlinePacket = Packet.create(EventType.USER_ONLINE)
                .withPayload(Map.of("userId", user.getId(), "username", user.getUsername(), "displayName", user.getDisplayName()));
        sessionManager.broadcastToAll(onlinePacket);

        // Reply login success
        Map<String, Object> resp = new HashMap<>();
        resp.put("user", user);
        resp.put("token", token);
        session.sendPacket(Packet.ok(EventType.AUTH_LOGIN, packet.getRequestId()).withPayload(resp));
    }

    private void handleLogout(ClientSession session, Packet packet) {
        if (session.getUserId() != null) {
            String uid = session.getUserId();
            userRepo.updateStatus(uid, "OFFLINE");
            sessionManager.removeSession(session);

            Packet offlinePacket = Packet.create(EventType.USER_OFFLINE)
                    .withPayload(Map.of("userId", uid, "lastSeen", Instant.now().toString()));
            sessionManager.broadcastToAll(offlinePacket);
        }
        session.sendPacket(Packet.ok(EventType.AUTH_LOGOUT, packet.getRequestId()));
    }

    private void handleConversationList(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) {
            session.sendPacket(Packet.error(EventType.CONVERSATION_LIST, packet.getRequestId(), "Chưa xác thực"));
            return;
        }
        String uid = session.getUserId();
        List<Conversation> convs = convRepo.getUserConversations(uid);

        // Customize direct conversation names and avatars for this user
        List<Conversation> customized = new ArrayList<>();
        for (Conversation c : convs) {
            if (Conversation.TYPE_DIRECT.equals(c.getType())) {
                String otherUid = c.getMemberIds().stream().filter(id -> !id.equals(uid)).findFirst().orElse(null);
                if (otherUid != null) {
                    User other = userRepo.findById(otherUid);
                    if (other != null) {
                        Conversation clone = new Conversation(c.getId(), c.getType(), other.getDisplayName());
                        clone.setAvatarUrl(other.getAvatarUrl());
                        clone.setLastMessageSnippet(c.getLastMessageSnippet());
                        clone.setLastMessageAt(c.getLastMessageAt());
                        clone.setMemberIds(c.getMemberIds());
                        clone.setCreatedAt(c.getCreatedAt());
                        customized.add(clone);
                        continue;
                    }
                }
            }
            customized.add(c);
        }

        session.sendPacket(Packet.ok(EventType.CONVERSATION_LIST, packet.getRequestId()).withPayload(customized));
    }

    private void handleConversationCreate(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, Object> data = packet.getPayloadAs(new TypeToken<Map<String, Object>>() {}.getType());
        String type = (String) data.get("type");
        String name = (String) data.get("name");
        List<String> memberIds = (List<String>) data.get("memberIds");
        if (memberIds == null) memberIds = new ArrayList<>();
        if (!memberIds.contains(session.getUserId())) {
            memberIds.add(session.getUserId());
        }

        if (Conversation.TYPE_DIRECT.equals(type)) {
            if (memberIds.size() >= 2) {
                String u1 = memberIds.get(0);
                String u2 = memberIds.get(1);
                Conversation existing = convRepo.findDirectConversation(u1, u2);
                if (existing != null) {
                    session.sendPacket(Packet.ok(EventType.CONVERSATION_CREATE, packet.getRequestId()).withPayload(existing));
                    return;
                }
            }
        }

        Conversation newConv = new Conversation();
        newConv.setType(type != null ? type : Conversation.TYPE_DIRECT);
        newConv.setName(name);
        newConv.setCreatedBy(session.getUserId());
        newConv.setMemberIds(memberIds);
        convRepo.save(newConv);

        session.sendPacket(Packet.ok(EventType.CONVERSATION_CREATE, packet.getRequestId()).withPayload(newConv));
    }

    private void handleMessageSend(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) {
            session.sendPacket(Packet.error(EventType.MESSAGE_SEND, packet.getRequestId(), "Chưa xác thực"));
            return;
        }

        Message msg = packet.getPayloadAs(Message.class);
        if (msg == null || msg.getConversationId() == null) {
            session.sendPacket(Packet.error(EventType.MESSAGE_SEND, packet.getRequestId(), "Thiếu thông tin tin nhắn"));
            return;
        }

        Conversation conv = convRepo.findById(msg.getConversationId());
        if (conv == null) {
            session.sendPacket(Packet.error(EventType.MESSAGE_SEND, packet.getRequestId(), "Hội thoại không tồn tại"));
            return;
        }

        // Check blocking for direct conversation
        if (Conversation.TYPE_DIRECT.equals(conv.getType())) {
            String otherUid = conv.getMemberIds().stream().filter(id -> !id.equals(session.getUserId())).findFirst().orElse(null);
            if (otherUid != null && friendRepo.isBlocked(session.getUserId(), otherUid)) {
                session.sendPacket(Packet.error(EventType.MESSAGE_SEND, packet.getRequestId(), "Không thể gửi tin nhắn cho người đã bị chặn"));
                return;
            }
        }

        msg.setSenderId(session.getUserId());
        msg.setSenderName(session.getDisplayName());
        msg.setStatus(Message.STATUS_SENT);
        msg.setCreatedAt(Instant.now());

        boolean saved = msgRepo.save(msg);
        if (!saved) {
            Message existing = msgRepo.findByClientMessageId(msg.getClientMessageId());
            if (existing != null) {
                Packet recvPacket = Packet.create(EventType.MESSAGE_RECEIVE).withPayload(existing);
                sessionManager.broadcastToConversation(conv.getMemberIds(), recvPacket);
                session.sendPacket(Packet.ok(EventType.MESSAGE_ACK, packet.getRequestId()).withPayload(existing));
                return;
            }
            session.sendPacket(Packet.error(
                    EventType.MESSAGE_SEND, packet.getRequestId(), "Không thể lưu tin nhắn vào cơ sở dữ liệu."));
            return;
        }

        sessionManager.incrementMessageCount();

        // Update conversation snippet
        String snippet = msg.getContent();
        if (Message.TYPE_FILE.equals(msg.getMessageType()) || Message.TYPE_IMAGE.equals(msg.getMessageType())) {
            snippet = "[Tệp đính kèm] " + (msg.getAttachmentName() != null ? msg.getAttachmentName() : "");
        } else if (Message.TYPE_VOICE.equals(msg.getMessageType())) {
            snippet = "[Tin nhắn thoại]";
        }
        convRepo.updateLastMessage(conv.getId(), snippet, msg.getCreatedAt());

        // Broadcast to all conversation members
        Packet recvPacket = Packet.create(EventType.MESSAGE_RECEIVE).withPayload(msg);
        sessionManager.broadcastToConversation(conv.getMemberIds(), recvPacket);

        // Send ACK back to sender
        session.sendPacket(Packet.ok(EventType.MESSAGE_ACK, packet.getRequestId()).withPayload(msg));
    }

    private void handleMessageHistory(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, Object> data = packet.getPayloadAs(new TypeToken<Map<String, Object>>() {}.getType());
        String convId = (String) data.get("conversationId");
        int limit = data.get("limit") != null ? ((Number) data.get("limit")).intValue() : 50;

        List<Message> history = msgRepo.getHistory(convId, null, limit);
        session.sendPacket(Packet.ok(EventType.MESSAGE_HISTORY, packet.getRequestId()).withPayload(history));
    }

    private void handleMessageEdit(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String msgId = data.get("messageId");
        String newContent = data.get("content");

        Message msg = msgRepo.findById(msgId);
        if (msg != null && session.getUserId().equals(msg.getSenderId())) {
            msgRepo.editMessage(msgId, newContent);
            Conversation conv = convRepo.findById(msg.getConversationId());
            if (conv != null) {
                Packet editPacket = Packet.create(EventType.MESSAGE_EDIT)
                        .withPayload(Map.of("messageId", msgId, "conversationId", conv.getId(), "content", newContent));
                sessionManager.broadcastToConversation(conv.getMemberIds(), editPacket);
            }
        }
    }

    private void handleMessageRecall(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String msgId = data.get("messageId");

        Message msg = msgRepo.findById(msgId);
        if (msg != null && session.getUserId().equals(msg.getSenderId())) {
            msgRepo.recallMessage(msgId);
            Conversation conv = convRepo.findById(msg.getConversationId());
            if (conv != null) {
                Packet recallPacket = Packet.create(EventType.MESSAGE_RECALL)
                        .withPayload(Map.of("messageId", msgId, "conversationId", conv.getId()));
                sessionManager.broadcastToConversation(conv.getMemberIds(), recallPacket);
            }
        }
    }

    private void handleMessageReact(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String msgId = data.get("messageId");
        String emoji = data.get("emoji");
        String action = data.get("action"); // "ADD" or "REMOVE"

        Message msg = msgRepo.findById(msgId);
        if (msg != null) {
            if ("REMOVE".equalsIgnoreCase(action)) {
                msgRepo.removeReaction(msgId, emoji, session.getUserId());
            } else {
                msgRepo.addReaction(msgId, emoji, session.getUserId());
            }
            Conversation conv = convRepo.findById(msg.getConversationId());
            if (conv != null) {
                Packet reactPacket = Packet.create(EventType.MESSAGE_REACT)
                        .withPayload(Map.of("messageId", msgId, "conversationId", conv.getId(), "reactions", msg.getReactions()));
                sessionManager.broadcastToConversation(conv.getMemberIds(), reactPacket);
            }
        }
    }

    private void handleMessageRead(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String msgId = data.get("messageId");
        String convId = data.get("conversationId");

        if (msgId != null) {
            msgRepo.markSeen(msgId, session.getUserId());
        }
        if (convId != null) {
            Conversation conv = convRepo.findById(convId);
            if (conv != null) {
                Packet readPacket = Packet.create(EventType.MESSAGE_READ)
                        .withPayload(Map.of("messageId", msgId != null ? msgId : "", "conversationId", convId, "userId", session.getUserId()));
                sessionManager.broadcastToConversation(conv.getMemberIds(), readPacket);
            }
        }
    }

    private void handleTyping(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String convId = data != null ? data.get("conversationId") : null;
        if (convId != null) {
            Conversation conv = convRepo.findById(convId);
            if (conv != null) {
                Packet typingPacket = Packet.create(packet.getType())
                        .withPayload(Map.of(
                                "conversationId", convId,
                                "userId", session.getUserId(),
                                "userName", session.getDisplayName()
                        ));
                for (String mId : conv.getMemberIds()) {
                    if (!mId.equals(session.getUserId())) {
                        sessionManager.sendToUser(mId, typingPacket);
                    }
                }
            }
        }
    }

    private void handleUserSearch(ClientSession session, Packet packet) {
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String query = data != null ? data.get("query") : "";
        List<User> users = userRepo.searchUsers(query, session.getUserId());
        session.sendPacket(Packet.ok(EventType.USER_SEARCH, packet.getRequestId()).withPayload(users));
    }

    private void handleProfileUpdate(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String displayName = data.get("displayName");
        String bio = data.get("bio");
        String avatarUrl = data.get("avatarUrl");

        userRepo.updateProfile(session.getUserId(), displayName, bio, avatarUrl);
        if (displayName != null) session.setDisplayName(displayName);

        session.sendPacket(Packet.ok(EventType.USER_PROFILE_UPDATE, packet.getRequestId()).withPayload(userRepo.findById(session.getUserId())));
    }

    private void handleFriendRequest(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String toUserId = data.get("toUserId");

        if (toUserId == null || toUserId.equals(session.getUserId())) return;
        if (friendRepo.isBlocked(session.getUserId(), toUserId)) {
            session.sendPacket(Packet.error(EventType.FRIEND_REQUEST, packet.getRequestId(), "Không thể gửi lời mời kết bạn"));
            return;
        }

        FriendRequest req = new FriendRequest(session.getUserId(), session.getDisplayName(), toUserId);
        friendRepo.saveRequest(req);

        // Notify recipient if online
        Packet notify = Packet.create(EventType.FRIEND_REQUEST).withPayload(req);
        sessionManager.sendToUser(toUserId, notify);

        session.sendPacket(Packet.ok(EventType.FRIEND_REQUEST, packet.getRequestId()).withPayload(req));
    }

    private void handleFriendAccept(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String reqId = data.get("requestId");

        FriendRequest req = friendRepo.findRequestById(reqId);
        if (req != null && session.getUserId().equals(req.getToUserId())) {
            friendRepo.acceptRequest(reqId);

            // Notify sender
            Packet notify = Packet.create(EventType.FRIEND_ACCEPT)
                    .withPayload(Map.of("requestId", reqId, "fromUserId", req.getFromUserId(), "toUserId", req.getToUserId()));
            sessionManager.sendToUser(req.getFromUserId(), notify);

            session.sendPacket(Packet.ok(EventType.FRIEND_ACCEPT, packet.getRequestId()).withPayload(req));
        }
    }

    private void handleFriendReject(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String reqId = data.get("requestId");
        friendRepo.rejectRequest(reqId);
        session.sendPacket(Packet.ok(EventType.FRIEND_REJECT, packet.getRequestId()));
    }

    private void handleFriendList(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        List<String> fIds = friendRepo.getFriendUserIds(session.getUserId());
        List<User> friends = new ArrayList<>();
        for (String id : fIds) {
            User u = userRepo.findById(id);
            if (u != null) {
                u.setStatus(sessionManager.isUserOnline(id) ? "ONLINE" : "OFFLINE");
                friends.add(u);
            }
        }
        session.sendPacket(Packet.ok(EventType.FRIEND_LIST, packet.getRequestId()).withPayload(friends));
    }

    private void handleUserBlock(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String targetUserId = data.get("targetUserId");
        if (targetUserId != null) {
            friendRepo.blockUser(session.getUserId(), targetUserId);
            session.sendPacket(Packet.ok(EventType.USER_BLOCK, packet.getRequestId()));
        }
    }

    private void handleCall(ClientSession session, Packet packet) {
        if (!session.isAuthenticated()) return;
        CallSession call = packet.getPayloadAs(CallSession.class);
        if (call == null) return;

        String targetId = session.getUserId().equals(call.getCallerId()) ? call.getReceiverId() : call.getCallerId();
        if (targetId != null) {
            sessionManager.sendToUser(targetId, packet);
        }
    }

    private void handleAdminStats(ClientSession session, Packet packet) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("uptime", sessionManager.getUptimeFormatted());
        stats.put("activeConnections", sessionManager.getActiveConnectionCount());
        stats.put("onlineUsers", sessionManager.getOnlineUserCount());
        stats.put("totalUsers", userRepo.count());
        stats.put("totalConversations", convRepo.count());
        stats.put("totalMessages", msgRepo.count());
        stats.put("isMongoConnected", MongoDBManager.getInstance().isConnected());

        session.sendPacket(Packet.ok(EventType.ADMIN_STATS, packet.getRequestId()).withPayload(stats));
    }

    private void handleAdminClientList(ClientSession session, Packet packet) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ClientSession s : sessionManager.getAllActiveSessions()) {
            Map<String, Object> item = new HashMap<>();
            item.put("sessionId", s.getSessionId());
            item.put("userId", s.getUserId());
            item.put("username", s.getUsername());
            item.put("displayName", s.getDisplayName());
            item.put("remoteAddress", s.getRemoteAddress());
            item.put("connectedAt", s.getConnectedAt().toString());
            list.add(item);
        }
        session.sendPacket(Packet.ok(EventType.ADMIN_CLIENT_LIST, packet.getRequestId()).withPayload(list));
    }

    private void handleAdminKick(ClientSession session, Packet packet) {
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String userId = data.get("userId");
        String reason = data.get("reason") != null ? data.get("reason") : "Bị kick bởi Quản trị viên";
        if (userId != null) {
            sessionManager.kickUser(userId, reason);
            auditRepo.log("ADMIN_KICK", session.getUserId(), userId, reason, session.getRemoteAddress());
            session.sendPacket(Packet.ok(EventType.ADMIN_KICK, packet.getRequestId()));
        }
    }

    private void handleAdminBan(ClientSession session, Packet packet) {
        Map<String, String> data = packet.getPayloadAs(new TypeToken<Map<String, String>>() {}.getType());
        String userId = data.get("userId");
        boolean ban = Boolean.parseBoolean(data.get("ban"));
        if (userId != null) {
            userRepo.setBanned(userId, ban);
            if (ban) {
                sessionManager.kickUser(userId, "Tài khoản của bạn đã bị khoá");
            }
            auditRepo.log(ban ? "ADMIN_BAN" : "ADMIN_UNBAN", session.getUserId(), userId, "Admin ban status changed", session.getRemoteAddress());
            session.sendPacket(Packet.ok(EventType.ADMIN_BAN, packet.getRequestId()));
        }
    }
}
