package org.example.client.utils;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import lombok.Setter;
import org.example.client.controller.*;
import org.example.client.model.dto.GameStateDTO;
import org.example.client.model.dto.RoomDTO;
import org.example.client.service.GameService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SceneManager {
    @Setter
    private Stage primaryStage;
    private final ApplicationContext context;
    private final GameService gameService;
    private final Map<String, Scene> scenes = new HashMap<>();
    private final List<Stage> openedStages = new ArrayList<>();

    public SceneManager(ApplicationContext context, GameService gameService) {
        this.context = context;
        this.gameService = gameService;
    }

    public void switchToLogin() {
        switchScene("login");
    }

    public void switchToRegister() {
        switchScene("register");
    }

    public void switchToRooms() {
        switchScene("rooms");
    }

    public void switchToGame(RoomDTO initialRoomDTO) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game-view.fxml"));
            loader.setControllerFactory(context::getBean);
            Parent root = loader.load();

            GameViewController gameViewController = loader.getController();
            gameViewController.setCurrentRoomDTO(initialRoomDTO);
            gameViewController.updateViewByRoomDTO();

            Scene gameScene = new Scene(root);
            Stage gameStage = new Stage();
            gameStage.setTitle("Game in room: " + initialRoomDTO.getName());
            gameStage.setResizable(false);
            gameStage.setScene(gameScene);
            openedStages.add(gameStage);
            root.requestFocus();

            gameStage.setOnCloseRequest(event -> {
                RoomsViewController roomsViewController = context.getBean(RoomsViewController.class);
                roomsViewController.handleLeaveRoom(initialRoomDTO.getId());
            });

            gameService.subscribeToRoom(initialRoomDTO.getId(), gameViewController);
            gameService.subscribeToChat(initialRoomDTO.getId(), gameViewController);
            gameService.subscribeToVoice(initialRoomDTO.getId(), gameViewController);
            gameService.addRoomVoice(initialRoomDTO.getId());
            gameService.startVoiceChat();

            gameStage.show();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void switchScene(String name) {
        Scene scene = scenes.get(name);
        if (scene == null) {
            throw new RuntimeException("Scene " + name + " not found");
        }
        primaryStage.setScene(scene);
        Object controller = scene.getUserData();
        if (controller instanceof RoomsViewController) {
            ((RoomsViewController) controller).refreshRoomList();
        }
    }

    public void loadScenes() {
        try {
            loadScene("login", "/fxml/login-view.fxml");
            loadScene("register", "/fxml/register-view.fxml");
            loadScene("rooms", "/fxml/rooms-view.fxml");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load scenes", e);
        }
    }

    private void loadScene(String name, String fxmlPath) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        loader.setControllerFactory(context::getBean);
        Parent root = loader.load();
        Scene scene = new Scene(root);
        root.requestFocus();
        scene.setUserData(loader.getController());
        scenes.put(name, scene);
    }

    public void closeAllStages() {
        for (Stage stage : openedStages) {
            if (stage.isShowing()) {
                stage.close();
            }
        }
        openedStages.clear();
    }
}
