package org.example.client.controller;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.client.model.dto.RoomDTO;
import org.example.client.service.GameService;
import org.example.client.utils.SceneManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.List;

import static org.example.client.utils.AlertUtils.showAlert;

@Component
public class RoomsViewController {
    @FXML
    private TextField roomNameField;
    @FXML
    private TableView<RoomDTO> roomsTable;
    @FXML
    private TableColumn<RoomDTO, String> roomNameColumn;
    @FXML
    private TableColumn<RoomDTO, String> playersColumn;
    @FXML
    private TableColumn<RoomDTO, Void> actionsColumn;

    private final GameService gameService;
    private final SceneManager sceneManager;

    @Autowired
    public RoomsViewController(GameService gameService, SceneManager sceneManager) {
        this.gameService = gameService;
        this.sceneManager = sceneManager;
    }

    @FXML
    public void initialize() {
        setupTableColumns();
    }

    private void setupTableColumns() {
        roomNameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        playersColumn.setCellValueFactory(cellData -> {
            RoomDTO room = cellData.getValue();
            String players = String.join("\n", room.getPlayers());
            return new SimpleStringProperty(players);
        });

        actionsColumn.setCellFactory(column -> new TableCell<>() {
            private final Button joinButton = new Button("Join");

            {
                joinButton.setOnAction(event -> {
                    RoomDTO room = getTableView().getItems().get(getIndex());
                    handleJoinRoom(room.getId());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(joinButton);
                }
            }
        });
    }

    @FXML
    public void handleCreateRoom() {
        String roomName = roomNameField.getText();
        if (roomName.isEmpty()) {
            showAlert("Error", "Please enter room name");
            return;
        }

        RoomDTO room = gameService.createRoom(roomName);
        if(room == null) return;

        roomNameField.clear();
        sceneManager.switchToGame(room);
    }

    private void handleJoinRoom(Long roomId) {
        RoomDTO room = gameService.joinRoom(roomId);
        if (room != null) {
            sceneManager.switchToGame(room);
        }
    }

    public void handleLeaveRoom(Long roomId) {
        gameService.leaveRoom(roomId);
    }

    public void handleLeaveAllRooms() {
        gameService.leaveAllRooms();
    }

    public void refreshRoomList() {
        List<RoomDTO> rooms = gameService.getRooms();
        roomsTable.getItems().setAll(rooms);
    }
}
