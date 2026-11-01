package org.example.ungdungchat.server.db;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuditLogRepository {
    public static class AuditLogEntry {
        private final String id;
        private final String action;
        private final String actorId;
        private final String targetId;
        private final String details;
        private final String ipAddress;
        private final Instant timestamp;

        public AuditLogEntry(String id, String action, String actorId, String targetId, String details, String ipAddress) {
            this.id = id;
            this.action = action;
            this.actorId = actorId;
            this.targetId = targetId;
            this.details = details;
            this.ipAddress = ipAddress;
            this.timestamp = Instant.now();
        }

        public String getId() { return id; }
        public String getAction() { return action; }
        public String getActorId() { return actorId; }
        public String getTargetId() { return targetId; }
        public String getDetails() { return details; }
        public String getIpAddress() { return ipAddress; }
        public Instant getTimestamp() { return timestamp; }
    }

    private final List<AuditLogEntry> logs = Collections.synchronizedList(new ArrayList<>());

    public void log(String action, String actorId, String targetId, String details, String ipAddress) {
        AuditLogEntry entry = new AuditLogEntry(
                java.util.UUID.randomUUID().toString(),
                action, actorId, targetId, details, ipAddress
        );
        logs.add(entry);
    }

    public List<AuditLogEntry> getRecentLogs(int limit) {
        synchronized (logs) {
            int size = logs.size();
            if (size <= limit) return new ArrayList<>(logs);
            return new ArrayList<>(logs.subList(size - limit, size));
        }
    }
}
