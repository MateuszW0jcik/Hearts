package org.example.client.utils;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.example.client.model.dto.RoomDTO;
import org.example.client.model.dto.RoomInvitationDTO;
import org.example.client.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AlertUtils {
    private static GameService gameService;
    private static SceneManager sceneManager;

    @Autowired
    public AlertUtils(GameService gameService, SceneManager sceneManager) {
        AlertUtils.gameService = gameService;
        AlertUtils.sceneManager = sceneManager;
    }

    public static void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void showInvitation(RoomInvitationDTO roomInvitation){
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Invitation");
        alert.setHeaderText(null);
        alert.setContentText("You got an invitation from " + roomInvitation.getSenderUsername() + " to join the room: " + roomInvitation.getRoomName());


        ButtonType joinButton = new ButtonType("Join");
        ButtonType cancelButton = new ButtonType("Cancel");
        alert.getButtonTypes().setAll(joinButton, cancelButton);

        alert.showAndWait().ifPresent(response -> {
            if (response == joinButton) {
                Platform.runLater(() -> {
                    RoomDTO room = gameService.joinRoom(roomInvitation.getRoomId());
                    if(room != null){
                        sceneManager.switchToGame(room);
                    }
                } );
            }
        });
    }
}
