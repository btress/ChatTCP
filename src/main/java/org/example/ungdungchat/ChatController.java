package org.example.ungdungchat;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.ungdungchat.client.audio.AudioService;
import org.example.ungdungchat.shared.model.CallSession;
import org.example.ungdungchat.shared.model.Conversation;
import org.example.ungdungchat.shared.model.FriendRequest;
import org.example.ungdungchat.shared.model.Message;
import org.example.ungdungchat.shared.model.User;
import org.example.ungdungchat.shared.protocol.EventType;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatController implements ChatClient.ClientListener {

    @FXML private BorderPane rootPane;
    @FXML private Button btnMyProfile;
    @FXML private Label lblMyAvatarInitial;

    // Navigation buttons
    @FXML private Button navBtnChats;
    @FXML private Button navBtnFriends;
    @FXML private Button navBtnRequests;
    @FXML private Button navBtnAdmin;
    @FXML private Button btnThemeToggle;

    // Conversation List Column
    @FXML private Label lblMasterTitle;
    @FXML private TextField txtSearch;
    @FXML private Button filterAll;
    @FXML private Button filterUnread;
    @FXML private Button filterGroups;
    @FXML private ListView<Conversation> listConversations;

    // Chat Window Column
    @FXML private BorderPane chatPane;
    @FXML private Label lblChatHeaderAvatar;
    @FXML private Region dotChatHeaderOnline;
    @FXML private Label lblChatHeaderTitle;
    @FXML private Label lblChatHeaderStatus;
    @FXML private Button btnVoiceCall;

    @FXML private ScrollPane scrollMessages;
    @FXML private VBox boxMessages;
    @FXML private Button btnLoadMore;

    @FXML private Label lblTypingIndicator;
    @FXML private HBox barReplyPreview;
    @FXML private Label lblReplyTargetText;
    @FXML private TextArea txtComposer;
    @FXML private Button btnVoiceRecord;
    @FXML private Button btnSend;

    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");
    private final AudioService audioService = new AudioService();

    private ChatClient client;
    private Stage stage;
    private Conversation activeConversation;
    private List<Conversation> allConversations = new ArrayList<>();
    private final Map<String, VBox> bubbleNodes = new ConcurrentHashMap<>();
    private final Map<String, VBox> bubbleNodesByClientMessageId = new ConcurrentHashMap<>();
    private final Map<String, Message> displayedMessagesByClientMessageId = new ConcurrentHashMap<>();
    private final Map<String, Label> bubbleTimeLabelsByClientMessageId = new ConcurrentHashMap<>();

    private Message replyingMessage = null;
    private boolean isRecordingVoice = false;
    private boolean isDarkMode = false;
    private javax.swing.Timer typingDebounceTimer;

    public void init(ChatClient client, Stage stage) {
        this.client = client;
        this.stage = stage;
        client.setListener(this);

        User me = client.getCurrentUser();
        if (me != null) {
            String initial = me.getDisplayName().isEmpty() ? "U" : me.getDisplayName().substring(0, 1).toUpperCase();
            lblMyAvatarInitial.setText(initial);
        }

        setupComposerKeyEvents();
        setupConversationListRendering();
        loadConversations();

        stage.setOnCloseRequest(e -> {
            audioService.close();
            client.close();
            Platform.exit();
            System.exit(0);
        });
    }

    private void setupComposerKeyEvents() {
        txtComposer.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER) {
                if (event.isShiftDown()) {
                    txtComposer.appendText("\n");
                } else {
                    event.consume();
                    onSendMessage();
                }
            } else {
                // Send typing indicator with throttle
                if (activeConversation != null) {
                    client.sendTyping(activeConversation.getId(), true);
                }
            }
        });
    }

    private void setupConversationListRendering() {
        listConversations.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Conversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(12);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.setPadding(new Insets(6, 4, 6, 4));

                    // Avatar
                    StackPane avatarStack = new StackPane();
                    Label avatar = new Label(item.getName() != null && !item.getName().isEmpty()
                            ? item.getName().substring(0, 1).toUpperCase() : "C");
                    avatar.getStyleClass().add("cell-avatar");

                    Region dot = new Region();
                    dot.getStyleClass().add("cell-online-dot");
                    avatarStack.getChildren().addAll(avatar, dot);
                    StackPane.setAlignment(dot, Pos.BOTTOM_RIGHT);

                    // Text Info
                    VBox textCol = new VBox(3);
                    textCol.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(textCol, Priority.ALWAYS);

                    HBox titleRow = new HBox(6);
                    titleRow.setAlignment(Pos.CENTER_LEFT);

                    Label nameLbl = new Label(item.getName() != null ? item.getName() : "Phòng chat");
                    nameLbl.getStyleClass().add("cell-name");
                    nameLbl.setMaxWidth(160);

                    Region spacer = new Region();
                    HBox.setHgrow(spacer, Priority.ALWAYS);

                    Label timeLbl = new Label(item.getLastMessageAt() != null
                            ? item.getLastMessageAt().atZone(java.time.ZoneId.systemDefault()).format(timeFormat) : "");
                    timeLbl.getStyleClass().add("cell-time");

                    titleRow.getChildren().addAll(nameLbl, spacer, timeLbl);

                    Label snippetLbl = new Label(item.getLastMessageSnippet() != null
                            ? item.getLastMessageSnippet() : "Chưa có tin nhắn");
                    snippetLbl.getStyleClass().add("cell-snippet");
                    snippetLbl.setMaxWidth(200);

                    textCol.getChildren().addAll(titleRow, snippetLbl);
                    box.getChildren().addAll(avatarStack, textCol);
                    setGraphic(box);
                }
            }
        });

        listConversations.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectConversation(newVal);
            }
        });

        // Search filtering
        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            filterConversations(newVal);
        });
    }

    private void filterConversations(String query) {
        if (query == null || query.isBlank()) {
            listConversations.getItems().setAll(allConversations);
            return;
        }
        String q = query.trim().toLowerCase();
        List<Conversation> filtered = new ArrayList<>();
        for (Conversation c : allConversations) {
            if (c.getName() != null && c.getName().toLowerCase().contains(q)) {
                filtered.add(c);
            }
        }
        listConversations.getItems().setAll(filtered);
    }

    private void loadConversations() {
        client.getConversations().thenAccept(list -> Platform.runLater(() -> {
            allConversations = new ArrayList<>(list);
            listConversations.getItems().setAll(allConversations);
            if (!allConversations.isEmpty() && activeConversation == null) {
                listConversations.getSelectionModel().select(0);
            }
        }));
    }

    private void loadConversationsForIncomingMessage(String conversationId) {
        client.getConversations().thenAccept(conversations -> Platform.runLater(() -> {
            allConversations = new ArrayList<>(conversations);
            filterConversations(txtSearch.getText());

            Conversation incomingConversation = allConversations.stream()
                    .filter(conversation -> conversationId.equals(conversation.getId()))
                    .findFirst()
                    .orElse(null);
            if (incomingConversation == null) {
                return;
            }

            int index = listConversations.getItems().indexOf(incomingConversation);
            if (index >= 0) {
                listConversations.getSelectionModel().select(index);
            } else if (txtSearch.getText() == null || txtSearch.getText().isBlank()) {
                listConversations.getSelectionModel().select(incomingConversation);
            }
        }));
    }

    private void selectConversation(Conversation conv) {
        activeConversation = conv;
        lblChatHeaderTitle.setText(conv.getName() != null ? conv.getName() : "Hội thoại");
        lblChatHeaderAvatar.setText(conv.getName() != null && !conv.getName().isEmpty()
                ? conv.getName().substring(0, 1).toUpperCase() : "C");

        if (Conversation.TYPE_DIRECT.equals(conv.getType())) {
            lblChatHeaderStatus.setText("Đang hoạt động");
        } else {
            lblChatHeaderStatus.setText((conv.getMemberIds() != null ? conv.getMemberIds().size() : 1) + " thành viên");
        }

        boxMessages.getChildren().clear();
        boxMessages.getChildren().add(new HBox(btnLoadMore));
        bubbleNodes.clear();
        bubbleNodesByClientMessageId.clear();
        displayedMessagesByClientMessageId.clear();
        bubbleTimeLabelsByClientMessageId.clear();

        // Load message history from MongoDB / server
        String conversationId = conv.getId();
        client.getMessageHistory(conversationId, 50).thenAccept(messages -> Platform.runLater(() -> {
            if (activeConversation == null || !conversationId.equals(activeConversation.getId())) {
                return;
            }
            for (Message message : messages) {
                appendMessageBubble(message);
            }
            scrollToLatestMessage();
        }));

        client.markMessageRead(conv.getId(), null);
    }

    // =========================================================================
    // UI Message Bubble Rendering
    // =========================================================================

    private void appendMessageBubble(Message msg) {
        VBox existingBubble = msg.getClientMessageId() != null
                ? bubbleNodesByClientMessageId.get(msg.getClientMessageId()) : null;
        if (existingBubble == null && msg.getId() != null) {
            existingBubble = bubbleNodes.get(msg.getId());
        }
        if (existingBubble != null) {
            reconcileMessageBubble(msg, existingBubble);
            return;
        }

        boolean isMine = msg.getSenderId() != null && msg.getSenderId().equals(client.getCurrentUser().getId());

        VBox bubble = new VBox(4);
        bubble.getStyleClass().add(isMine ? "bubble-box-right" : "bubble-box-left");

        // Sender name for other users in group chats
        if (!isMine && activeConversation != null && !Conversation.TYPE_DIRECT.equals(activeConversation.getType())) {
            Label senderLbl = new Label(msg.getSenderName());
            senderLbl.getStyleClass().add("bubble-sender-name");
            bubble.getChildren().add(senderLbl);
        }

        // Reply quote if replying
        if (msg.getReplyToContent() != null && !msg.getReplyToContent().isBlank()) {
            VBox replyBox = new VBox(2);
            replyBox.getStyleClass().add("bubble-reply-box");
            Label replySender = new Label("↩ " + (msg.getReplyToSender() != null ? msg.getReplyToSender() : ""));
            replySender.getStyleClass().add("bubble-reply-sender");
            Label replyContent = new Label(msg.getReplyToContent());
            replyContent.getStyleClass().add("bubble-reply-text");
            replyBox.getChildren().addAll(replySender, replyContent);
            bubble.getChildren().add(replyBox);
        }

        // Main content
        if (Message.TYPE_VOICE.equals(msg.getMessageType())) {
            HBox voiceCard = createVoiceCard(msg);
            bubble.getChildren().add(voiceCard);
        } else if (Message.TYPE_FILE.equals(msg.getMessageType()) || Message.TYPE_IMAGE.equals(msg.getMessageType())) {
            HBox fileCard = createFileCard(msg);
            bubble.getChildren().add(fileCard);
        } else {
            Label contentLbl = new Label(msg.getContent());
            contentLbl.getStyleClass().add(isMine ? "bubble-text-right" : "bubble-text-left");
            contentLbl.setWrapText(true);
            bubble.getChildren().add(contentLbl);
        }

        // Bottom timestamp & status tick
        String timeStr = msg.getCreatedAt() != null
                ? msg.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).format(timeFormat)
                : LocalTime.now().format(timeFormat);

        Label timeLbl = new Label(timeStr + statusMarker(msg, isMine));
        timeLbl.getStyleClass().add(isMine ? "bubble-time-right" : "bubble-time-left");
        if (Message.STATUS_FAILED.equals(msg.getStatus())) {
            timeLbl.setTooltip(new Tooltip("Không gửi được tin nhắn. Hãy gửi lại."));
        }
        bubble.getChildren().add(timeLbl);

        // Hover Context Action Menu
        ContextMenu menu = new ContextMenu();
        MenuItem itemReply = new MenuItem("↩ Trả lời");
        itemReply.setOnAction(e -> setReplyingMessage(msg));
        menu.getItems().add(itemReply);

        MenuItem itemReactHeart = new MenuItem("❤️ Yêu thích");
        itemReactHeart.setOnAction(e -> client.reactMessage(msg.getId(), "❤️", "ADD"));
        MenuItem itemReactLike = new MenuItem("👍 Thích");
        itemReactLike.setOnAction(e -> client.reactMessage(msg.getId(), "👍", "ADD"));
        MenuItem itemReactLaugh = new MenuItem("😂 Cười");
        itemReactLaugh.setOnAction(e -> client.reactMessage(msg.getId(), "😂", "ADD"));
        menu.getItems().addAll(itemReactHeart, itemReactLike, itemReactLaugh);

        if (isMine) {
            MenuItem itemEdit = new MenuItem("✏️ Chỉnh sửa");
            itemEdit.setOnAction(e -> onPromptEditMessage(msg));
            MenuItem itemRecall = new MenuItem("🗑️ Thu hồi tin nhắn");
            itemRecall.setOnAction(e -> client.recallMessage(msg.getId()));
            menu.getItems().addAll(new SeparatorMenuItem(), itemEdit, itemRecall);
        }

        bubble.setOnContextMenuRequested(e -> menu.show(bubble, e.getScreenX(), e.getScreenY()));

        HBox row = new HBox(8);
        row.getStyleClass().add(isMine ? "bubble-row-right" : "bubble-row-left");
        row.getChildren().add(bubble);

        if (msg.getId() != null) {
            bubbleNodes.put(msg.getId(), bubble);
        }
        if (msg.getClientMessageId() != null) {
            bubbleNodesByClientMessageId.put(msg.getClientMessageId(), bubble);
            displayedMessagesByClientMessageId.put(msg.getClientMessageId(), msg);
            bubbleTimeLabelsByClientMessageId.put(msg.getClientMessageId(), timeLbl);
        }

        boxMessages.getChildren().add(row);
    }

    private void scrollToLatestMessage() {
        Platform.runLater(() -> {
            if (scrollMessages.getScene() != null) {
                scrollMessages.applyCss();
                scrollMessages.layout();
                scrollMessages.setVvalue(1.0);
            }
        });
    }

    private String statusMarker(Message message, boolean isMine) {
        if (!isMine) {
            return "";
        }
        if (Message.STATUS_SENDING.equals(message.getStatus())) {
            return " …";
        }
        if (Message.STATUS_FAILED.equals(message.getStatus())) {
            return " !";
        }
        return Message.STATUS_SEEN.equals(message.getStatus()) ? " ✓✓" : " ✓";
    }

    private void reconcileMessageBubble(Message serverMessage, VBox bubble) {
        String clientMessageId = serverMessage.getClientMessageId();
        Message displayedMessage = clientMessageId != null
                ? displayedMessagesByClientMessageId.get(clientMessageId) : null;
        if (displayedMessage != null && displayedMessage != serverMessage) {
            displayedMessage.setId(serverMessage.getId());
            displayedMessage.setSenderId(serverMessage.getSenderId());
            displayedMessage.setSenderName(serverMessage.getSenderName());
            displayedMessage.setSenderAvatar(serverMessage.getSenderAvatar());
            displayedMessage.setContent(serverMessage.getContent());
            displayedMessage.setStatus(serverMessage.getStatus());
            displayedMessage.setCreatedAt(serverMessage.getCreatedAt());
            displayedMessage.setEdited(serverMessage.isEdited());
            displayedMessage.setRecalled(serverMessage.isRecalled());
            displayedMessage.setReactions(serverMessage.getReactions());
        }
        if (serverMessage.getId() != null) {
            bubbleNodes.put(serverMessage.getId(), bubble);
        }
        Label timeLabel = clientMessageId != null
                ? bubbleTimeLabelsByClientMessageId.get(clientMessageId) : null;
        if (timeLabel != null) {
            Message messageToDisplay = displayedMessage != null ? displayedMessage : serverMessage;
            boolean isMine = messageToDisplay.getSenderId() != null
                    && messageToDisplay.getSenderId().equals(client.getCurrentUser().getId());
            Instant createdAt = messageToDisplay.getCreatedAt();
            String time = createdAt != null
                    ? createdAt.atZone(java.time.ZoneId.systemDefault()).format(timeFormat)
                    : LocalTime.now().format(timeFormat);
            timeLabel.setText(time + statusMarker(messageToDisplay, isMine));
            if (Message.STATUS_FAILED.equals(messageToDisplay.getStatus())) {
                timeLabel.setTooltip(new Tooltip("Không gửi được tin nhắn. Hãy gửi lại."));
            } else {
                timeLabel.setTooltip(null);
            }
        }
    }

    private HBox createFileCard(Message msg) {
        HBox card = new HBox(10);
        card.getStyleClass().add("attachment-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label(Message.TYPE_IMAGE.equals(msg.getMessageType()) ? "🖼️" : "📎");
        icon.setStyle("-fx-font-size: 20px;");

        VBox meta = new VBox(2);
        Label name = new Label(msg.getAttachmentName() != null ? msg.getAttachmentName() : "Tệp tin");
        name.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label size = new Label(Protocol.formatSize(msg.getAttachmentSize()));
        size.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-text-secondary;");
        meta.getChildren().addAll(name, size);

        Button btnOpen = new Button("Mở");
        btnOpen.getStyleClass().add("btn-download-file");
        btnOpen.setOnAction(e -> {
            if (msg.getAttachmentUrl() != null) {
                try {
                    File f = new File(msg.getAttachmentUrl());
                    if (f.exists() && Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(f);
                    }
                } catch (Exception ignored) {}
            }
        });

        card.getChildren().addAll(icon, meta, btnOpen);
        return card;
    }

    private HBox createVoiceCard(Message msg) {
        HBox card = new HBox(10);
        card.getStyleClass().add("attachment-card");
        card.setAlignment(Pos.CENTER_LEFT);

        Button btnPlay = new Button("▶");
        btnPlay.getStyleClass().add("btn-download-file");
        btnPlay.setOnAction(e -> {
            if (msg.getAttachmentUrl() != null) {
                File f = new File(msg.getAttachmentUrl());
                if (f.exists()) {
                    audioService.playWavFile(f);
                }
            }
        });

        Label label = new Label("🎙️ Tin nhắn thoại (" + Protocol.formatSize(msg.getAttachmentSize()) + ")");
        label.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;");

        card.getChildren().addAll(btnPlay, label);
        return card;
    }

    private void setReplyingMessage(Message msg) {
        this.replyingMessage = msg;
        barReplyPreview.setVisible(true);
        barReplyPreview.setManaged(true);
        lblReplyTargetText.setText(msg.getSenderName() + ": " + msg.getContent());
        txtComposer.requestFocus();
    }

    @FXML
    protected void onCancelReply() {
        this.replyingMessage = null;
        barReplyPreview.setVisible(false);
        barReplyPreview.setManaged(false);
    }

    private void onPromptEditMessage(Message msg) {
        TextInputDialog dialog = new TextInputDialog(msg.getContent());
        dialog.setTitle("Chỉnh sửa tin nhắn");
        dialog.setHeaderText("Cập nhật nội dung tin nhắn:");
        dialog.setContentText("Nội dung:");
        dialog.showAndWait().ifPresent(newContent -> {
            if (!newContent.trim().isEmpty() && !newContent.equals(msg.getContent())) {
                client.editMessage(msg.getId(), newContent.trim());
            }
        });
    }

    // =========================================================================
    // Composer Actions
    // =========================================================================

    @FXML
    protected void onSendMessage() {
        String content = txtComposer.getText().trim();
        if (content.isEmpty() || activeConversation == null) return;

        Conversation conversation = activeConversation;
        Message msg = new Message(conversation.getId(), client.getCurrentUser().getId(), client.getCurrentUser().getDisplayName(), content);
        msg.setStatus(Message.STATUS_SENDING);
        if (replyingMessage != null) {
            msg.setReplyToId(replyingMessage.getId());
            msg.setReplyToSender(replyingMessage.getSenderName());
            msg.setReplyToContent(replyingMessage.getContent());
            onCancelReply();
        }

        txtComposer.clear();
        appendMessageBubble(msg);
        scrollToLatestMessage();
        updateConversationPreview(conversation.getId(), content, msg.getCreatedAt());
        client.sendMessage(msg).whenComplete((sentMessage, error) -> Platform.runLater(() -> {
            if (error != null) {
                msg.setStatus(Message.STATUS_FAILED);
                VBox bubble = bubbleNodesByClientMessageId.get(msg.getClientMessageId());
                if (bubble != null) {
                    reconcileMessageBubble(msg, bubble);
                }
                if (activeConversation != null && conversation.getId().equals(activeConversation.getId())) {
                    Throwable cause = error;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    lblChatHeaderStatus.setText("Gửi tin nhắn thất bại: " + cause.getMessage());
                }
                return;
            }
            if (sentMessage != null) {
                VBox bubble = bubbleNodesByClientMessageId.get(msg.getClientMessageId());
                if (bubble != null) {
                    reconcileMessageBubble(sentMessage, bubble);
                }
            }
        }));
        client.sendTyping(conversation.getId(), false);
    }

    private void updateConversationPreview(String conversationId, String content, Instant createdAt) {
        for (Conversation conversation : allConversations) {
            if (conversation.getId().equals(conversationId)) {
                conversation.setLastMessageSnippet(content);
                conversation.setLastMessageAt(createdAt);
                break;
            }
        }
        listConversations.refresh();
    }

    @FXML
    protected void onAttachFile() {
        if (activeConversation == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn tệp tin gửi");
        File file = fc.showOpenDialog(stage);
        if (file == null) return;

        client.sendFileChunked(activeConversation.getId(), file, progress -> {
            // Optional progress indicator
        });
    }

    @FXML
    protected void onAttachImage() {
        if (activeConversation == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Chọn ảnh gửi");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Ảnh (*.png, *.jpg, *.jpeg, *.gif)", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = fc.showOpenDialog(stage);
        if (file == null) return;

        client.sendFileChunked(activeConversation.getId(), file, null);
    }

    @FXML
    protected void onToggleVoiceRecording() {
        if (activeConversation == null) return;

        if (!isRecordingVoice) {
            boolean started = audioService.startVoiceRecording();
            if (started) {
                isRecordingVoice = true;
                btnVoiceRecord.setText("⏹️");
                btnVoiceRecord.setStyle("-fx-text-fill: -color-danger;");
            }
        } else {
            isRecordingVoice = false;
            btnVoiceRecord.setText("🎙️");
            btnVoiceRecord.setStyle("");
            File wav = audioService.stopVoiceRecording();
            if (wav != null && wav.exists()) {
                client.sendFileChunked(activeConversation.getId(), wav, null);
            }
        }
    }

    @FXML
    protected void onOpenEmojiPicker() {
        ContextMenu emojiMenu = new ContextMenu();
        String[] emojis = {"😊", "😂", "❤️", "👍", "🔥", "🎉", "😍", "😭", "😮", "😎", "🙏", "💯"};
        for (String em : emojis) {
            MenuItem item = new MenuItem(em);
            item.setOnAction(e -> txtComposer.appendText(em));
            emojiMenu.getItems().add(item);
        }
        emojiMenu.show(txtComposer, javafx.geometry.Side.TOP, 0, 0);
    }

    // =========================================================================
    // Voice Call Action
    // =========================================================================

    @FXML
    protected void onStartVoiceCall() {
        if (activeConversation == null || client.getCurrentUser() == null) return;

        String otherUid = activeConversation.getMemberIds().stream()
                .filter(id -> !id.equals(client.getCurrentUser().getId()))
                .findFirst().orElse(null);

        if (otherUid == null) {
            new Alert(Alert.AlertType.INFORMATION, "Không thể gọi trong phòng chưa có người khác").show();
            return;
        }

        CallSession call = new CallSession(UUID.randomUUID().toString(),
                client.getCurrentUser().getId(), client.getCurrentUser().getDisplayName(),
                otherUid, activeConversation.getName());

        client.sendCallSignal(call, EventType.CALL_REQUEST);

        // Open Call Window
        openCallDialog(call, true);
    }

    private void openCallDialog(CallSession call, boolean isCaller) {
        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        dialog.setTitle("Cuộc gọi thoại");
        dialog.setHeaderText((isCaller ? "Đang gọi tới " : "Cuộc gọi đến từ ") + (isCaller ? call.getReceiverName() : call.getCallerName()));
        dialog.setContentText("📞 Cuộc gọi thoại TCP thời gian thực.");

        ButtonType btnEnd = new ButtonType("Kết thúc", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType btnAccept = new ButtonType("Nghe máy", ButtonBar.ButtonData.OK_DONE);

        if (isCaller) {
            dialog.getButtonTypes().setAll(btnEnd);
            // Start audio capture
            audioService.startCallAudioStreaming(chunk -> client.sendAudioData(call.getReceiverId(), chunk));
        } else {
            dialog.getButtonTypes().setAll(btnAccept, btnEnd);
        }

        dialog.showAndWait().ifPresent(res -> {
            if (res == btnAccept) {
                client.sendCallSignal(call, EventType.CALL_ACCEPT);
                audioService.startCallAudioStreaming(chunk -> client.sendAudioData(call.getCallerId(), chunk));
            } else {
                client.sendCallSignal(call, isCaller ? EventType.CALL_END : EventType.CALL_REJECT);
                audioService.stopCallAudioStreaming();
            }
        });
    }

    // =========================================================================
    // Navigation & Modals
    // =========================================================================

    @FXML
    protected void onNavChats() {
        lblMasterTitle.setText("Đoạn chat");
        loadConversations();
    }

    @FXML
    protected void onNavFriends() {
        lblMasterTitle.setText("Bạn bè & Danh bạ");
        client.getFriends().thenAccept(friends -> Platform.runLater(() -> {
            listConversations.getItems().clear();
            for (User f : friends) {
                Conversation fakeConv = new Conversation(f.getId(), Conversation.TYPE_DIRECT, f.getDisplayName());
                fakeConv.setLastMessageSnippet(f.getStatus());
                listConversations.getItems().add(fakeConv);
            }
        }));
    }

    @FXML
    protected void onNavRequests() {
        lblMasterTitle.setText("Lời mời kết bạn");
    }

    @FXML
    protected void onNavAdmin() {
        openAdminDashboard();
    }

    @FXML
    protected void onOpenProfile() {
        User u = client.getCurrentUser();
        if (u == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Trang cá nhân & Cài đặt");
        dialog.setHeaderText("Thông tin người dùng: " + u.getUsername());

        VBox form = new VBox(10);
        form.setPadding(new Insets(16));

        TextField nameField = new TextField(u.getDisplayName());
        TextField bioField = new TextField(u.getBio() != null ? u.getBio() : "");

        form.getChildren().addAll(
                new Label("Tên hiển thị:"), nameField,
                new Label("Tiểu sử (Bio):"), bioField
        );

        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                client.updateProfile(nameField.getText().trim(), bioField.getText().trim(), null)
                        .thenAccept(updated -> Platform.runLater(() -> {
                            client.setCurrentUser(updated);
                            lblMyAvatarInitial.setText(updated.getDisplayName().substring(0, 1).toUpperCase());
                        }));
            }
        });
    }

    @FXML
    protected void onOpenNewChatModal() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Tạo cuộc trò chuyện mới");
        dialog.setHeaderText("Nhập username hoặc tên nhóm muốn tạo:");
        dialog.setContentText("Tên:");

        dialog.showAndWait().ifPresent(target -> {
            if (!target.trim().isEmpty()) {
                client.searchUsers(target.trim()).thenAccept(users -> Platform.runLater(() -> {
                    if (!users.isEmpty()) {
                        User targetUser = users.get(0);
                        client.createConversation(Conversation.TYPE_DIRECT, targetUser.getDisplayName(),
                                List.of(client.getCurrentUser().getId(), targetUser.getId()))
                                .thenAccept(conv -> Platform.runLater(this::loadConversations));
                    } else {
                        // Create as group
                        client.createConversation(Conversation.TYPE_GROUP, target.trim(),
                                List.of(client.getCurrentUser().getId()))
                                .thenAccept(conv -> Platform.runLater(this::loadConversations));
                    }
                }));
            }
        });
    }

    @FXML
    protected void onToggleTheme() {
        isDarkMode = !isDarkMode;
        if (isDarkMode) {
            rootPane.getStyleClass().add("dark-mode");
            btnThemeToggle.setText("☀️");
        } else {
            rootPane.getStyleClass().remove("dark-mode");
            btnThemeToggle.setText("🌙");
        }
    }

    @FXML
    protected void onLogout() {
        audioService.close();
        client.logout();
        try {
            FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            stage.setTitle("Messenger Desktop - Đăng nhập");
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onLoadMoreMessages() {
        // Pagination logic
    }

    @FXML
    protected void onFilterAll() {
        filterAll.getStyleClass().add("filter-active");
        filterUnread.getStyleClass().remove("filter-active");
        filterGroups.getStyleClass().remove("filter-active");
        listConversations.getItems().setAll(allConversations);
    }

    @FXML
    protected void onFilterUnread() {
        filterAll.getStyleClass().remove("filter-active");
        filterUnread.getStyleClass().add("filter-active");
        filterGroups.getStyleClass().remove("filter-active");
    }

    @FXML
    protected void onFilterGroups() {
        filterAll.getStyleClass().remove("filter-active");
        filterUnread.getStyleClass().remove("filter-active");
        filterGroups.getStyleClass().add("filter-active");
        List<Conversation> grps = new ArrayList<>();
        for (Conversation c : allConversations) {
            if (Conversation.TYPE_GROUP.equals(c.getType())) grps.add(c);
        }
        listConversations.getItems().setAll(grps);
    }

    @FXML
    protected void onToggleConversationInfo() {
        if (activeConversation == null) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông tin hội thoại");
        alert.setHeaderText(activeConversation.getName());
        alert.setContentText("Loại: " + activeConversation.getType() + "\nSố thành viên: "
                + (activeConversation.getMemberIds() != null ? activeConversation.getMemberIds().size() : 1));
        alert.show();
    }

    private void openAdminDashboard() {
        client.sendRequest(org.example.ungdungchat.shared.protocol.Packet.create(EventType.ADMIN_STATS))
                .thenAccept(resp -> Platform.runLater(() -> {
                    Map<String, Object> stats = resp.getPayloadAs(new com.google.gson.reflect.TypeToken<Map<String, Object>>() {}.getType());
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Bảng Quản Trị Server");
                    alert.setHeaderText("Thống kê hệ thống Server Socket TCP:");
                    StringBuilder sb = new StringBuilder();
                    if (stats != null) {
                        sb.append("⏱️ Uptime: ").append(stats.get("uptime")).append("\n");
                        sb.append("🔗 Kết nối hoạt động: ").append(stats.get("activeConnections")).append("\n");
                        sb.append("🟢 Người online: ").append(stats.get("onlineUsers")).append("\n");
                        sb.append("👥 Tổng tài khoản: ").append(stats.get("totalUsers")).append("\n");
                        sb.append("💬 Tổng tin nhắn: ").append(stats.get("totalMessages")).append("\n");
                        sb.append("🍃 MongoDB: ").append(Boolean.TRUE.equals(stats.get("isMongoConnected")) ? "ĐÃ KẾT NỐI" : "BỘ ĐỆM BỘ NHỚ (In-Memory)").append("\n");
                    }
                    alert.setContentText(sb.toString());
                    alert.show();
                }));
    }

    // =========================================================================
    // ClientListener Callbacks
    // =========================================================================

    @Override
    public void onMessageReceived(Message message) {
        Platform.runLater(() -> {
            boolean conversationKnown = allConversations.stream()
                    .anyMatch(conversation -> conversation.getId().equals(message.getConversationId()));

            if (activeConversation != null && activeConversation.getId().equals(message.getConversationId())) {
                appendMessageBubble(message);
                scrollToLatestMessage();
                client.markMessageRead(message.getConversationId(), message.getId());
            }

            updateConversationPreview(
                    message.getConversationId(), message.getContent(), message.getCreatedAt());

            if (!conversationKnown) {
                loadConversationsForIncomingMessage(message.getConversationId());
            }
        });
    }

    @Override
    public void onMessageEdited(String messageId, String newContent) {
        Platform.runLater(() -> {
            VBox bubble = bubbleNodes.get(messageId);
            if (bubble != null) {
                for (javafx.scene.Node n : bubble.getChildren()) {
                    if (n instanceof Label lbl && (lbl.getStyleClass().contains("bubble-text-left") || lbl.getStyleClass().contains("bubble-text-right"))) {
                        lbl.setText(newContent + " (Đã chỉnh sửa)");
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onMessageRecalled(String messageId) {
        Platform.runLater(() -> {
            VBox bubble = bubbleNodes.get(messageId);
            if (bubble != null) {
                for (javafx.scene.Node n : bubble.getChildren()) {
                    if (n instanceof Label lbl && (lbl.getStyleClass().contains("bubble-text-left") || lbl.getStyleClass().contains("bubble-text-right"))) {
                        lbl.setText("Tin nhắn đã được thu hồi");
                        lbl.setStyle("-fx-font-style: italic; -fx-opacity: 0.7;");
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void onMessageReacted(String messageId, Map<String, List<String>> reactions) {
        Platform.runLater(() -> {
            VBox bubble = bubbleNodes.get(messageId);
            if (bubble != null && reactions != null) {
                // Find or create reaction container
                StringBuilder sb = new StringBuilder();
                reactions.forEach((k, v) -> {
                    if (v != null && !v.isEmpty()) sb.append(k).append(" ").append(v.size()).append("  ");
                });
                Label rLbl = new Label(sb.toString().trim());
                rLbl.getStyleClass().add("reaction-badge");
                bubble.getChildren().removeIf(n -> n.getStyleClass().contains("reaction-badge"));
                if (!sb.toString().trim().isEmpty()) {
                    bubble.getChildren().add(rLbl);
                }
            }
        });
    }

    @Override
    public void onMessageRead(String messageId, String userId) {
        Platform.runLater(() -> {
            // Update checkmark
        });
    }

    @Override
    public void onTyping(String conversationId, String userId, String userName, boolean isTyping) {
        Platform.runLater(() -> {
            if (activeConversation != null && activeConversation.getId().equals(conversationId)) {
                if (isTyping) {
                    lblTypingIndicator.setText(userName + " đang nhập...");
                    lblTypingIndicator.setVisible(true);
                    lblTypingIndicator.setManaged(true);
                } else {
                    lblTypingIndicator.setVisible(false);
                    lblTypingIndicator.setManaged(false);
                }
            }
        });
    }

    @Override
    public void onUserOnline(String userId, String username, String displayName) {
        Platform.runLater(() -> {
            dotChatHeaderOnline.setVisible(true);
        });
    }

    @Override
    public void onUserOffline(String userId) {
        Platform.runLater(() -> {
            dotChatHeaderOnline.setVisible(false);
        });
    }

    @Override
    public void onFriendRequestReceived(FriendRequest req) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Lời mời kết bạn");
            alert.setHeaderText("Bạn nhận được lời mời kết bạn");
            alert.setContentText(req.getFromUserName() + " muốn kết bạn với bạn!");
            alert.show();
        });
    }

    @Override
    public void onCallSignal(CallSession call, String signalType) {
        Platform.runLater(() -> {
            if (EventType.CALL_REQUEST.equals(signalType)) {
                openCallDialog(call, false);
            } else if (EventType.CALL_END.equals(signalType) || EventType.CALL_REJECT.equals(signalType)) {
                audioService.stopCallAudioStreaming();
            }
        });
    }

    @Override
    public void onAudioReceived(byte[] audioData) {
        audioService.playPcmChunk(audioData);
    }

    @Override
    public void onServerNotice(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Thông báo hệ thống");
            alert.setHeaderText("Thông báo từ Quản trị viên");
            alert.setContentText(message);
            alert.show();
        });
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            lblChatHeaderStatus.setText("Đã mất kết nối tới máy chủ");
        });
    }

    @Override
    public void onReconnected() {
        Platform.runLater(() -> {
            lblChatHeaderStatus.setText("Đã kết nối lại");
        });
    }
}
