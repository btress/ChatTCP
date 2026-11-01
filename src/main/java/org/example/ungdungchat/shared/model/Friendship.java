package org.example.ungdungchat.shared.model;

import java.time.Instant;

public class Friendship {
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_BLOCKED = "BLOCKED";

    private String id;
    private String user1Id;
    private String user2Id;
    private String status = STATUS_ACCEPTED;
    private Instant createdAt = Instant.now();

    public Friendship() {}

    public Friendship(String user1Id, String user2Id) {
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.status = STATUS_ACCEPTED;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUser1Id() {
        return user1Id;
    }

    public void setUser1Id(String user1Id) {
        this.user1Id = user1Id;
    }

    public String getUser2Id() {
        return user2Id;
    }

    public void setUser2Id(String user2Id) {
        this.user2Id = user2Id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
