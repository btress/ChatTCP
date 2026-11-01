package org.example.ungdungchat;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.ungdungchat.shared.model.User;
import org.example.ungdungchat.shared.protocol.Packet;

import java.io.IOException;

public class HelloController {

    @FXML private Button btnTabLogin;
    @FXML private Button btnTabRegister;
    @FXML private VBox formLogin;
    @FXML private VBox formRegister;

    // Login fields
    @FXML private TextField txtLoginIdentifier;
    @FXML private PasswordField txtLoginPassword;
    @FXML private TextField txtHost;
    @FXML private TextField txtPort;
    @FXML private Button btnLogin;

    // Register fields
    @FXML private TextField txtRegUsername;
    @FXML private TextField txtRegDisplayName;
    @FXML private TextField txtRegEmail;
    @FXML private PasswordField txtRegPassword;
    @FXML private PasswordField txtRegConfirmPassword;
    @FXML private Button btnRegister;

    @FXML private Label lblStatus;

    private ChatClient client;

    @FXML
    public void initialize() {
        onSwitchToLogin();
    }

    @FXML
    protected void onSwitchToLogin() {
        formLogin.setVisible(true);
        formLogin.setManaged(true);
        formRegister.setVisible(false);
        formRegister.setManaged(false);

        btnTabLogin.getStyleClass().removeAll("tab-active");
        btnTabRegister.getStyleClass().removeAll("tab-active");
        btnTabLogin.getStyleClass().add("tab-active");
        lblStatus.setText("");
    }

    @FXML
    protected void onSwitchToRegister() {
        formLogin.setVisible(false);
        formLogin.setManaged(false);
        formRegister.setVisible(true);
        formRegister.setManaged(true);

        btnTabLogin.getStyleClass().removeAll("tab-active");
        btnTabRegister.getStyleClass().removeAll("tab-active");
        btnTabRegister.getStyleClass().add("tab-active");
        lblStatus.setText("");
    }

    private boolean ensureConnected() {
        if (client != null && client.isConnected()) {
            return true;
        }

        String host = txtHost.getText().trim();
        if (host.isEmpty()) host = "localhost";

        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            showError("Cổng kết nối (Port) không hợp lệ.");
            return false;
        }

        try {
            client = new ChatClient(host, port);
            client.connect();
            return true;
        } catch (IOException e) {
            showError("Không thể kết nối đến máy chủ: " + e.getMessage());
            return false;
        }
    }

    @FXML
    protected void onLogin() {
        String identifier = txtLoginIdentifier.getText().trim();
        String password = txtLoginPassword.getText();

        if (identifier.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập tài khoản và mật khẩu.");
            return;
        }

        btnLogin.setDisable(true);
        lblStatus.getStyleClass().removeAll("status-error", "status-success");
        lblStatus.getStyleClass().add("status-info");
        lblStatus.setText("Đang đăng nhập...");

        new Thread(() -> {
            if (!ensureConnected()) {
                Platform.runLater(() -> btnLogin.setDisable(false));
                return;
            }

            client.login(identifier, password)
                    .thenAccept(user -> Platform.runLater(() -> openMainMessenger(user)))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            btnLogin.setDisable(false);
                            showError("Đăng nhập thất bại: " + ex.getCause().getMessage());
                        });
                        return null;
                    });
        }).start();
    }

    @FXML
    protected void onRegister() {
        String username = txtRegUsername.getText().trim();
        String displayName = txtRegDisplayName.getText().trim();
        String email = txtRegEmail.getText().trim();
        String password = txtRegPassword.getText();
        String confirmPassword = txtRegConfirmPassword.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng điền đầy đủ tên đăng nhập và mật khẩu.");
            return;
        }
        if (username.length() < 3) {
            showError("Tên đăng nhập phải từ 3 ký tự trở lên.");
            return;
        }
        if (password.length() < 6) {
            showError("Mật khẩu phải từ 6 ký tự trở lên.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không trùng khớp.");
            return;
        }

        btnRegister.setDisable(true);
        lblStatus.getStyleClass().removeAll("status-error", "status-success");
        lblStatus.getStyleClass().add("status-info");
        lblStatus.setText("Đang tạo tài khoản...");

        new Thread(() -> {
            if (!ensureConnected()) {
                Platform.runLater(() -> btnRegister.setDisable(false));
                return;
            }

            client.register(username, email, password, displayName)
                    .thenAccept(packet -> Platform.runLater(() -> {
                        btnRegister.setDisable(false);
                        if (packet.isOk()) {
                            showSuccess("Đăng ký thành công! Hãy đăng nhập ngay.");
                            txtLoginIdentifier.setText(username);
                            txtLoginPassword.setText(password);
                            onSwitchToLogin();
                        } else {
                            showError(packet.getMessage());
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            btnRegister.setDisable(false);
                            showError("Đăng ký thất bại: " + ex.getMessage());
                        });
                        return null;
                    });
        }).start();
    }

    private void openMainMessenger(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("chat-view.fxml"));
            Scene scene = new Scene(loader.load());

            ChatController controller = loader.getController();
            Stage stage = (Stage) btnLogin.getScene().getWindow();
            controller.init(client, stage);

            stage.setScene(scene);
            stage.setTitle("Messenger Desktop - " + user.getDisplayName());
            stage.setMinWidth(960);
            stage.setMinHeight(640);
            stage.centerOnScreen();
        } catch (IOException e) {
            showError("Lỗi nạp giao diện chính: " + e.getMessage());
            btnLogin.setDisable(false);
        }
    }

    private void showError(String msg) {
        lblStatus.getStyleClass().removeAll("status-info", "status-success");
        lblStatus.getStyleClass().add("status-error");
        lblStatus.setText(msg);
    }

    private void showSuccess(String msg) {
        lblStatus.getStyleClass().removeAll("status-info", "status-error");
        lblStatus.getStyleClass().add("status-success");
        lblStatus.setText(msg);
    }
}
