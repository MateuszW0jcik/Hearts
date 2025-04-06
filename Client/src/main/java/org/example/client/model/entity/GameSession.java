package org.example.client.model.entity;

import lombok.Data;
import org.example.client.model.dto.GameStatus;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class GameSession {
    private Long id;
    private Room room;
    private Set<User> players;
    private String gameState;
    private GameStatus status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
