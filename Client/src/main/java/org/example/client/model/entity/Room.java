package org.example.client.model.entity;

import lombok.Data;
import org.example.client.model.dto.RoomStatus;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class Room {
    private Long id;
    private String name;
    private User owner;
    private Set<User> players;
    private GameSession currentGame;
    private boolean isPrivate;
    private String password;
    private RoomStatus status;
    private LocalDateTime createdAt;
}
