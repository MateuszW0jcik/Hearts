package org.example.hearts.service;

import lombok.RequiredArgsConstructor;
import org.example.hearts.exception.AuthException;
import org.example.hearts.model.dto.*;
import org.example.hearts.model.entity.User;
import org.example.hearts.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendInvitation(RoomInvitationDTO invitation) {
        messagingTemplate.convertAndSendToUser(
                invitation.getInvitedUsername(),
                "/queue/invitations",
                invitation
        );
    }

    public void notifyRoomListChange() {
        messagingTemplate.convertAndSend(
                "/topic/rooms",
                new WebSocketMessage<>("ROOM_LIST_CHANGED", true)
        );
    }

    public void notifyRoomStateChanged(RoomDTO roomDTO){
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomDTO.getId() + "/changes",
                roomDTO
        );
    }

    public void notifyGameStart(Long roomId, GameStateDTO gameState){
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/start",
                gameState
//                new WebSocketMessage<>("GAME_STATE_CHANGE", gameState)
        );
    }

    public void notifyGameStateChanged(GameStateDTO gameState){
        messagingTemplate.convertAndSend(
                "/topic/game/" + gameState.getGameId() + "/change",
                gameState
//                new WebSocketMessage<>("GAME_STATE_CHANGE", gameState)
        );
    }

    public void notifyGameEnded(Long gameId, String message){
        messagingTemplate.convertAndSend(
                "/topic/game/" + gameId + "/end",
//                message
                new WebSocketMessage<>("GAME_ENDED", message)
        );
    }
}
