package org.example.client.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.example.client.controller.GameViewController;
import org.example.client.controller.RoomsViewController;
import org.example.client.model.dto.*;
import org.example.client.model.entity.User;
import org.example.client.model.game.Card;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import javax.sound.sampled.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.example.client.utils.AlertUtils.showAlert;
import static org.example.client.utils.AlertUtils.showInvitation;

@Service
@Slf4j
public class GameService {
    @Autowired
    private ApplicationContext context;
    private final RestTemplate restTemplate;
    private final WebSocketStompClient stompClient;
    @Value("${app.base-url}")
    private String baseUrl;
    @Value("${app.ws-url}")
    private String wsUrl;
    private String authToken;
    private static String currentUsername;
    private StompSession stompSession;
    private Map<Long, ArrayList<StompSession.Subscription>> roomRelatedSubscriptions = new HashMap<>();
    Thread voiceThread = null;
    private static final int SAMPLE_RATE = 44100;
    private static final int SAMPLE_SIZE_IN_BITS = 16;
    private static final int CHANNELS = 1;
    private TargetDataLine microphone;
    private SourceDataLine speakers;
    private final Map<Long, Boolean> activeRoomIdsAndIsMuted =  Collections.synchronizedMap(new HashMap<>());

    public GameService(RestTemplate restTemplate, WebSocketStompClient stompClient) {
        this.restTemplate = restTemplate;
        this.stompClient = stompClient;
    }

    public boolean register(String username, String password, String email) {
        RegisterRequest request = new RegisterRequest(username, password, email);
        try {
            restTemplate.postForObject(baseUrl + "/api/auth/register", request, User.class);
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
            }catch (Exception ex){
                showAlert("Error", "Registration failed");
            }
            return false;
        } catch (RestClientException e){
            showAlert("Error", "Registration failed");
            return false;
        }
        showAlert("Success", "Registration succeeded");
        return true;
    }

    public boolean login(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/api/auth/login",
                    request,
                    String.class
            );
            this.authToken = response.getBody();
            connectWebSocket();
            currentUsername = username;
            subscribeToPersonal();
            initializeAudio();
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
            }catch (Exception ex){
                showAlert("Error", "Login failed");
            }
            return false;
        } catch (RestClientException e) {
            showAlert("Error", "Login failed");
            return false;
        }

        return true;
    }

    private void connectWebSocket() {
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("Authorization", "Bearer " + authToken);

        try {
            stompSession = stompClient.connectAsync(
                    wsUrl + "/ws",
                    handshakeHeaders,
                    new StompSessionHandlerAdapter() {
                        @Override
                        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                            System.out.println("Connected to WebSocket");
                            stompSession = session;
                        }

                        @Override
                        public void handleException(StompSession session, StompCommand command,
                                                    StompHeaders headers, byte[] payload, Throwable exception) {
                            System.out.println("Error in STOMP session");
                            System.out.println(headers);
                            System.out.println(exception);
                        }
                    }
            ).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            System.out.println("WebSocket connection failed: ");
        }
    }

    public List<RoomDTO> getRooms(){
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<List<RoomDTO>> response = restTemplate.exchange(
                    baseUrl + "/api/rooms",
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<List<RoomDTO>>() {}
            );
            return response.getBody();
        } catch (RestClientException e) {
            showAlert("Error", "Failed to get rooms");
            return null;
        }
    }

    public RoomDTO createRoom(String roomName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);

        RoomDTO request = new RoomDTO();
        request.setName(roomName);

        HttpEntity<RoomDTO> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<RoomDTO> response = restTemplate.exchange(
                    baseUrl + "/api/rooms",
                    HttpMethod.POST,
                    entity,
                    RoomDTO.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
                return null;
            }catch (Exception ex){
                showAlert("Error", "Failed to create room");
                return null;
            }
        } catch (RestClientException e) {
            showAlert("Error", "Failed to create room");
            return null;
        }
    }

    public RoomDTO joinRoom(Long roomId){
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<RoomDTO> response = restTemplate.exchange(
                    baseUrl + "/api/rooms/" + roomId + "/join",
                    HttpMethod.POST,
                    entity,
                    RoomDTO.class
            );
            return response.getBody();
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
                return null;
            }catch (Exception ex){
                showAlert("Error", "Failed to join room");
                return null;
            }
        } catch (RestClientException e) {
            showAlert("Error", "Failed to join room");
            return null;
        }
    }

    public void leaveRoom(Long roomId){
        unsubscribeRoom(roomId);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    baseUrl + "/api/rooms/" + roomId + "/leave",
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
            }catch (Exception ex){
                showAlert("Error", "Failed to leave room");
            }
        } catch (RestClientException e) {
            showAlert("Error", "Failed to leave room");
        }
    }

    public void unsubscribeRoom(long roomId){
        if(roomRelatedSubscriptions.containsKey(roomId)) {
            for (StompSession.Subscription subscription : roomRelatedSubscriptions.get(roomId)) {
                subscription.unsubscribe();
            }
            roomRelatedSubscriptions.get(roomId).clear();
            roomRelatedSubscriptions.remove(roomId);
        }
        synchronized (activeRoomIdsAndIsMuted) {
            activeRoomIdsAndIsMuted.remove(roomId);
            if (activeRoomIdsAndIsMuted.isEmpty()) {
                voiceThread.interrupt();
                voiceThread = null;
            }
        }
    }

    public void leaveAllRooms() {
        if(authToken == null) return;
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    baseUrl + "/api/rooms/leave/all",
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
            }catch (Exception ex){
                showAlert("Error", "Failed to leave room");
            }
        } catch (RestClientException e) {
            showAlert("Error", "Failed to leave room");
        }
    }

    public void invitePlayer(Long roomId, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(
                    baseUrl + "/api/rooms/" + roomId + "/invite?username=" + username,
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
        } catch (HttpClientErrorException e) {
            try {
                ErrorResponse error = new ObjectMapper().readValue(e.getResponseBodyAsString(), ErrorResponse.class);
                showAlert("Error", error.getErrorMessage());
            }catch (Exception ex){
                showAlert("Error", "Failed to invite player");
            }
        } catch (RestClientException e) {
            showAlert("Error", "Failed to invite player");
        }
    }

    public GameStateDTO startGame(Long roomId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);
        HttpEntity<?> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<GameStateDTO> response = restTemplate.exchange(
                    baseUrl + "/api/games/" + roomId + "/start",
                    HttpMethod.POST,
                    entity,
                    GameStateDTO.class
            );
            return response.getBody();
        } catch (RestClientException e) {
            System.out.println("Failed to start game: ");
            return null;
        }
    }

    public void subscribeToRoom(Long roomId, GameViewController gameViewController) {
        StompSession.Subscription subscription;

        roomRelatedSubscriptions.computeIfAbsent(roomId, k -> new ArrayList<>());

        subscription = stompSession.subscribe("/topic/room/" + roomId + "/start",
                new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameStateDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                GameStateDTO gameState = (GameStateDTO) payload;
                    Platform.runLater(() -> {
                        gameViewController.setCurrentGameState(gameState);
                        gameViewController.updateViewByGameState();
                        subscribeToGame(gameState.getGameId(), gameViewController, roomId);
                    });
            }
        });

        roomRelatedSubscriptions.get(roomId).add(subscription);

        subscription = stompSession.subscribe("/topic/room/" + roomId + "/changes", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RoomDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                RoomDTO roomDTO = (RoomDTO) payload;
                Platform.runLater(() -> {
                    gameViewController.setCurrentRoomDTO(roomDTO);
                    gameViewController.updateViewByRoomDTO();
                });
            }
        });

        roomRelatedSubscriptions.get(roomId).add(subscription);
    }

    public void subscribeToGame(Long gameID, GameViewController gameViewController, Long roomId){
        StompSession.Subscription subscription;

        roomRelatedSubscriptions.computeIfAbsent(roomId, k -> new ArrayList<>());

        subscription = stompSession.subscribe("/topic/game/" + gameID + "/change", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return GameStateDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                GameStateDTO gameState = (GameStateDTO) payload;
                Platform.runLater(() -> {
                    gameViewController.setCurrentGameState(gameState);
                    gameViewController.updateViewByGameState();
                });
            }
        });

        roomRelatedSubscriptions.get(roomId).add(subscription);

        subscription = stompSession.subscribe("/topic/game/" + gameID + "/end", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WebSocketMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                WebSocketMessage<String> message = (WebSocketMessage<String>) payload;
                Platform.runLater(() -> {
                    showAlert("Game ended", message.getPayload());
                    gameViewController.setCurrentGameState(null);
                    gameViewController.updateViewByRoomDTO();
                });
            }
        });

        roomRelatedSubscriptions.get(roomId).add(subscription);
    }

    public void subscribeToChat(Long roomId, GameViewController gameViewController) {
        StompSession.Subscription subscription;

        roomRelatedSubscriptions.computeIfAbsent(roomId, k -> new ArrayList<>());

        subscription = stompSession.subscribe("/topic/room/" + roomId + "/chat", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return ChatMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                ChatMessage chatMessage = (ChatMessage) payload;
                Platform.runLater(() -> gameViewController.addChatMessage(chatMessage));
            }
        });

        roomRelatedSubscriptions.get(roomId).add(subscription);
    }

    public void subscribeToVoice(Long roomId, GameViewController gameViewController) {
        StompSession.Subscription subscription;

        roomRelatedSubscriptions.computeIfAbsent(roomId, k -> new ArrayList<>());

        subscription = stompSession.subscribe("/topic/room/" + roomId + "/voice", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return VoiceMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                VoiceMessage voiceMessage = (VoiceMessage) payload;
                if (!currentUsername.equals(voiceMessage.getUsername())) {
                    Platform.runLater(() -> playAudio(voiceMessage.getAudioData()));
                }
            }
        });

        roomRelatedSubscriptions.get(roomId).add(subscription);
    }

    public void subscribeToPersonal() {
        stompSession.subscribe("/user/" + currentUsername + "/queue/invitations", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RoomInvitationDTO.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                RoomInvitationDTO roomInvitation = (RoomInvitationDTO) payload;
                Platform.runLater(() -> showInvitation(roomInvitation));
            }
        });

        stompSession.subscribe("/topic/rooms", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return WebSocketMessage.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                WebSocketMessage<Boolean> webSocketMessage = (WebSocketMessage<Boolean>) payload;
                if(webSocketMessage.getType().equals("ROOM_LIST_CHANGED") && webSocketMessage.getPayload()) {
                    RoomsViewController roomsViewController = context.getBean(RoomsViewController.class);
                    Platform.runLater(roomsViewController::refreshRoomList);
                }
            }
        });
    }

    public void playCard(Long gameId, Card card) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authToken);

        PlayerMoveDTO request = new PlayerMoveDTO(gameId, currentUsername ,card);

        HttpEntity<PlayerMoveDTO> entity = new HttpEntity<>(request, headers);

        try {
            restTemplate.exchange(
                    baseUrl + "/api/games/" + gameId + "/play",
                    HttpMethod.POST,
                    entity,
                    Void.class
            );
        } catch (RestClientException e) {
            System.out.println("Failed to play card: ");
        }
    }

    public void sendChatMessage(Long roomId, String content) {
        ChatMessage chatMessage = new ChatMessage(currentUsername, content, ChatMessage.MessageType.CHAT);

        stompSession.send(
                "/app/room/" + roomId + "/chat",
                chatMessage
        );
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    private void playAudio(byte[] audioData) {
        speakers.write(audioData, 0, audioData.length);
    }

    private void initializeAudio() {
        try {
            AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS,
                    CHANNELS, true, true);

            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);

            DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
            speakers = (SourceDataLine) AudioSystem.getLine(speakerInfo);
            speakers.open(format);
            speakers.start();
        }catch (LineUnavailableException e){
            System.out.println("Couldn't load audio");
        }
    }

    public void startVoiceChat() {
        if(voiceThread == null){
            try {
                if (microphone == null) {
                    initializeAudio();
                }
                microphone.start();

                voiceThread = new Thread(() -> {
                    byte[] buffer = new byte[4096];
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            int count = microphone.read(buffer, 0, buffer.length);
                            if (count > 0 && !activeRoomIdsAndIsMuted.isEmpty() && activeRoomIdsAndIsMuted.values().stream().anyMatch(value -> !value)) {
                                byte[] audioData = Arrays.copyOf(buffer, count);
                                System.out.println(activeRoomIdsAndIsMuted);
                                synchronized (activeRoomIdsAndIsMuted) {
                                    for (Long roomId : activeRoomIdsAndIsMuted.keySet()) {
                                        if(!activeRoomIdsAndIsMuted.get(roomId)) {
                                            VoiceMessage voiceMessage = new VoiceMessage(
                                                    audioData,
                                                    currentUsername,
                                                    System.currentTimeMillis(),
                                                    roomId
                                            );
                                            try {
                                                stompSession.send("/app/room/" + roomId + "/voice", voiceMessage);
                                            } catch (Exception e) {
                                                log.error("Failed to send voice message to room " + roomId, e);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error("Error in recording thread", e);
                            if (microphone.isActive()) {
                                microphone.stop();
                            }
                            try {
                                Thread.sleep(5000);
                                voiceThread = null;
                                startVoiceChat();
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                });
                voiceThread.setDaemon(true);
                voiceThread.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void addRoomVoice(long roomId){
        synchronized (activeRoomIdsAndIsMuted){
            activeRoomIdsAndIsMuted.put(roomId, true);
        }
    }

    public void setMuted(boolean isMuted, long roomId) {
        synchronized (activeRoomIdsAndIsMuted){
            activeRoomIdsAndIsMuted.replace(roomId, isMuted);
        }
    }
}
