package org.example.hearts.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.hearts.exception.RoomException;
import org.example.hearts.model.dto.RoomDTO;
import org.example.hearts.model.entity.Room;
import org.example.hearts.model.entity.User;
import org.example.hearts.repository.RoomRepository;
import org.example.hearts.repository.UserRepository;
import org.example.hearts.service.RoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<RoomDTO>> getAllRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    @PostMapping
    public ResponseEntity<RoomDTO> createRoom(@Valid @RequestBody RoomDTO roomDto,
                                              @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RoomException("User not found"));
        return ResponseEntity.ok(roomService.createRoom(roomDto, user));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<RoomDTO> joinRoom(@PathVariable Long roomId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RoomException("User not found"));
        return ResponseEntity.ok(roomService.joinRoom(roomId, user));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<Void> leaveRoom(@PathVariable Long roomId,
                                         @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RoomException("User not found"));
        roomService.leaveRoom(roomId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/leave/all")
    public ResponseEntity<Void> leaveAllRooms(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RoomException("User not found"));
        roomService.leaveAllRooms(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{roomId}/invite")
    public ResponseEntity<Void> invitePlayer(@PathVariable Long roomId,
                                             @RequestParam String username,
                                             @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RoomException("User not found"));
        roomService.invitePlayer(roomId, username, user);
        return ResponseEntity.ok().build();
    }
}
