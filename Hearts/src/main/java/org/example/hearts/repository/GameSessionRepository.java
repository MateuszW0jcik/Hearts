package org.example.hearts.repository;

import org.example.hearts.model.dto.GameStatus;
import org.example.hearts.model.entity.GameSession;
import org.example.hearts.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findByRoomId(Long roomId);
}
