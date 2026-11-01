package org.example.ungdungchat.shared.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Message {
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_IMAGE = "IMAGE";
    public static final String TYPE_FILE = "FILE";
    public static final String TYPE_VOICE = "VOICE";
    public static final String TYPE_SYSTEM = "SYSTEM";

    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_SEEN = "SEEN";
    public static final String STATUS_FAILED = "FAILED";

    private String id;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String clientMessageId = UUID.randomUUID().toString();
    private String messageType = TYPE_TEXT;
    private String content;

    // Attachments
    private String attachmentUrl;
    private String attachmentName;
    private long attachmentSize = 0;

    // Reply
    private String replyToId;
    private String replyToSender;
    private String replyToContent;

    // Reactions: emoji -> list of userIds
    private Map<String, List<String>> reactions = new HashMap<>();

    // Status flags
    private boolean isRecalled = false;
    private boolean isEdited = false;
    private String status = STATUS_SENT;
    private List<String> seenByUserIds = new ArrayList<>();
    private Instant createdAt = Instant.now();

    public Message() {}

    public Message(String conversationId, String senderId, String senderName, String content) {
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.messageType = TYPE_TEXT;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSenderAvatar() {
        return senderAvatar;
    }

    public void setSenderAvatar(String senderAvatar) {
        this.senderAvatar = senderAvatar;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public void setClientMessageId(String clientMessageId) {
        this.clientMessageId = clientMessageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public void setAttachmentUrl(String attachmentUrl) {
        this.attachmentUrl = attachmentUrl;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public void setAttachmentName(String attachmentName) {
        this.attachmentName = attachmentName;
    }

    public long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }

    public String getReplyToId() {
        return replyToId;
    }

    public void setReplyToId(String replyToId) {
        this.replyToId = replyToId;
    }

    public String getReplyToSender() {
        return replyToSender;
    }

    public void setReplyToSender(String replyToSender) {
        this.replyToSender = replyToSender;
    }

    public String getReplyToContent() {
        return replyToContent;
    }

    public void setReplyToContent(String replyToContent) {
        this.replyToContent = replyToContent;
    }

    public Map<String, List<String>> getReactions() {
        return reactions;
    }

    public void setReactions(Map<String, List<String>> reactions) {
        this.reactions = reactions;
    }

    public void addReaction(String emoji, String userId) {
        if (reactions == null) reactions = new HashMap<>();
        List<String> users = reactions.computeIfAbsent(emoji, k -> new ArrayList<>());
        if (!users.contains(userId)) {
            users.add(userId);
        }
    }

    public void removeReaction(String emoji, String userId) {
        if (reactions == null) return;
        List<String> users = reactions.get(emoji);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                reactions.remove(emoji);
            }
        }
    }

    public boolean isRecalled() {
        return isRecalled;
    }

    public void setRecalled(boolean recalled) {
        isRecalled = recalled;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getSeenByUserIds() {
        return seenByUserIds;
    }

    public void setSeenByUserIds(List<String> seenByUserIds) {
        this.seenByUserIds = seenByUserIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
