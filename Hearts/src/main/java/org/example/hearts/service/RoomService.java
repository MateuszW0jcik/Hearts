package org.example.hearts.service;

import lombok.RequiredArgsConstructor;
import org.example.hearts.exception.RoomException;
import org.example.hearts.model.dto.RoomDTO;
import org.example.hearts.model.dto.RoomInvitationDTO;
import org.example.hearts.model.dto.RoomStatus;
import org.example.hearts.model.entity.Room;
import org.example.hearts.model.entity.User;
import org.example.hearts.repository.GameSessionRepository;
import org.example.hearts.repository.RoomRepository;
import org.example.hearts.repository.UserRepository;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final WebSocketService webSocketService;
    private final GameService gameService;

    /**
     * @return All rooms list
     */
    public List<RoomDTO> getAllRooms() {
        return roomRepository.findByIsPrivate(false)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * @param roomDto Request roomDto
     * @param owner Owner of room
     * @return Created RoomDTO
     */
    @Transactional
    public RoomDTO createRoom(RoomDTO roomDto, User owner) {
        List<Room> userRooms = roomRepository.findByPlayers(owner);
        if(userRooms!=null && userRooms.size() >= gameService.getMaxRoomsCreated()){
            throw new RoomException("You have reached the maximum number of rooms");
        }

        Room room = new Room();
        room.setName(roomDto.getName());
        room.setOwner(owner);
        room.setPrivate(roomDto.isPrivate());
        room.setStatus(RoomStatus.WAITING);
        room.setPlayers(new HashSet<>(Collections.singletonList(owner)));

        Room savedRoom = roomRepository.save(room);
        webSocketService.notifyRoomListChange();
        return convertToDTO(savedRoom);
    }

    /**
     * @param roomId Room id to join
     * @param user Who join a room
     * @return Joined RoomDTO
     */
    @Transactional
    public RoomDTO joinRoom(Long roomId, User user) {
        Room room = roomRepository.findByIdAndStatus(roomId, RoomStatus.WAITING)
                .orElseThrow(() -> new RoomException("Room not found or not available"));

        if (room.getPlayers().size() >= gameService.getMaxPlayersCount()) {
            throw new RoomException("Room is full");
        }

        if (room.getPlayers().stream().anyMatch(player -> player.getId().equals(user.getId()))) {
            throw new RoomException("You are already in this room");
        }

        room.getPlayers().add(user);

        Room savedRoom = roomRepository.save(room);
        webSocketService.notifyRoomListChange();

        RoomDTO roomDTO = convertToDTO(savedRoom);
        webSocketService.notifyRoomStateChanged(roomDTO);
        return roomDTO;
    }

    /**
     * @param roomId Room id to invite
     * @param username To invite username
     * @param inviter Inviter username
     */
    @Transactional
    public void invitePlayer(Long roomId, String username, User inviter) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("Room not found"));

        if (!room.getOwner().equals(inviter)) {
            throw new RoomException("Only room owner can invite players");
        }

        User invitedUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RoomException("User not found"));

        if (room.getPlayers().contains(invitedUser)) {
            throw new RoomException("User is already in the room");
        }

        if (room.getPlayers().size() >= gameService.getMaxPlayersCount()) {
            throw new RoomException("Room is full");
        }

        if (room.getStatus() != RoomStatus.WAITING) {
            throw new RoomException("Cannot invite players to a room that is not in WAITING status");
        }

        RoomInvitationDTO invitation = new RoomInvitationDTO();
        invitation.setRoomId(room.getId());
        invitation.setRoomName(room.getName());
        invitation.setInvitedUsername(invitedUser.getUsername());
        invitation.setSenderUsername(inviter.getUsername());

        webSocketService.sendInvitation(invitation);
    }

    /**
     * @param roomId Room id to leave
     * @param user User who want to leave
     */
    @Transactional
    public void leaveRoom(Long roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("Room not found or not available"));

        if (!room.getPlayers().contains(user)) {
            throw new RoomException("User is not in this room");
        }

        if (room.getCurrentGame() != null) {
            gameService.forceEndGame(room.getCurrentGame().getId(), user.getUsername() + " has left the room");
            room.setStatus(RoomStatus.WAITING);
            room.setCurrentGame(null);
        }

        if (user.equals(room.getOwner())) {
            if(!handleOwnerLeaving(room, user)){
                webSocketService.notifyRoomListChange();
                return;
            }
        }

        room.getPlayers().remove(user);

        Room savedRoom = roomRepository.save(room);
        webSocketService.notifyRoomListChange();

        RoomDTO roomDTO = convertToDTO(savedRoom);
        webSocketService.notifyRoomStateChanged(roomDTO);
    }

    /**
     * @param user User who want to leave all rooms
     */
    @Transactional
    public void leaveAllRooms(User user) {
        List<Room> rooms = roomRepository.findByPlayers(user);
        for(Room room : rooms){
            if (user.equals(room.getOwner())) {
                if(!handleOwnerLeaving(room, user)) continue;
            }

            room.getPlayers().remove(user);
            Room savedRoom = roomRepository.save(room);

            RoomDTO roomDTO = convertToDTO(savedRoom);
            webSocketService.notifyRoomStateChanged(roomDTO);
        }
        webSocketService.notifyRoomListChange();
    }

    /**
     * @param room Room to leave
     * @param user User who left the room
     * @return Is room still existing
     */
    private boolean handleOwnerLeaving(Room room, User user){
        if (room.getPlayers().size() > 1) {
            User newOwner = room.getPlayers().stream()
                    .filter(player -> !player.equals(user))
                    .findFirst()
                    .orElseThrow(() -> new RoomException("No other players to transfer ownership to"));

            room.setOwner(newOwner);
            return true;
        } else {
            roomRepository.delete(room);
            return false;
        }
    }

    /**
     * @param room Room to convert
     * @return Converted RoomDTO
     */
    private RoomDTO convertToDTO(Room room) {
        RoomDTO dto = new RoomDTO();
        dto.setId(room.getId());
        dto.setName(room.getName());
        dto.setOwner(room.getOwner().getUsername());
        dto.setPlayers(room.getPlayers().stream()
                .map(User::getUsername)
                .collect(Collectors.toList()));
        dto.setStatus(room.getStatus());
        dto.setPrivate(room.isPrivate());
        return dto;
    }

}
