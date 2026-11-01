package org.example.ungdungchat.server;

import org.example.ungdungchat.shared.protocol.EventType;
import org.example.ungdungchat.shared.protocol.Packet;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    public static SessionManager getInstance() { return INSTANCE; }

    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Instant startTime = Instant.now();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);

    private SessionManager() {}

    public void addSession(ClientSession session) {
        sessions.put(session.getSessionId(), session);
    }

    public void registerAuthenticatedSession(ClientSession session, String userId) {
        session.setUserId(userId);
        session.setAuthenticated(true);
        userSessions.computeIfAbsent(userId, k -> Collections.synchronizedSet(new HashSet<>()))
                .add(session.getSessionId());
    }

    public void removeSession(ClientSession session) {
        sessions.remove(session.getSessionId());
        if (session.getUserId() != null) {
            Set<String> sIds = userSessions.get(session.getUserId());
            if (sIds != null) {
                sIds.remove(session.getSessionId());
                if (sIds.isEmpty()) {
                    userSessions.remove(session.getUserId());
                }
            }
        }
    }

    public boolean isUserOnline(String userId) {
        Set<String> sIds = userSessions.get(userId);
        return sIds != null && !sIds.isEmpty();
    }

    public List<ClientSession> getSessionsForUser(String userId) {
        List<ClientSession> result = new ArrayList<>();
        Set<String> sIds = userSessions.get(userId);
        if (sIds != null) {
            for (String sId : sIds) {
                ClientSession s = sessions.get(sId);
                if (s != null && s.isConnected()) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    public void sendToUser(String userId, Packet packet) {
        for (ClientSession s : getSessionsForUser(userId)) {
            s.sendPacket(packet);
        }
    }

    public void broadcastToAll(Packet packet) {
        for (ClientSession s : sessions.values()) {
            if (s.isAuthenticated() && s.isConnected()) {
                s.sendPacket(packet);
            }
        }
    }

    public void broadcastToConversation(List<String> memberUserIds, Packet packet) {
        if (memberUserIds == null) return;
        for (String uid : memberUserIds) {
            sendToUser(uid, packet);
        }
    }

    public void kickUser(String userId, String reason) {
        for (ClientSession s : getSessionsForUser(userId)) {
            Packet notice = Packet.create(EventType.SERVER_NOTICE)
                    .withPayload(Map.of("message", "Bạn đã bị buộc ngắt kết nối: " + reason));
            s.sendPacket(notice);
            s.close();
            removeSession(s);
        }
    }

    public List<ClientSession> getAllActiveSessions() {
        return new ArrayList<>(sessions.values());
    }

    public int getOnlineUserCount() {
        return userSessions.size();
    }

    public int getActiveConnectionCount() {
        return sessions.size();
    }

    public void incrementMessageCount() {
        totalMessagesProcessed.incrementAndGet();
    }

    public long getTotalMessagesProcessed() {
        return totalMessagesProcessed.get();
    }

    public String getUptimeFormatted() {
        Duration d = Duration.between(startTime, Instant.now());
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
