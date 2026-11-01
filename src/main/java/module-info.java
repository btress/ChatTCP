module org.example.ungdungchat {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires com.google.gson;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;
    requires jbcrypt;
    requires MaterialFX;
    requires java.logging;

    opens org.example.ungdungchat to javafx.fxml, com.google.gson;
    opens org.example.ungdungchat.shared.model to com.google.gson, javafx.fxml;
    opens org.example.ungdungchat.shared.protocol to com.google.gson, javafx.fxml;
    opens org.example.ungdungchat.server.db to com.google.gson;

    exports org.example.ungdungchat;
    exports org.example.ungdungchat.shared.model;
    exports org.example.ungdungchat.shared.protocol;
    exports org.example.ungdungchat.server;
    exports org.example.ungdungchat.server.db;
    exports org.example.ungdungchat.client.audio;
}
