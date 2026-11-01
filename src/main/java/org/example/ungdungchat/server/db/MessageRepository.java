package org.example.ungdungchat.server.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.example.ungdungchat.shared.model.Message;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MessageRepository {
    private static final Logger LOGGER = Logger.getLogger(MessageRepository.class.getName());
    private final MongoCollection<Document> collection;
    private final Map<String, Message> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, String> clientMsgIdIndex = new ConcurrentHashMap<>();

    public MessageRepository() {
        this.collection = MongoDBManager.getInstance().getCollection("messages");
    }

    private Message docToMsg(Document doc) {
        if (doc == null) return null;
        Message m = new Message();
        m.setId(doc.getString("_id"));
        m.setConversationId(doc.getString("conversation_id"));
        m.setSenderId(doc.getString("sender_id"));
        m.setSenderName(doc.getString("sender_name"));
        m.setSenderAvatar(doc.getString("sender_avatar"));
        m.setClientMessageId(doc.getString("client_message_id"));
        m.setMessageType(doc.getString("message_type"));
        m.setContent(doc.getString("content"));
        m.setAttachmentUrl(doc.getString("attachment_url"));
        m.setAttachmentName(doc.getString("attachment_name"));
        Long as = doc.getLong("attachment_size");
        if (as != null) m.setAttachmentSize(as);
        m.setReplyToId(doc.getString("reply_to_id"));
        m.setReplyToSender(doc.getString("reply_to_sender"));
        m.setReplyToContent(doc.getString("reply_to_content"));

        Boolean recalled = doc.getBoolean("is_recalled");
        m.setRecalled(recalled != null && recalled);
        Boolean edited = doc.getBoolean("is_edited");
        m.setEdited(edited != null && edited);
        m.setStatus(doc.getString("status"));

        List<String> seen = doc.getList("seen_by_user_ids", String.class);
        if (seen != null) m.setSeenByUserIds(new ArrayList<>(seen));

        Document reactDoc = (Document) doc.get("reactions");
        if (reactDoc != null) {
            Map<String, List<String>> reactMap = new HashMap<>();
            for (String key : reactDoc.keySet()) {
                List<String> uids = reactDoc.getList(key, String.class);
                if (uids != null) reactMap.put(key, new ArrayList<>(uids));
            }
            m.setReactions(reactMap);
        }

        Date ca = doc.getDate("created_at");
        if (ca != null) m.setCreatedAt(ca.toInstant());
        return m;
    }

    private Document msgToDoc(Message m) {
        Document reactDoc = new Document();
        if (m.getReactions() != null) {
            for (Map.Entry<String, List<String>> e : m.getReactions().entrySet()) {
                reactDoc.append(e.getKey(), e.getValue());
            }
        }

        return new Document("_id", m.getId())
                .append("conversation_id", m.getConversationId())
                .append("sender_id", m.getSenderId())
                .append("sender_name", m.getSenderName())
                .append("sender_avatar", m.getSenderAvatar())
                .append("client_message_id", m.getClientMessageId())
                .append("message_type", m.getMessageType())
                .append("content", m.getContent())
                .append("attachment_url", m.getAttachmentUrl())
                .append("attachment_name", m.getAttachmentName())
                .append("attachment_size", m.getAttachmentSize())
                .append("reply_to_id", m.getReplyToId())
                .append("reply_to_sender", m.getReplyToSender())
                .append("reply_to_content", m.getReplyToContent())
                .append("is_recalled", m.isRecalled())
                .append("is_edited", m.isEdited())
                .append("status", m.getStatus())
                .append("seen_by_user_ids", m.getSeenByUserIds())
                .append("reactions", reactDoc)
                .append("created_at", m.getCreatedAt() != null ? Date.from(m.getCreatedAt()) : new Date());
    }

    public synchronized boolean save(Message msg) {
        if (msg.getId() == null) {
            msg.setId(UUID.randomUUID().toString());
        }

        if (msg.getClientMessageId() != null) {
            if (clientMsgIdIndex.containsKey(msg.getClientMessageId())) {
                return false; // Already saved!
            }
        }

        if (collection != null) {
            try {
                Document doc = msgToDoc(msg);
                collection.replaceOne(Filters.eq("_id", msg.getId()), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Could not persist message " + msg.getClientMessageId(), e);
                return false;
            }
        }

        memoryStore.put(msg.getId(), msg);
        if (msg.getClientMessageId() != null) {
            clientMsgIdIndex.put(msg.getClientMessageId(), msg.getId());
        }
        return true;
    }

    public Message findById(String id) {
        if (id == null) return null;
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.eq("_id", id)).first();
                if (doc != null) return docToMsg(doc);
            } catch (Exception ignored) {}
        }
        return memoryStore.get(id);
    }

    public Message findByClientMessageId(String clientMessageId) {
        if (clientMessageId == null) return null;
        String id = clientMsgIdIndex.get(clientMessageId);
        if (id != null) {
            return memoryStore.get(id);
        }
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.eq("client_message_id", clientMessageId)).first();
                if (doc != null) {
                    Message message = docToMsg(doc);
                    memoryStore.put(message.getId(), message);
                    clientMsgIdIndex.put(clientMessageId, message.getId());
                    return message;
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not look up message " + clientMessageId, e);
            }
        }
        return null;
    }

    public List<Message> getHistory(String conversationId, Instant beforeTime, int limit) {
        if (collection != null) {
            try {
                List<org.bson.conversions.Bson> filters = new ArrayList<>();
                filters.add(Filters.eq("conversation_id", conversationId));
                if (beforeTime != null) {
                    filters.add(Filters.lt("created_at", Date.from(beforeTime)));
                }

                List<Message> messages = new ArrayList<>();
                for (Document doc : collection.find(Filters.and(filters))
                        .sort(Sorts.descending("created_at"))
                        .limit(limit)) {
                    Message message = docToMsg(doc);
                    messages.add(message);
                    memoryStore.put(message.getId(), message);
                    if (message.getClientMessageId() != null) {
                        clientMsgIdIndex.put(message.getClientMessageId(), message.getId());
                    }
                }
                Collections.reverse(messages);
                return messages;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not load message history for conversation " + conversationId, e);
            }
        }

        List<Message> list = memoryStore.values().stream()
                .filter(m -> conversationId.equals(m.getConversationId()))
                .filter(m -> beforeTime == null || m.getCreatedAt().isBefore(beforeTime))
                .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .collect(Collectors.toList());

        if (list.size() > limit) {
            return list.subList(list.size() - limit, list.size());
        }
        return list;
    }

    public void recallMessage(String messageId) {
        Message m = findById(messageId);
        if (m != null) {
            m.setRecalled(true);
            m.setContent("Tin nhắn đã được thu hồi");
            memoryStore.put(messageId, m);
            if (collection != null) {
                try {
                    collection.updateOne(Filters.eq("_id", messageId),
                            Updates.combine(
                                    Updates.set("is_recalled", true),
                                    Updates.set("content", "Tin nhắn đã được thu hồi")
                            ));
                } catch (Exception ignored) {}
            }
        }
    }

    public void editMessage(String messageId, String newContent) {
        Message m = findById(messageId);
        if (m != null && !m.isRecalled()) {
            m.setEdited(true);
            m.setContent(newContent);
            memoryStore.put(messageId, m);
            if (collection != null) {
                try {
                    collection.updateOne(Filters.eq("_id", messageId),
                            Updates.combine(
                                    Updates.set("is_edited", true),
                                    Updates.set("content", newContent)
                            ));
                } catch (Exception ignored) {}
            }
        }
    }

    public void addReaction(String messageId, String emoji, String userId) {
        Message m = findById(messageId);
        if (m != null) {
            m.addReaction(emoji, userId);
            memoryStore.put(messageId, m);
        }
    }

    public void removeReaction(String messageId, String emoji, String userId) {
        Message m = findById(messageId);
        if (m != null) {
            m.removeReaction(emoji, userId);
            memoryStore.put(messageId, m);
        }
    }

    public void markSeen(String messageId, String userId) {
        Message m = findById(messageId);
        if (m != null) {
            if (!m.getSeenByUserIds().contains(userId)) {
                m.getSeenByUserIds().add(userId);
                m.setStatus(Message.STATUS_SEEN);
                memoryStore.put(messageId, m);
            }
        }
    }

    public long count() {
        return memoryStore.size();
    }
}
