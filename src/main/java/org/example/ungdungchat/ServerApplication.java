package org.example.ungdungchat;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.ungdungchat.server.ClientSession;
import org.example.ungdungchat.server.SessionManager;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ServerApplication extends Application {
    private static final Logger APP_LOGGER = Logger.getLogger("org.example.ungdungchat");
    private static final DateTimeFormatter CONNECTED_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ExecutorService lifecycleExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "server-dashboard-lifecycle");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean closing = new AtomicBoolean();

    private final Label statusLabel = new Label("Đang khởi tạo...");
    private final Label connectionCount = new Label("0");
    private final Label onlineCount = new Label("0");
    private final Label messageCount = new Label("0");
    private final Label uptimeLabel = new Label("00:00:00");
    private final Label memoryUsage = new Label("0 MB");
    private final Label actionStatus = new Label();
    private final TableView<ClientSession> sessionsTable = new TableView<>();
    private final TextArea logArea = new TextArea();
    private final TextField usernameField = new TextField();
    private final TextField broadcastField = new TextField();
    private final Button startButton = new Button("Khởi động server");
    private final Button stopButton = new Button("Dừng server");

    private TCPServer server;
    private Handler logHandler;
    private Timeline refreshTimer;

    @Override
    public void start(Stage stage) {
        logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (!isLoggable(record)) {
                    return;
                }
                String message = String.format("[%1$tT] %2$s %3$s%n",
                        record.getMillis(), record.getLevel().getName(), record.getMessage());
                Platform.runLater(() -> appendLog(message));
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logHandler.setLevel(Level.ALL);
        APP_LOGGER.addHandler(logHandler);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("server-root");
        root.setTop(createHeader());
        root.setCenter(createMainContent());
        root.setBottom(createAdminPanel());

        Scene scene = new Scene(root, 1120, 780);
        scene.getStylesheets().add(ServerApplication.class.getResource("style.css").toExternalForm());
        stage.setTitle("Messenger Server Manager");
        stage.setMinWidth(900);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> closeDashboard());
        stage.show();

        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshDashboard()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
        startServer();
    }

    private HBox createHeader() {
        Label title = new Label("MESSENGER SERVER");
        title.getStyleClass().add("server-title");
        Label subtitle = new Label("Quản lý TCP server · Cổng " + TCPServer.PORT);
        subtitle.getStyleClass().add("server-subtitle");

        VBox heading = new VBox(4, title, subtitle);
        HBox.setHgrow(heading, Priority.ALWAYS);

        statusLabel.getStyleClass().add("server-status");
        startButton.getStyleClass().add("server-primary");
        stopButton.getStyleClass().add("server-danger");
        startButton.setOnAction(event -> startServer());
        stopButton.setOnAction(event -> stopServer());

        HBox actions = new HBox(10, statusLabel, startButton, stopButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox header = new HBox(20, heading, actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(22, 26, 22, 26));
        header.getStyleClass().add("server-header");
        return header;
    }

    private VBox createMainContent() {
        HBox metrics = new HBox(14,
                createMetricCard("KẾT NỐI", connectionCount),
                createMetricCard("NGƯỜI DÙNG ONLINE", onlineCount),
                createMetricCard("TIN NHẮN", messageCount),
                createMetricCard("THỜI GIAN HOẠT ĐỘNG", uptimeLabel),
                createMetricCard("RAM JVM", memoryUsage));

        Label sessionsTitle = new Label("Kết nối hiện tại");
        sessionsTitle.getStyleClass().add("server-section-title");
        configureSessionsTable();

        VBox sessionsPanel = new VBox(12, sessionsTitle, sessionsTable);
        sessionsPanel.getStyleClass().add("server-panel");
        sessionsPanel.setPadding(new Insets(18));
        VBox.setVgrow(sessionsTable, Priority.ALWAYS);
        VBox.setVgrow(sessionsPanel, Priority.ALWAYS);

        Label logsTitle = new Label("Nhật ký server");
        logsTitle.getStyleClass().add("server-section-title");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        logArea.setPrefRowCount(7);
        logArea.getStyleClass().add("server-log");

        VBox logsPanel = new VBox(10, logsTitle, logArea);
        logsPanel.getStyleClass().add("server-panel");
        logsPanel.setPadding(new Insets(18));

        VBox content = new VBox(14, metrics, sessionsPanel, logsPanel);
        content.setPadding(new Insets(18, 24, 14, 24));
        VBox.setVgrow(sessionsPanel, Priority.ALWAYS);
        return content;
    }

    private VBox createMetricCard(String title, Label value) {
        Label caption = new Label(title);
        caption.getStyleClass().add("server-metric-caption");
        value.getStyleClass().add("server-metric-value");
        VBox card = new VBox(9, caption, value);
        card.getStyleClass().add("server-panel");
        card.setPadding(new Insets(16));
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox createAdminPanel() {
        usernameField.setPromptText("Tên tài khoản");
        usernameField.setPrefWidth(180);
        broadcastField.setPromptText("Nội dung thông báo tới mọi người");
        HBox.setHgrow(broadcastField, Priority.ALWAYS);

        Button kickButton = new Button("Kick");
        Button banButton = new Button("Ban");
        Button unbanButton = new Button("Bỏ ban");
        Button broadcastButton = new Button("Gửi thông báo");
        kickButton.setOnAction(event -> runUserAction("kick"));
        banButton.setOnAction(event -> runUserAction("ban"));
        unbanButton.setOnAction(event -> runUserAction("unban"));
        broadcastButton.getStyleClass().add("server-primary");
        broadcastButton.setOnAction(event -> sendBroadcast());

        HBox userActions = new HBox(8, usernameField, kickButton, banButton, unbanButton);
        userActions.setAlignment(Pos.CENTER_LEFT);
        HBox broadcastActions = new HBox(8, broadcastField, broadcastButton);
        broadcastActions.setAlignment(Pos.CENTER_LEFT);

        actionStatus.getStyleClass().add("server-action-status");
        VBox panel = new VBox(10, userActions, broadcastActions, actionStatus);
        panel.getStyleClass().add("server-admin-panel");
        panel.setPadding(new Insets(14, 24, 18, 24));
        return panel;
    }

    private void configureSessionsTable() {
        TableColumn<ClientSession, String> usernameColumn = new TableColumn<>("Tài khoản");
        usernameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(valueOrGuest(cell.getValue().getUsername())));
        usernameColumn.setPrefWidth(170);

        TableColumn<ClientSession, String> displayNameColumn = new TableColumn<>("Tên hiển thị");
        displayNameColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(valueOrGuest(cell.getValue().getDisplayName())));
        displayNameColumn.setPrefWidth(200);

        TableColumn<ClientSession, String> addressColumn = new TableColumn<>("Địa chỉ");
        addressColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getRemoteAddress()));
        addressColumn.setPrefWidth(250);

        TableColumn<ClientSession, String> authColumn = new TableColumn<>("Trạng thái");
        authColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().isAuthenticated() ? "Đã đăng nhập" : "Chưa đăng nhập"));
        authColumn.setPrefWidth(160);

        TableColumn<ClientSession, String> connectedColumn = new TableColumn<>("Kết nối lúc");
        connectedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(CONNECTED_FORMAT.format(cell.getValue().getConnectedAt())));
        connectedColumn.setPrefWidth(140);

        sessionsTable.getColumns().setAll(java.util.List.of(
                usernameColumn, displayNameColumn, addressColumn, authColumn, connectedColumn));
        sessionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        sessionsTable.getStyleClass().add("server-table");
        sessionsTable.setItems(FXCollections.observableArrayList());
    }

    private String valueOrGuest(String value) {
        return value == null || value.isBlank() ? "Khách" : value;
    }

    private void startServer() {
        if (closing.get() || server != null) {
            return;
        }
        startButton.setDisable(true);
        statusLabel.setText("Đang khởi động...");
        lifecycleExecutor.execute(() -> {
            TCPServer newServer = null;
            try {
                newServer = new TCPServer();
                if (closing.get()) {
                    newServer.shutdown();
                    return;
                }
                newServer.start();
                if (closing.get()) {
                    newServer.shutdown();
                    return;
                }
                server = newServer;
                Platform.runLater(() -> {
                    statusLabel.setText("Đang chạy · cổng " + TCPServer.PORT);
                    startButton.setDisable(true);
                    stopButton.setDisable(false);
                    appendLog("Server đã khởi động trên cổng " + TCPServer.PORT + System.lineSeparator());
                });
            } catch (IOException | RuntimeException e) {
                if (newServer != null) {
                    newServer.shutdown();
                }
                Platform.runLater(() -> {
                    statusLabel.setText("Không khởi động được");
                    startButton.setDisable(false);
                    stopButton.setDisable(true);
                    actionStatus.setText("Lỗi: " + e.getMessage());
                    appendLog("Không khởi động được server: " + e.getMessage() + System.lineSeparator());
                });
            }
        });
    }

    private void stopServer() {
        TCPServer current = server;
        if (current == null) {
            return;
        }
        stopButton.setDisable(true);
        statusLabel.setText("Đang dừng...");
        lifecycleExecutor.execute(() -> {
            current.shutdown();
            if (server == current) {
                server = null;
            }
            Platform.runLater(() -> {
                statusLabel.setText("Đã dừng");
                startButton.setDisable(false);
                stopButton.setDisable(true);
                sessionsTable.getItems().clear();
                appendLog("Server đã dừng." + System.lineSeparator());
            });
        });
    }

    private void runUserAction(String action) {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            actionStatus.setText("Nhập tên tài khoản trước.");
            return;
        }

        lifecycleExecutor.execute(() -> {
            TCPServer current = server;
            if (current == null || !current.isRunning()) {
                showActionResult("Server chưa chạy.");
                return;
            }
            boolean success = switch (action) {
                case "kick" -> current.kickUserByUsername(username);
                case "ban" -> current.setUserBanned(username, true);
                case "unban" -> current.setUserBanned(username, false);
                default -> false;
            };
            showActionResult(success
                    ? "Đã thực hiện " + action + " với " + username + "."
                    : "Không tìm thấy tài khoản " + username + ".");
        });
    }

    private void sendBroadcast() {
        String message = broadcastField.getText().trim();
        if (message.isEmpty()) {
            actionStatus.setText("Nhập nội dung thông báo trước.");
            return;
        }
        lifecycleExecutor.execute(() -> {
            TCPServer current = server;
            boolean sent = current != null && current.broadcastNotice(message);
            showActionResult(sent ? "Đã gửi thông báo." : "Server chưa chạy.");
            if (sent) {
                Platform.runLater(broadcastField::clear);
            }
        });
    }

    private void showActionResult(String message) {
        Platform.runLater(() -> actionStatus.setText(message));
    }

    private void refreshDashboard() {
        TCPServer current = server;
        if (current == null || !current.isRunning()) {
            if (current != null) {
                statusLabel.setText("Server đã dừng");
                startButton.setDisable(false);
                stopButton.setDisable(true);
                server = null;
            }
            return;
        }

        SessionManager manager = current.getSessionManager();
        connectionCount.setText(Integer.toString(manager.getActiveConnectionCount()));
        onlineCount.setText(Integer.toString(manager.getOnlineUserCount()));
        messageCount.setText(Long.toString(manager.getTotalMessagesProcessed()));
        uptimeLabel.setText(manager.getUptimeFormatted());
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        memoryUsage.setText(usedMemory + " MB");
        sessionsTable.getItems().setAll(manager.getAllActiveSessions());
    }

    private void appendLog(String message) {
        logArea.appendText(message);
        if (logArea.getLength() > 50_000) {
            logArea.deleteText(0, logArea.getLength() - 30_000);
        }
    }

    private void closeDashboard() {
        if (!closing.compareAndSet(false, true)) {
            return;
        }
        if (refreshTimer != null) {
            refreshTimer.stop();
        }
        if (logHandler != null) {
            APP_LOGGER.removeHandler(logHandler);
        }
        TCPServer current = server;
        if (current != null) {
            current.shutdown();
        }
        lifecycleExecutor.shutdownNow();
    }

    @Override
    public void stop() {
        closeDashboard();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
