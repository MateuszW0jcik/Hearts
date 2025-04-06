package org.example.hearts.controller;

import lombok.RequiredArgsConstructor;
import org.example.hearts.exception.AuthException;
import org.example.hearts.exception.RoomException;
import org.example.hearts.model.dto.*;
import org.example.hearts.model.entity.Room;
import org.example.hearts.model.entity.User;
import org.example.hearts.repository.RoomRepository;
import org.example.hearts.repository.UserRepository;
import org.example.hearts.service.RoomService;
import org.example.hearts.service.WebSocketService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;


import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebSocketController {
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;


    @MessageMapping("/room/{roomId}/chat")
    @SendTo("/topic/room/{roomId}/chat")
    public ChatMessage handleChat(@DestinationVariable Long roomId,
                                  Principal principal,
                                  ChatMessage message) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new AuthException("User not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("Room not found"));
        if (room.getPlayers().stream().noneMatch(player -> player.getId().equals(user.getId()))) {
            throw new RoomException("You are not in this room");
        }
        return message;
    }

    @MessageMapping("/room/{roomId}/voice")
    @SendTo("/topic/room/{roomId}/voice")
    public VoiceMessage handleVoice(@DestinationVariable Long roomId,
                                    Principal principal,
                                    VoiceMessage voiceMessage) {
        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new AuthException("User not found"));
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("Room not found"));

        if (room.getPlayers().stream().noneMatch(player -> player.getId().equals(user.getId()))) {
            throw new RoomException("You are not in this room");
        }

        voiceMessage.setUsername(user.getUsername());
        voiceMessage.setTimestamp(System.currentTimeMillis());
        voiceMessage.setRoomId(roomId);

        return voiceMessage;
    }
}
