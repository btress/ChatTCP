package org.example.ungdungchat.shared.protocol;

public class EventType {
    // AUTH
    public static final String AUTH_REGISTER = "AUTH_REGISTER";
    public static final String AUTH_LOGIN = "AUTH_LOGIN";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String AUTH_SESSION_EXPIRED = "AUTH_SESSION_EXPIRED";

    // USER
    public static final String USER_SEARCH = "USER_SEARCH";
    public static final String USER_ONLINE = "USER_ONLINE";
    public static final String USER_OFFLINE = "USER_OFFLINE";
    public static final String USER_PROFILE_UPDATE = "USER_PROFILE_UPDATE";
    public static final String USER_GET_PROFILE = "USER_GET_PROFILE";

    // FRIEND
    public static final String FRIEND_REQUEST = "FRIEND_REQUEST";
    public static final String FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String FRIEND_REJECT = "FRIEND_REJECT";
    public static final String FRIEND_REMOVE = "FRIEND_REMOVE";
    public static final String FRIEND_LIST = "FRIEND_LIST";
    public static final String USER_BLOCK = "USER_BLOCK";
    public static final String USER_UNBLOCK = "USER_UNBLOCK";

    // CONVERSATION
    public static final String CONVERSATION_LIST = "CONVERSATION_LIST";
    public static final String CONVERSATION_CREATE = "CONVERSATION_CREATE";
    public static final String CONVERSATION_UPDATE = "CONVERSATION_UPDATE";
    public static final String CONVERSATION_PIN = "CONVERSATION_PIN";
    public static final String CONVERSATION_MUTE = "CONVERSATION_MUTE";
    public static final String CONVERSATION_ARCHIVE = "CONVERSATION_ARCHIVE";

    // MESSAGE
    public static final String MESSAGE_SEND = "MESSAGE_SEND";
    public static final String MESSAGE_RECEIVE = "MESSAGE_RECEIVE";
    public static final String MESSAGE_ACK = "MESSAGE_ACK";
    public static final String MESSAGE_EDIT = "MESSAGE_EDIT";
    public static final String MESSAGE_RECALL = "MESSAGE_RECALL";
    public static final String MESSAGE_DELETE_FOR_ME = "MESSAGE_DELETE_FOR_ME";
    public static final String MESSAGE_REACT = "MESSAGE_REACT";
    public static final String MESSAGE_READ = "MESSAGE_READ";
    public static final String MESSAGE_DELIVERED = "MESSAGE_DELIVERED";
    public static final String MESSAGE_HISTORY = "MESSAGE_HISTORY";

    // GROUP
    public static final String GROUP_CREATE = "GROUP_CREATE";
    public static final String GROUP_MEMBER_ADD = "GROUP_MEMBER_ADD";
    public static final String GROUP_MEMBER_REMOVE = "GROUP_MEMBER_REMOVE";
    public static final String GROUP_ROLE_UPDATE = "GROUP_ROLE_UPDATE";
    public static final String GROUP_LEAVE = "GROUP_LEAVE";
    public static final String GROUP_DISBAND = "GROUP_DISBAND";

    // TYPING
    public static final String TYPING_START = "TYPING_START";
    public static final String TYPING_STOP = "TYPING_STOP";

    // FILE
    public static final String FILE_INIT = "FILE_INIT";
    public static final String FILE_CHUNK = "FILE_CHUNK";
    public static final String FILE_COMPLETE = "FILE_COMPLETE";
    public static final String FILE_DOWNLOAD_REQ = "FILE_DOWNLOAD_REQ";
    public static final String FILE_DOWNLOAD_CHUNK = "FILE_DOWNLOAD_CHUNK";

    // CALL
    public static final String CALL_REQUEST = "CALL_REQUEST";
    public static final String CALL_RINGING = "CALL_RINGING";
    public static final String CALL_ACCEPT = "CALL_ACCEPT";
    public static final String CALL_REJECT = "CALL_REJECT";
    public static final String CALL_END = "CALL_END";
    public static final String CALL_AUDIO_DATA = "CALL_AUDIO_DATA";

    // ADMIN
    public static final String ADMIN_STATS = "ADMIN_STATS";
    public static final String ADMIN_CLIENT_LIST = "ADMIN_CLIENT_LIST";
    public static final String ADMIN_KICK = "ADMIN_KICK";
    public static final String ADMIN_BAN = "ADMIN_BAN";
    public static final String ADMIN_UNBAN = "ADMIN_UNBAN";
    public static final String SERVER_NOTICE = "SERVER_NOTICE";
}
