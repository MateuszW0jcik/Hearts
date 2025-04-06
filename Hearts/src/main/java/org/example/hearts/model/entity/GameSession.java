package org.example.hearts.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.example.hearts.model.dto.GameStatus;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Table(name = "game_session")
public class GameSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "game_session_players",
            joinColumns = @JoinColumn(name = "game_session_id"),
            inverseJoinColumns = @JoinColumn(name = "player_id")
    )
    private Set<User> players;

    @Column(columnDefinition = "TEXT")
    private String gameState;

    @Enumerated(EnumType.STRING)
    private GameStatus status;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @PrePersist
    protected void onCreate() {
        startTime = LocalDateTime.now();
    }
}
