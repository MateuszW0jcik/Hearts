package org.example.hearts;

import org.example.hearts.controller.RoomController;
import org.example.hearts.model.dto.RoomDTO;
import org.example.hearts.model.dto.RoomStatus;
import org.example.hearts.repository.UserRepository;
import org.example.hearts.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.example.hearts.model.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomControllerTests {

    @Mock
    private RoomService roomService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RoomController roomController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllRooms() {
        RoomDTO room1 = new RoomDTO(1L, "Room 1", "owner1", List.of("user1", "user2"), RoomStatus.WAITING, false);
        RoomDTO room2 = new RoomDTO(2L, "Room 2", "owner2", List.of("user3", "user4"), RoomStatus.WAITING, true);
        when(roomService.getAllRooms()).thenReturn(Arrays.asList(room1, room2));

        ResponseEntity<List<RoomDTO>> response = roomController.getAllRooms();

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(2, response.getBody().size());
        verify(roomService, times(1)).getAllRooms();
    }

    @Test
    void testCreateRoom() {
        RoomDTO roomDto = new RoomDTO();
        roomDto.setName("New Room");
        User user = new User();
        user.setUsername("testUser");
        UserDetails userDetails = mock(UserDetails.class);

        when(userDetails.getUsername()).thenReturn("testUser");
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));
        when(roomService.createRoom(roomDto, user)).thenReturn(roomDto);

        ResponseEntity<RoomDTO> response = roomController.createRoom(roomDto, userDetails);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("New Room", response.getBody().getName());
        verify(userRepository, times(1)).findByUsername("testUser");
        verify(roomService, times(1)).createRoom(roomDto, user);
    }

    @Test
    void testJoinRoom() {
        Long roomId = 1L;
        User user = new User();
        user.setUsername("testUser");
        UserDetails userDetails = mock(UserDetails.class);
        RoomDTO roomDto = new RoomDTO();
        roomDto.setId(roomId);

        when(userDetails.getUsername()).thenReturn("testUser");
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));
        when(roomService.joinRoom(roomId, user)).thenReturn(roomDto);

        ResponseEntity<RoomDTO> response = roomController.joinRoom(roomId, userDetails);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        assertEquals(roomId, response.getBody().getId());
        verify(userRepository, times(1)).findByUsername("testUser");
        verify(roomService, times(1)).joinRoom(roomId, user);
    }

    @Test
    void testLeaveRoom() {
        Long roomId = 1L;
        User user = new User();
        user.setUsername("testUser");
        UserDetails userDetails = mock(UserDetails.class);

        when(userDetails.getUsername()).thenReturn("testUser");
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));

        ResponseEntity<Void> response = roomController.leaveRoom(roomId, userDetails);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(userRepository, times(1)).findByUsername("testUser");
        verify(roomService, times(1)).leaveRoom(roomId, user);
    }

    @Test
    void testLeaveAllRooms() {
        User user = new User();
        user.setUsername("testUser");
        UserDetails userDetails = mock(UserDetails.class);

        when(userDetails.getUsername()).thenReturn("testUser");
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));

        ResponseEntity<Void> response = roomController.leaveAllRooms(userDetails);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(userRepository, times(1)).findByUsername("testUser");
        verify(roomService, times(1)).leaveAllRooms(user);
    }

    @Test
    void testInvitePlayer() {
        Long roomId = 1L;
        String username = "player2";
        User user = new User();
        user.setUsername("testUser");
        UserDetails userDetails = mock(UserDetails.class);

        when(userDetails.getUsername()).thenReturn("testUser");
        when(userRepository.findByUsername("testUser")).thenReturn(Optional.of(user));

        ResponseEntity<Void> response = roomController.invitePlayer(roomId, username, userDetails);

        assertNotNull(response);
        assertEquals(200, response.getStatusCodeValue());
        verify(userRepository, times(1)).findByUsername("testUser");
        verify(roomService, times(1)).invitePlayer(roomId, username, user);
    }
}