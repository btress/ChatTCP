package org.example.ungdungchat.shared.model;

import java.time.Instant;

public class CallSession {
    public static final String CALL_VOICE = "VOICE";
    public static final String CALL_VIDEO = "VIDEO";

    public static final String STATUS_RINGING = "RINGING";
    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_ENDED = "ENDED";
    public static final String STATUS_MISSED = "MISSED";

    private String callId;
    private String conversationId;
    private String callerId;
    private String callerName;
    private String receiverId;
    private String receiverName;
    private String callType = CALL_VOICE;
    private String status = STATUS_RINGING;
    private int audioPort = 9999;
    private Instant startedAt = Instant.now();
    private Instant endedAt;

    public CallSession() {}

    public CallSession(String callId, String callerId, String callerName, String receiverId, String receiverName) {
        this.callId = callId;
        this.callerId = callerId;
        this.callerName = callerName;
        this.receiverId = receiverId;
        this.receiverName = receiverName;
        this.status = STATUS_RINGING;
        this.startedAt = Instant.now();
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = callId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getCallerName() {
        return callerName;
    }

    public void setCallerName(String callerName) {
        this.callerName = callerName;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAudioPort() {
        return audioPort;
    }

    public void setAudioPort(int audioPort) {
        this.audioPort = audioPort;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }
}
