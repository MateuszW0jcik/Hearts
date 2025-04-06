package org.example.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.control.PasswordField;
import org.example.client.service.GameService;
import org.example.client.utils.SceneManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import static org.example.client.utils.AlertUtils.showAlert;

@Component
public class LoginViewController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    private final GameService gameService;
    private final SceneManager sceneManager;

    @Autowired
    public LoginViewController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please fill in all fields");
            return;
        }

        if (gameService.login(username, password)) sceneManager.switchToRooms();
    }

    @FXML
    public void switchToRegister() {
        sceneManager.switchToRegister();
    }

}
