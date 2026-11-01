package org.example.ungdungchat.server.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.example.ungdungchat.shared.model.Conversation;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConversationRepository {
    private final MongoCollection<Document> collection;
    private final Map<String, Conversation> memoryStore = new ConcurrentHashMap<>();

    public ConversationRepository() {
        this.collection = MongoDBManager.getInstance().getCollection("conversations");
    }

    private Conversation docToConv(Document doc) {
        if (doc == null) return null;
        Conversation c = new Conversation();
        c.setId(doc.getString("_id"));
        c.setType(doc.getString("type"));
        c.setName(doc.getString("name"));
        c.setAvatarUrl(doc.getString("avatar_url"));
        c.setCreatedBy(doc.getString("created_by"));
        c.setLastMessageSnippet(doc.getString("last_message_snippet"));
        Date lma = doc.getDate("last_message_at");
        if (lma != null) c.setLastMessageAt(lma.toInstant());
        List<String> members = doc.getList("member_ids", String.class);
        if (members != null) c.setMemberIds(new ArrayList<>(members));
        Date ca = doc.getDate("created_at");
        if (ca != null) c.setCreatedAt(ca.toInstant());
        return c;
    }

    private Document convToDoc(Conversation c) {
        return new Document("_id", c.getId())
                .append("type", c.getType())
                .append("name", c.getName())
                .append("avatar_url", c.getAvatarUrl())
                .append("created_by", c.getCreatedBy())
                .append("last_message_snippet", c.getLastMessageSnippet())
                .append("last_message_at", c.getLastMessageAt() != null ? Date.from(c.getLastMessageAt()) : new Date())
                .append("member_ids", c.getMemberIds())
                .append("created_at", c.getCreatedAt() != null ? Date.from(c.getCreatedAt()) : new Date());
    }

    public synchronized void save(Conversation conv) {
        if (conv.getId() == null) {
            conv.setId(UUID.randomUUID().toString());
        }
        memoryStore.put(conv.getId(), conv);

        if (collection != null) {
            try {
                Document doc = convToDoc(conv);
                collection.replaceOne(Filters.eq("_id", conv.getId()), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
            } catch (Exception ignored) {}
        }
    }

    public Conversation findById(String id) {
        if (id == null) return null;
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.eq("_id", id)).first();
                if (doc != null) return docToConv(doc);
            } catch (Exception ignored) {}
        }
        return memoryStore.get(id);
    }

    public Conversation findDirectConversation(String user1Id, String user2Id) {
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.and(
                                Filters.eq("type", Conversation.TYPE_DIRECT),
                                Filters.all("member_ids", user1Id, user2Id),
                                Filters.size("member_ids", 2)))
                        .first();
                if (doc != null) {
                    Conversation conversation = docToConv(doc);
                    memoryStore.put(conversation.getId(), conversation);
                    return conversation;
                }
            } catch (Exception ignored) {}
        }

        for (Conversation c : memoryStore.values()) {
            if (Conversation.TYPE_DIRECT.equals(c.getType())) {
                List<String> m = c.getMemberIds();
                if (m.contains(user1Id) && m.contains(user2Id) && m.size() == 2) {
                    return c;
                }
            }
        }
        return null;
    }

    public List<Conversation> getUserConversations(String userId) {
        if (collection != null) {
            try {
                for (Document doc : collection.find(Filters.in("member_ids", userId))
                        .sort(Sorts.descending("last_message_at"))) {
                    Conversation conversation = docToConv(doc);
                    memoryStore.put(conversation.getId(), conversation);
                }
            } catch (Exception ignored) {}
        }

        List<Conversation> list = new ArrayList<>();
        for (Conversation c : memoryStore.values()) {
            if (c.getMemberIds() != null && c.getMemberIds().contains(userId)) {
                list.add(c);
            }
        }
        list.sort((a, b) -> {
            Instant ta = a.getLastMessageAt() != null ? a.getLastMessageAt() : a.getCreatedAt();
            Instant tb = b.getLastMessageAt() != null ? b.getLastMessageAt() : b.getCreatedAt();
            return tb.compareTo(ta);
        });
        return list;
    }

    public void updateLastMessage(String conversationId, String snippet, Instant timestamp) {
        Conversation c = findById(conversationId);
        if (c != null) {
            c.setLastMessageSnippet(snippet);
            c.setLastMessageAt(timestamp);
            memoryStore.put(conversationId, c);

            if (collection != null) {
                try {
                    collection.updateOne(Filters.eq("_id", conversationId),
                            Updates.combine(
                                    Updates.set("last_message_snippet", snippet),
                                    Updates.set("last_message_at", Date.from(timestamp))
                            ));
                } catch (Exception ignored) {}
            }
        }
    }

    public long count() {
        if (collection != null) {
            try {
                return collection.countDocuments();
            } catch (Exception ignored) {}
        }
        return memoryStore.size();
    }
}
