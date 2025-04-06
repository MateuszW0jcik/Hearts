package org.example.hearts.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.example.hearts.config.GameConfiguration;
import org.example.hearts.exception.GameException;
import org.example.hearts.exception.RoomException;
import org.example.hearts.model.dto.*;
import org.example.hearts.model.entity.GameSession;
import org.example.hearts.model.entity.Room;
import org.example.hearts.model.entity.User;
import org.example.hearts.model.game.Card;
import org.example.hearts.model.game.Deck;
import org.example.hearts.repository.GameSessionRepository;
import org.example.hearts.repository.RoomRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameService {
    private final GameSessionRepository gameSessionRepository;
    private final RoomRepository roomRepository;
    private final WebSocketService webSocketService;
    private final GameConfiguration gameConfiguration;

    /**
     * @param roomId Room id to start a game
     * @param user Who start a game
     * @return GameStateDTO
     */
    public GameStateDTO startGame(Long roomId, User user) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomException("Room not found"));

        if (!room.getOwner().equals(user)) {
            throw new RoomException("Only room owner can start the game");
        }

        if (room.getPlayers().size() != getMaxPlayersCount()) {
            throw new RoomException("Need exactly " + getMaxPlayersCount() + " players to start");
        }

        GameSession session = new GameSession();
        session.setRoom(room);
        session.setPlayers(new HashSet<>(room.getPlayers()));
        session.setStatus(GameStatus.DEALING);

        room.setStatus(RoomStatus.PLAYING);
        room.setCurrentGame(session);

        Deck deck = new Deck();
        deck.shuffle();

        GameState state = GameState.builder()
                .currentRound(1)
                .currentPhase(1)
                .playerOrder(new ArrayList<>(session.getPlayers().stream()
                        .map(User::getUsername)
                        .collect(Collectors.toList())))
                .playerHands(dealCards(deck, new ArrayList<>(session.getPlayers().stream()
                        .map(User::getUsername)
                        .collect(Collectors.toList()))))
                .scores(initializeScores(session.getPlayers()))
                .currentTrick(new ArrayList<>())
                .dealer(pickRandomDealer(session.getPlayers()))
                .heartsBlocked(true)
                .build();

        state.setCurrentPlayer(getNextPlayer(state.getDealer(), state.getPlayerOrder()));
        try {
            session.setGameState(new ObjectMapper().writeValueAsString(state));
        }catch (Exception e){
            throw new GameException(e.toString());
        }

        gameSessionRepository.save(session);
        roomRepository.save(room);

        Optional<GameSession> gameSession = gameSessionRepository.findByRoomId(roomId);
        try {
            state.setGameId(gameSession.get().getId());
        }catch (Exception e){
            throw new GameException(e.toString());
        }
        gameSession.get().setGameState(serializeGameState(state));
        gameSessionRepository.save(session);

        GameStateDTO gameStateDTO = convertToDTO(state);

        webSocketService.notifyGameStart(roomId, gameStateDTO);

        return gameStateDTO;
    }

    /**
     * @param gameId Game id to force end game
     * @param error Notification to players
     * @return GameStateDTO
     */
    public GameStateDTO forceEndGame(Long gameId, String error) {
        GameSession session = gameSessionRepository.findById(gameId)
                .orElseThrow(() -> new GameException("Game not found"));

//        Random random = new Random();
//        for (User player : session.getPlayers()) {
//            int points = random.nextInt(500) - 250;
//        }

        session.setStatus(GameStatus.GAME_FINISHED);
        session.setEndTime(LocalDateTime.now());
        gameSessionRepository.save(session);

        GameState state = deserializeGameState(session.getGameState());

        GameStateDTO finalState = convertToDTO(state);

        Room room = roomRepository.findByCurrentGame(session)
                .orElseThrow(() -> new RoomException("Room not found or not available"));

        room.setCurrentGame(null);
        roomRepository.save(room);

        webSocketService.notifyGameEnded(gameId, "Forced game end because " + error);

        return finalState;
    }

    /**
     * @param gameId Game id to force end game
     * @param move Player move
     * @param player Who makes a move
     * @return GameStateDTO
     */
    public GameStateDTO playCard(Long gameId, PlayerMoveDTO move, User player) {
        GameSession session = gameSessionRepository.findById(gameId)
                .orElseThrow(() -> new GameException("Game not found"));

        GameState state = deserializeGameState(session.getGameState());
        validateMove(state, move, player);

        executeMove(state, move);

        if (state.getCurrentTrick().size() == getMaxPlayersCount()) {
            handleCompleteTrick(state);
        }

        if (isRoundComplete(state)) {
            handleRoundEnd(state, gameId);
        }

        session.setGameState(serializeGameState(state));
        gameSessionRepository.save(session);

        GameStateDTO gameStateDTO = convertToDTO(state);

        webSocketService.notifyGameStateChanged(gameStateDTO);

        return gameStateDTO;
    }

    /**
     * @param state GameState of current game
     * @param move Player move
     * @param player Who makes a move
     */
    private void validateMove(GameState state, PlayerMoveDTO move, User player) {
        if (!player.getUsername().equals(state.getCurrentPlayer())) {
            throw new GameException("Not your turn");
        }

        List<Card> playerHand = state.getPlayerHands().get(player.getUsername());
        if (!playerHand.contains(move.getPlayedCard())) {
            throw new GameException("Card not in hand");
        }

        if (state.isHeartsBlocked() && move.getPlayedCard().isHeart()) {
            if (hasNonHeartCards(playerHand)) {
                throw new GameException("Hearts are blocked");
            }
        }

        if (!state.getCurrentTrick().isEmpty()) {
            Card.Suit leadSuit = state.getCurrentTrick().getFirst().getSuit();
            if (move.getPlayedCard().getSuit() != leadSuit &&
                    hasCardsOfSuit(playerHand, leadSuit)) {
                throw new GameException("Must follow suit");
            }
        }
    }

    /**
     * @param state GameState of current game
     */
    private void handleCompleteTrick(GameState state) {
        String winner = determineTrickWinner(state, state.getCurrentTrick(), state.getTrump());
        updateScores(state, winner);
        state.setCurrentPlayer(winner);
        state.getCurrentTrick().clear();

        if (state.getCurrentPhase() == 11) {
            for (String player : state.getPlayerOrder()) {
                handleLoteryjkaPoints(state, player);
            }
        }
    }

    /**
     * @param state GameState of current game
     * @param gameId Game id to round end
     */
    private void handleRoundEnd(GameState state, long gameId) {
        state.setCurrentRound(state.getCurrentRound() + 1);
        state.setDealer(getNextPlayer(state.getDealer(), state.getPlayerOrder()));

        if (state.getCurrentRound() <= 11) {
            Deck deck = new Deck();
            deck.shuffle();
            state.setPlayerHands(dealCards(deck, state.getPlayerOrder()));
            state.setCurrentPlayer(getNextPlayer(state.getDealer(), state.getPlayerOrder()));

            if (state.getCurrentRound() == 8) {
                setupAtutPhase(state);
            } else if (state.getCurrentRound() == 11) {
                setupLoteryjkaPhase(state);
            }
        } else {
            endGame(state, gameId);
        }
    }

    /**
     * @param deck Deck of card
     * @param players List of player's usernames
     * @return Username: Hand card
     */
    private Map<String, List<Card>> dealCards(Deck deck, List<String> players) {
        Map<String, List<Card>> hands = new HashMap<>();
        int cardsPerPlayer = deck.getCards().size() / players.size();

        for (int i = 0; i < players.size(); i++) {
            hands.put(players.get(i), new ArrayList<>(
                    deck.getCards().subList(i * cardsPerPlayer, (i + 1) * cardsPerPlayer)
            ));
        }
        return hands;
    }

    /**
     * @param players Players in game
     * @return Username: 0
     */
    private Map<String, Integer> initializeScores(Set<User> players) {
        return players.stream().collect(Collectors.toMap(
                User::getUsername,
                player -> 0
        ));
    }

    /**
     * @param players Players in game
     * @return Dealer username
     */
    private String pickRandomDealer(Set<User> players) {
        List<User> playerList = new ArrayList<>(players);
        return playerList.get(new Random().nextInt(playerList.size())).getUsername();
//        return playerList.getFirst().getUsername();
    }

    /**
     * @param currentPlayer Username on move
     * @param playerOrder Players order usernames
     * @return Next on move username
     */
    private String getNextPlayer(String currentPlayer, List<String> playerOrder) {
        int currentIndex = playerOrder.indexOf(currentPlayer);
        return playerOrder.get((currentIndex + 1) % playerOrder.size());
    }

    /**
     * @param state GameState
     * @return GameStateDTO
     */
    private GameStateDTO convertToDTO(GameState state) {
        return GameStateDTO.builder()
                .gameId(state.getGameId())
                .currentRound(state.getCurrentRound())
                .currentPhase(state.getCurrentPhase())
                .playerHands(state.getPlayerHands())
                .currentTrick(state.getCurrentTrick())
                .currentPlayer(state.getCurrentPlayer())
                .dealer(state.getDealer())
                .scores(state.getScores().entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        )))
                .heartsBlocked(state.isHeartsBlocked())
                .trump(state.getTrump())
                .build();
    }

    /**
     * @param gameState String to deserialize
     * @return GameState deserialized
     */
    private GameState deserializeGameState(String gameState) {
        try {
            return new ObjectMapper().readValue(gameState, GameState.class);
        } catch (JsonProcessingException e) {
            throw new GameException("Failed to deserialize game state");
        }
    }

    /**
     * @param state Current GameState
     * @param move Player move
     */
    private void executeMove(GameState state, PlayerMoveDTO move) {
        String player = state.getPlayerOrder().stream()
                .filter(p -> p.equals(move.getUsername()))
                .findFirst()
                .orElseThrow();

        state.getPlayerHands().get(player).remove(move.getPlayedCard());
        state.getCurrentTrick().add(move.getPlayedCard());
        state.setCurrentPlayer(getNextPlayer(player, state.getPlayerOrder()));
    }

    /**
     * @param state Current GameState
     * @return Round completed
     */
    private boolean isRoundComplete(GameState state) {
        return state.getPlayerHands().values().stream()
                .allMatch(List::isEmpty);
    }

    /**
     * @param state GameState to serialize
     * @return String serialized
     */
    private String serializeGameState(GameState state) {
        try {
            return new ObjectMapper().writeValueAsString(state);
        } catch (JsonProcessingException e) {
            throw new GameException("Failed to serialize game state");
        }
    }

    /**
     * @param hand Player cards
     * @return Has no heart
     */
    private boolean hasNonHeartCards(List<Card> hand) {
        return hand.stream().anyMatch(card -> card.getSuit() != Card.Suit.HEARTS);
    }


    /**
     * @param hand Player cards
     * @param suit Card suit
     * @return Has cards of suit
     */
    private boolean hasCardsOfSuit(List<Card> hand, Card.Suit suit) {
        return hand.stream().anyMatch(card -> card.getSuit() == suit);
    }

    /**
     * @param state Current GameState
     */
    private void setupAtutPhase(GameState state) {
        state.setCurrentPhase(8);
        List<Card> dealerHand = state.getPlayerHands().get(state.getDealer());
        state.setTrump(dealerHand.subList(0, 5).getFirst().getSuit());
    }

    /**
     * @param state Current GameState
     */
    private void setupLoteryjkaPhase(GameState state) {
        state.setCurrentPhase(11);
        state.setTrump(null);
        state.setHeartsBlocked(false);
    }

    /**
     * @param state Current GameState
     * @param gameId Game id to end game
     */
    private void endGame(GameState state, long gameId) {
        String winner = determineGameWinner(state);
        GameSession session = gameSessionRepository.findById(gameId)
                .orElseThrow(() -> new GameException("Game not found"));

        Room room = roomRepository.findByCurrentGame(session)
                .orElseThrow(() -> new RoomException("Room not found or not available"));

        room.setCurrentGame(null);
        roomRepository.save(room);

        webSocketService.notifyGameEnded(gameId, "Winner is: " + winner);
    }

    /**
     * @param state Current GameState
     * @return Winner username
     */
    private String determineGameWinner(GameState state) {
        return state.getScores().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new GameException("Cannot determine winner"));
    }

    /**
     * @param state Current GameState
     * @param trick Game trick
     * @param targetCard Played card
     * @return Card owner username
     */
    private String getCardOwner(GameState state, List<Card> trick, Card targetCard) {
        for (int i = 0; i < trick.size(); i++) {
            if (trick.get(i).equals(targetCard)) {
                return state.getPlayerOrder().get(i);
            }
        }
        throw new GameException("Card owner not found");
    }

    /**
     * @param state Current GameState
     * @param winner Winner username
     */
    private void updateScores(GameState state, String winner) {
        int points = calculatePoints(state, state.getCurrentTrick(), state.getCurrentPhase());
        Map<String, Integer> scores = state.getScores();
        scores.put(winner, scores.get(winner) + points);
    }

    /**
     * @param state Current GameState
     * @param trick Game trick
     * @param phase Round phase
     * @return Points
     */
    private int calculatePoints(GameState state, List<Card> trick, int phase) {
        switch (phase) {
            case 1: return -20;
            case 2: return countHeartsPoints(trick);
            case 3: return countQueensPoints(trick);
            case 4: return countJacksAndKingsPoints(trick);
            case 5: return countHeartKingPoints(trick);
            case 6: return calculateSeventhLastTrickPoints(trick,
                    state.getPlayerHands().get(state.getCurrentPlayer()).size());
            case 7: return calculateRozbojnikPoints(state, trick);
            case 8:
            case 9:
            case 10:
            case 11: return 25;
            default: return 0;
        }
    }

    private int countHeartsPoints(List<Card> trick) {
        return (int) trick.stream()
                .filter(Card::isHeart)
                .count() * -20;
    }

    private int countQueensPoints(List<Card> trick) {
        return (int) trick.stream()
                .filter(card -> card.getRank() == Card.Rank.QUEEN)
                .count() * -60;
    }

    private int countJacksAndKingsPoints(List<Card> trick) {
        return (int) trick.stream()
                .filter(card -> card.getRank() == Card.Rank.JACK ||
                        card.getRank() == Card.Rank.KING)
                .count() * -30;
    }

    private int countHeartKingPoints(List<Card> trick) {
        return trick.stream()
                .anyMatch(card -> card.getSuit() == Card.Suit.HEARTS &&
                        card.getRank() == Card.Rank.KING) ? -150 : 0;
    }

    private int calculateSeventhLastTrickPoints(List<Card> trick, int remainingCards) {
        if (remainingCards == 6 || remainingCards == 0) {
            return -75;
        }
        return 0;
    }

    private int calculateRozbojnikPoints(GameState state, List<Card> trick) {
        return countHeartsPoints(trick) +
                countQueensPoints(trick) +
                countJacksAndKingsPoints(trick) +
                countHeartKingPoints(trick) +
                calculateSeventhLastTrickPoints(trick,
                        state.getPlayerHands().get(state.getCurrentPlayer()).size());
    }

    private void handleLoteryjkaPoints(GameState state, String player) {
        Map<String, Integer> scores = state.getScores();
        if (!state.getPlayerHands().get(player).isEmpty()) {
            return;
        }

        long finishedPlayers = state.getPlayerHands().values().stream()
                .filter(List::isEmpty)
                .count();

        if (finishedPlayers == 1) {
            scores.put(player, scores.get(player) + 800);
        } else if (finishedPlayers == 2) {
            scores.put(player, scores.get(player) + 500);
        }
    }

    private String determineTrickWinner(GameState state, List<Card> trick, Card.Suit trump) {
        Card winningCard = trick.getFirst();
        Card.Suit leadSuit = winningCard.getSuit();

        for (Card card : trick) {
            if (card.getSuit() == trump) {
                if (winningCard.getSuit() != trump ||
                        card.getRank().getValue() > winningCard.getRank().getValue()) {
                    winningCard = card;
                }
            } else if (card.getSuit() == leadSuit &&
                    card.getRank().getValue() > winningCard.getRank().getValue()) {
                winningCard = card;
            }
        }

        return getCardOwner(state, trick, winningCard);
    }

    public int getMaxRoomsCreated() {
        return gameConfiguration.getRoomSettings().getMaxRoomCreated();
    }

    public int getMaxPlayersCount() {
        return gameConfiguration.getGameSettings().getMaxPlayersCount();
    }
}
