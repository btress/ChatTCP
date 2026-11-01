package org.example.ungdungchat.shared.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Conversation {
    public static final String TYPE_DIRECT = "DIRECT";
    public static final String TYPE_GROUP = "GROUP";
    public static final String TYPE_PUBLIC = "PUBLIC";

    private String id;
    private String type = TYPE_DIRECT;
    private String name;
    private String avatarUrl;
    private String createdBy;
    private String lastMessageSnippet;
    private Instant lastMessageAt;
    private int unreadCount = 0;
    private boolean isPinned = false;
    private boolean isMuted = false;
    private boolean isArchived = false;
    private List<String> memberIds = new ArrayList<>();
    private Instant createdAt = Instant.now();

    public Conversation() {}

    public Conversation(String id, String type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastMessageSnippet() {
        return lastMessageSnippet;
    }

    public void setLastMessageSnippet(String lastMessageSnippet) {
        this.lastMessageSnippet = lastMessageSnippet;
    }

    public Instant getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(Instant lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = unreadCount;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    public boolean isMuted() {
        return isMuted;
    }

    public void setMuted(boolean muted) {
        isMuted = muted;
    }

    public boolean isArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
