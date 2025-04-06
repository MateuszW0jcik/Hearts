package org.example.hearts.repository;

import org.example.hearts.model.dto.GameState;
import org.example.hearts.model.dto.RoomStatus;
import org.example.hearts.model.entity.GameSession;
import org.example.hearts.model.entity.Room;
import org.example.hearts.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByIsPrivate(boolean isPrivate);
    List<Room> findByPlayers(User player);
    Optional<Room> findByIdAndStatus(Long id, RoomStatus status);

    Optional<Room> findByCurrentGame(GameSession currentGame);
}
