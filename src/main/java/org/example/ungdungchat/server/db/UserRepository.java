package org.example.ungdungchat.server.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.example.ungdungchat.shared.model.User;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserRepository {
    private final MongoCollection<Document> collection;
    private final Map<String, User> memoryStore = new ConcurrentHashMap<>();

    public UserRepository() {
        this.collection = MongoDBManager.getInstance().getCollection("users");
    }

    private User docToUser(Document doc) {
        if (doc == null) return null;
        User u = new User();
        u.setId(doc.getString("_id"));
        u.setUsername(doc.getString("username"));
        u.setEmail(doc.getString("email"));
        u.setPasswordHash(doc.getString("password_hash"));
        u.setDisplayName(doc.getString("display_name"));
        u.setAvatarUrl(doc.getString("avatar_url"));
        u.setBio(doc.getString("bio"));
        u.setStatus(doc.getString("status"));
        Date ls = doc.getDate("last_seen");
        if (ls != null) u.setLastSeen(ls.toInstant());
        Boolean b = doc.getBoolean("is_banned");
        u.setBanned(b != null && b);
        Date ca = doc.getDate("created_at");
        if (ca != null) u.setCreatedAt(ca.toInstant());
        return u;
    }

    private Document userToDoc(User u) {
        Document doc = new Document("_id", u.getId())
                .append("username", u.getUsername())
                .append("email", u.getEmail())
                .append("password_hash", u.getPasswordHash())
                .append("display_name", u.getDisplayName())
                .append("avatar_url", u.getAvatarUrl())
                .append("bio", u.getBio())
                .append("status", u.getStatus())
                .append("last_seen", u.getLastSeen() != null ? Date.from(u.getLastSeen()) : new Date())
                .append("is_banned", u.isBanned())
                .append("created_at", u.getCreatedAt() != null ? Date.from(u.getCreatedAt()) : new Date());
        return doc;
    }

    public synchronized boolean save(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        memoryStore.put(user.getId(), user);

        if (collection != null) {
            try {
                Document doc = userToDoc(user);
                collection.replaceOne(Filters.eq("_id", user.getId()), doc, new com.mongodb.client.model.ReplaceOptions().upsert(true));
                return true;
            } catch (Exception e) {
                // If duplicate key error, return false
                return false;
            }
        }
        return true;
    }

    public User findById(String id) {
        if (id == null) return null;
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.eq("_id", id)).first();
                if (doc != null) return docToUser(doc);
            } catch (Exception ignored) {}
        }
        return memoryStore.get(id);
    }

    public User findByUsername(String username) {
        if (username == null) return null;
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.eq("username", username)).first();
                if (doc != null) return docToUser(doc);
            } catch (Exception ignored) {}
        }
        for (User u : memoryStore.values()) {
            if (u.getUsername().equalsIgnoreCase(username)) return u;
        }
        return null;
    }

    public User findByEmail(String email) {
        if (email == null) return null;
        if (collection != null) {
            try {
                Document doc = collection.find(Filters.eq("email", email)).first();
                if (doc != null) return docToUser(doc);
            } catch (Exception ignored) {}
        }
        for (User u : memoryStore.values()) {
            if (email.equalsIgnoreCase(u.getEmail())) return u;
        }
        return null;
    }

    public List<User> searchUsers(String query, String excludeUserId) {
        List<User> list = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";
        for (User u : memoryStore.values()) {
            if (excludeUserId != null && excludeUserId.equals(u.getId())) continue;
            if (u.getUsername().toLowerCase().contains(q) || u.getDisplayName().toLowerCase().contains(q)) {
                list.add(u);
            }
        }
        return list;
    }

    public void updateStatus(String userId, String status) {
        User u = findById(userId);
        if (u != null) {
            u.setStatus(status);
            u.setLastSeen(Instant.now());
            memoryStore.put(userId, u);
            if (collection != null) {
                try {
                    collection.updateOne(Filters.eq("_id", userId),
                            Updates.combine(Updates.set("status", status), Updates.set("last_seen", new Date())));
                } catch (Exception ignored) {}
            }
        }
    }

    public void updateProfile(String userId, String displayName, String bio, String avatarUrl) {
        User u = findById(userId);
        if (u != null) {
            if (displayName != null) u.setDisplayName(displayName);
            if (bio != null) u.setBio(bio);
            if (avatarUrl != null) u.setAvatarUrl(avatarUrl);
            memoryStore.put(userId, u);
            if (collection != null) {
                try {
                    collection.updateOne(Filters.eq("_id", userId),
                            Updates.combine(
                                    Updates.set("display_name", u.getDisplayName()),
                                    Updates.set("bio", u.getBio()),
                                    Updates.set("avatar_url", u.getAvatarUrl())));
                } catch (Exception ignored) {}
            }
        }
    }

    public void setBanned(String userId, boolean banned) {
        User u = findById(userId);
        if (u != null) {
            u.setBanned(banned);
            memoryStore.put(userId, u);
            if (collection != null) {
                try {
                    collection.updateOne(Filters.eq("_id", userId), Updates.set("is_banned", banned));
                } catch (Exception ignored) {}
            }
        }
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(memoryStore.values());
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
