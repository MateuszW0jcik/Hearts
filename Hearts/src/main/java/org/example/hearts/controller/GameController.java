package org.example.hearts.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.hearts.exception.AuthException;
import org.example.hearts.model.dto.GameStateDTO;
import org.example.hearts.model.dto.PlayerMoveDTO;
import org.example.hearts.model.entity.User;
import org.example.hearts.repository.UserRepository;
import org.example.hearts.service.GameService;
import org.example.hearts.service.WebSocketService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;
    private final UserRepository userRepository;

    @PostMapping("/{roomId}/start")
    public ResponseEntity<GameStateDTO> startGame(@PathVariable Long roomId,
                                                  @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new AuthException("User not found"));
        GameStateDTO gameStateDTO = gameService.startGame(roomId, user);
        return ResponseEntity.ok(gameStateDTO);
    }

    @PostMapping("/{gameId}/play")
    public ResponseEntity<GameStateDTO> playCard(@PathVariable Long gameId,
                                                 @Valid @RequestBody PlayerMoveDTO move,
                                                 @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new AuthException("User not found"));
        return ResponseEntity.ok(gameService.playCard(gameId, move, user));
    }

    @PostMapping("/{gameId}/force-end")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GameStateDTO> forceEndGame(@PathVariable Long gameId) {
        return ResponseEntity.ok(gameService.forceEndGame(gameId, "Admin end the game"));
    }
}
