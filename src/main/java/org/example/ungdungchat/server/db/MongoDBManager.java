package org.example.ungdungchat.server.db;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.BsonType;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MongoDBManager {
    private static final Logger LOGGER = Logger.getLogger(MongoDBManager.class.getName());
    private static MongoDBManager instance;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private boolean isConnected = false;

    private MongoDBManager() {
        initConnection();
    }

    public static synchronized MongoDBManager getInstance() {
        if (instance == null) {
            instance = new MongoDBManager();
        }
        return instance;
    }

    private void initConnection() {
        String uri = System.getenv("MONGODB_URI");
        if (uri == null || uri.isBlank()) {
            uri = System.getProperty("mongodb.uri", "mongodb://localhost:27017");
        }

        try {
            LOGGER.info("Connecting to MongoDB at: " + (uri.contains("@") ? "Atlas/Remote Cluster" : uri));
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri))
                    .applyToSocketSettings(builder ->
                            builder.connectTimeout(3, TimeUnit.SECONDS)
                                   .readTimeout(3, TimeUnit.SECONDS))
                    .applyToClusterSettings(builder ->
                            builder.serverSelectionTimeout(3, TimeUnit.SECONDS))
                    .build();

            mongoClient = MongoClients.create(settings);
            database = mongoClient.getDatabase("messenger_db");

            // Ping server to verify connectivity
            database.runCommand(new Document("ping", 1));
            isConnected = true;
            LOGGER.info("Connected to MongoDB successfully!");

            createIndexes();
        } catch (Exception e) {
            isConnected = false;
            LOGGER.log(Level.WARNING, "Could not connect to MongoDB server (" + e.getMessage() + "). Operating in in-memory resilience mode.");
        }
    }

    private void createIndexes() {
        if (!isConnected || database == null) return;
        try {
            // users: unique username, unique email
            MongoCollection<Document> users = database.getCollection("users");
            users.createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
            users.createIndex(Indexes.ascending("email"), new IndexOptions().unique(true));

            // Migrate the legacy index whose field name did not match the stored document schema.
            MongoCollection<Document> messages = database.getCollection("messages");
            for (Document index : messages.listIndexes()) {
                Document key = index.get("key", Document.class);
                if (key != null
                        && key.size() == 1
                        && key.containsKey("clientMessageId")
                        && Boolean.TRUE.equals(index.getBoolean("unique"))) {
                    messages.dropIndex(index.getString("name"));
                    LOGGER.info("Removed legacy clientMessageId index.");
                }
            }
            messages.createIndex(
                    Indexes.ascending("client_message_id"),
                    new IndexOptions()
                            .unique(true)
                            .name("client_message_id_unique")
                            .partialFilterExpression(Filters.type("client_message_id", BsonType.STRING)));
            messages.createIndex(Indexes.ascending("conversation_id", "created_at"));

            // conversations: index on memberIds
            MongoCollection<Document> conversations = database.getCollection("conversations");
            conversations.createIndex(Indexes.ascending("memberIds"));

            // friendships: compound index
            MongoCollection<Document> friendships = database.getCollection("friendships");
            friendships.createIndex(Indexes.ascending("user1Id", "user2Id"));

            LOGGER.info("MongoDB unique indexes created successfully.");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error creating MongoDB indexes: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getCollection(String name) {
        if (!isConnected || database == null) return null;
        try {
            return database.getCollection(name);
        } catch (Exception e) {
            return null;
        }
    }

    public void close() {
        if (mongoClient != null) {
            try {
                mongoClient.close();
            } catch (Exception ignored) {}
        }
    }
}
