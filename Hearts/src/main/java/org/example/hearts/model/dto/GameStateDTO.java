package org.example.hearts.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.hearts.model.game.Card;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateDTO {
    private Long gameId;
    private int currentRound;
    private int currentPhase;
    private Map<String, List<Card>> playerHands;
    private List<Card> currentTrick;
    private String currentPlayer;
    private String dealer;
    private Map<String, Integer> scores;
    private boolean heartsBlocked;
    private Card.Suit trump;
    private GameStatus status;
}
