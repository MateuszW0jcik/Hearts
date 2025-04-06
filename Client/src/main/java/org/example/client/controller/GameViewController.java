package org.example.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.example.client.model.dto.ChatMessage;
import org.example.client.model.dto.GameStateDTO;
import org.example.client.model.dto.PlayerMoveDTO;
import org.example.client.model.dto.RoomDTO;
import org.example.client.model.game.Card;
import org.example.client.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Scope("prototype")
public class GameViewController {
    @FXML
    public Label phaseLabel;
    @FXML
    public Label roundLabel;
    @FXML
    public Label trumpLabel;
    @FXML
    public HBox currentTrick;
    @FXML
    public VBox scoreBoard;
    @FXML
    public Button invitePlayerButton;
    @FXML
    public Button sendMessageButton;
    @FXML
    private Label currentPlayerLabel;
    @FXML
    private HBox playerCards;
    @FXML
    private ListView<ChatMessage> chatMessages;
    @FXML
    private TextField chatInput;
    @FXML
    private TextField inviteUsername;
    @FXML
    private Label topPlayerName;
    @FXML
    private Label leftPlayerName;
    @FXML
    private Label rightPlayerName;
    @FXML
    private Label bottomPlayerName;
    @FXML
    private HBox topPlayerCards;
    @FXML
    private VBox leftPlayerCards;
    @FXML
    private VBox rightPlayerCards;
    @FXML
    private Button muteButton;

    @FXML
    private ImageView micIcon;

    private final GameService gameService;
    @Setter
    private GameStateDTO currentGameState;
    @Setter
    private RoomDTO currentRoomDTO;
    private String[] phasesNames = {"No Tricks", "No Hearts", "No Queens", "No Kings or Jacks", "No King of Hearts", "No Seventh or Last Trick", "The \"Outlaw\"", "Trump Round", "Trump Round", "Trump Round", "Trump Round", "Lottery Round"};

    @Autowired
    public GameViewController(GameService gameService) {
        this.gameService = gameService;
    }

    @FXML
    public void initialize() {
        setupChatList();
        inviteUsername.setOnAction(event -> invitePlayerButton.fire());
        chatInput.setOnAction(event -> sendMessageButton.fire());

        Image micOffImage = new Image(getClass().getResourceAsStream("/icons/mic-off.png"));
        muteButton.getStyleClass().add("muted");
        micIcon.setImage(micOffImage);
        muteButton.setUserData(true);
    }

    private void setupChatList() {
        chatMessages.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(ChatMessage message, boolean empty) {
                super.updateItem(message, empty);
                if (empty || message == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    TextFlow textFlow = new TextFlow();

                    Text sender = new Text(message.getSender() + ": ");
                    sender.setFill(Color.WHITE);
                    sender.setFont(Font.font(null, FontWeight.BOLD, 12));

                    Text content = new Text(message.getContent());
                    content.setFill(Color.WHITE);

                    textFlow.getChildren().addAll(sender, content);
                    textFlow.setMaxWidth(list.getWidth() - 20);

                    setGraphic(textFlow);
                    setText(null);
                }
            }
        });

        chatMessages.widthProperty().addListener((obs, oldVal, newVal) -> {
            chatMessages.refresh();
        });
    }

    public void updateViewByGameState() {
        if (currentGameState == null) return;

        currentPlayerLabel.setText("On move: " + currentGameState.getCurrentPlayer());
        roundLabel.setText("Round: " + currentGameState.getCurrentRound());
        phaseLabel.setText(phasesNames[currentGameState.getCurrentPhase()-1]);

        updateScoreBoard();

        updateCards();
    }

    public void updateViewByRoomDTO() {
        if (currentRoomDTO == null) return;

        clearView();

        setPlayersNames();

        setRoomStatusInfo();

        updateScoreBoard();
    }

    private void setPlayersNames() {
        String currentUser = gameService.getCurrentUsername();
        List<String> players = currentRoomDTO.getPlayers();

        clearPlayerLabels();

        int currentPlayerIndex = players.indexOf(currentUser);
        if (currentPlayerIndex == -1) return;

        for (int i = 0; i < players.size(); i++) {
            String playerName = players.get(i);
            int relativePosition = (i - currentPlayerIndex + players.size()) % players.size();

            switch (relativePosition) {
                case 0:
                    bottomPlayerName.setText(playerName);
                    break;
                case 1:
                    leftPlayerName.setText(playerName);
                    break;
                case 2:
                    topPlayerName.setText(playerName);
                    break;
                case 3:
                    rightPlayerName.setText(playerName);
                    break;
            }
        }
    }

    private void setRoomStatusInfo() {
        currentTrick.getChildren().clear();
        if(currentRoomDTO.getPlayers().size() != 4){
            Text text = new Text("Waiting for players...");
            text.setFill(Color.WHITE);
            currentTrick.getChildren().add(text);
            return;
        }
        if(currentRoomDTO.getOwner().equals(gameService.getCurrentUsername())){
            Button startGame = new Button("Start game");
            startGame.setOnAction(event -> {
                startGame.setDisable(true);
                gameService.startGame(currentRoomDTO.getId());
            });
            currentTrick.getChildren().add(startGame);
        }else {
            Text text = new Text("Waiting for owner to start game...");
            text.setFill(Color.WHITE);
            currentTrick.getChildren().add(text);
        }
    }

    private void clearPlayerLabels() {
        topPlayerName.setText("");
        leftPlayerName.setText("");
        rightPlayerName.setText("");
        bottomPlayerName.setText("");
    }

    private void clearView(){
        currentPlayerLabel.setText("");
        roundLabel.setText("");
        phaseLabel.setText("");
        playerCards.getChildren().clear();
        leftPlayerCards.getChildren().clear();
        rightPlayerCards.getChildren().clear();
        topPlayerCards.getChildren().clear();
        currentTrick.getChildren().clear();
    }

    private void updateScoreBoard() {
        scoreBoard.getChildren().clear();

        Label headerLabel = new Label("Scoreboard:");
        headerLabel.getStyleClass().add("header-label-smaller");
        scoreBoard.getChildren().add(headerLabel);

        for (String player : currentRoomDTO.getPlayers()) {
            HBox playerRow = new HBox(10);
            playerRow.setAlignment(Pos.CENTER_LEFT);

            String playerName = player;
            if (player.equals(currentRoomDTO.getOwner())) playerName += " (Host)";
            if (player.equals(gameService.getCurrentUsername())) playerName += " (You)";

            Label playerLabel = new Label(playerName);
            playerLabel.setPrefWidth(200);

            String playerScore = "0";
            if (currentGameState != null) {
                playerScore = String.valueOf(currentGameState.getScores().getOrDefault(player, 0));
            }
            Label scoreLabel = new Label(playerScore);

            playerRow.getChildren().addAll(playerLabel, scoreLabel);

            scoreBoard.getChildren().add(playerRow);
        }

    }

    private void updateCards() {
        playerCards.getChildren().clear();
        leftPlayerCards.getChildren().clear();
        rightPlayerCards.getChildren().clear();
        topPlayerCards.getChildren().clear();
        currentTrick.getChildren().clear();

        playerCards.setSpacing(-50);
        currentGameState.getPlayerHands().get(gameService.getCurrentUsername()).forEach(card -> {
            Node cardNode = createCardNode(card, true);
            playerCards.getChildren().add(cardNode);
        });

        leftPlayerCards.setSpacing(-100);
        currentGameState.getPlayerHands().get(leftPlayerName.getText()).forEach(card -> {
            StackPane cardContainer = new StackPane();
            Node cardNode = createCardNode(null, false);
            cardNode.setRotate(90);
            cardContainer.getChildren().add(cardNode);
            cardContainer.setPrefWidth(120);
            leftPlayerCards.getChildren().add(cardContainer);
        });

        rightPlayerCards.setSpacing(-100);
        currentGameState.getPlayerHands().get(rightPlayerName.getText()).forEach(card -> {
            StackPane cardContainer = new StackPane();
            Node cardNode = createCardNode(null, false);
            cardNode.setRotate(-90);
            cardContainer.getChildren().add(cardNode);
            cardContainer.setPrefWidth(120);
            rightPlayerCards.getChildren().add(cardContainer);
        });

        topPlayerCards.setSpacing(-50);
        currentGameState.getPlayerHands().get(topPlayerName.getText()).forEach(card -> {
            Node cardNode = createCardNode(null, false);
            topPlayerCards.getChildren().add(cardNode);
        });

        currentTrick.setSpacing(10);
        currentGameState.getCurrentTrick().forEach(card -> {
            Node cardNode = createCardNode(card, true);
            currentTrick.getChildren().add(cardNode);
        });
    }

    private Node createCardNode(Card card, boolean faceUp) {
        StackPane cardPane = new StackPane();
        cardPane.setPrefSize(80, 120);

        // Create the card background
        Rectangle background = new Rectangle(80, 120);
        background.setArcWidth(10);
        background.setArcHeight(10);
        background.setFill(Color.WHITE);
        background.setStroke(Color.BLACK);

        if (!faceUp) {
            // Create card back pattern
            Rectangle pattern = new Rectangle(70, 110);
            pattern.setArcWidth(8);
            pattern.setArcHeight(8);
            pattern.setFill(Color.NAVY);
            pattern.setStroke(Color.DARKBLUE);

            cardPane.getChildren().addAll(background, pattern);
            return cardPane;
        }

        VBox content = new VBox(5);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(5));

        Color suitColor = (card.getSuit() == Card.Suit.HEARTS || card.getSuit() == Card.Suit.DIAMONDS)
                ? Color.RED : Color.BLACK;

        Text topRank = new Text(getRankSymbol(card.getRank()));
        topRank.setFill(suitColor);
        topRank.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        Text topSuit = new Text(getSuitSymbol(card.getSuit()));
        topSuit.setFill(suitColor);
        topSuit.setFont(Font.font("Arial", 16));

        VBox topSection = new VBox(2);
        topSection.setAlignment(Pos.TOP_LEFT);
        topSection.getChildren().addAll(topRank, topSuit);

        Text centerSuit = new Text(getSuitSymbol(card.getSuit()));
        centerSuit.setFill(suitColor);
        centerSuit.setFont(Font.font("Arial", FontWeight.BOLD, 32));

        Text bottomRank = new Text(getRankSymbol(card.getRank()));
        bottomRank.setFill(suitColor);
        bottomRank.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        bottomRank.setRotate(180);

        Text bottomSuit = new Text(getSuitSymbol(card.getSuit()));
        bottomSuit.setFill(suitColor);
        bottomSuit.setFont(Font.font("Arial", 16));
        bottomSuit.setRotate(180);

        VBox bottomSection = new VBox(2);
        bottomSection.setAlignment(Pos.BOTTOM_RIGHT);
        bottomSection.getChildren().addAll(bottomSuit, bottomRank);

        cardPane.getChildren().addAll(background, topSection, centerSuit, bottomSection);

        StackPane.setAlignment(topSection, Pos.TOP_LEFT);
        StackPane.setMargin(topSection, new Insets(5));
        StackPane.setAlignment(bottomSection, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(bottomSection, new Insets(5));

        if (faceUp && card != null &&
                gameService.getCurrentUsername().equals(currentGameState.getCurrentPlayer())) {
            cardPane.setOnMouseEntered(event -> {
                background.setEffect(new DropShadow(10, Color.GRAY));
                cardPane.setTranslateY(-10);
            });

            cardPane.setOnMouseExited(event -> {
                background.setEffect(null);
                cardPane.setTranslateY(0);
            });

            cardPane.setOnMouseClicked(event -> {
                handleCardClick(card);
            });
        }
        return cardPane;
    }

    private String getRankSymbol(Card.Rank rank) {
        switch (rank) {
            case ACE: return "A";
            case KING: return "K";
            case QUEEN: return "Q";
            case JACK: return "J";
            default: return String.valueOf(rank.getValue());
        }
    }

    private String getSuitSymbol(Card.Suit suit) {
        return switch (suit) {
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case CLUBS -> "♣";
            case SPADES -> "♠";
            default -> "";
        };
    }

    private boolean isPlayerTurn() {
        return currentGameState != null && currentGameState.getCurrentPlayer().equals(gameService.getCurrentUsername());
    }

    private void handleCardClick(Card card) {
        if(isPlayerTurn()) gameService.playCard(currentGameState.getGameId(), card);
    }

    @FXML
    public void handleSendMessage() {
        String message = chatInput.getText();
        if (!message.isEmpty()) {
            gameService.sendChatMessage(currentRoomDTO.getId(), message);
            chatInput.clear();
        }
    }

    @FXML
    public void handleInvitePlayer() {
        String username = inviteUsername.getText().trim();
        if (!username.isEmpty()) {
            gameService.invitePlayer(currentRoomDTO.getId(), username);
            inviteUsername.clear();
        }
    }

    @FXML
    public void handleMuteToggle(){
        boolean isMuted = !(boolean) muteButton.getUserData();
        muteButton.setUserData(isMuted);

        if (isMuted) {
            micIcon.setImage(new Image(getClass().getResourceAsStream("/icons/mic-off.png")));
            muteButton.getStyleClass().add("muted");
        } else {
            micIcon.setImage(new Image(getClass().getResourceAsStream("/icons/mic-on.png")));
            muteButton.getStyleClass().remove("muted");
        }

        gameService.setMuted(isMuted, currentRoomDTO.getId());
    }

    public void addChatMessage(ChatMessage message) {
        Platform.runLater(() -> chatMessages.getItems().add(message));
    }
}
