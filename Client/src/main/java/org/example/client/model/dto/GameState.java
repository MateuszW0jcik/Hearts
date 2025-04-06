package org.example.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.client.model.game.Card;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameState {
    private long gameId;
    private int currentRound;
    private int currentPhase;
    private List<String> playerOrder;
    private Map<String, List<Card>> playerHands;
    private Map<String, Integer> scores;
    private List<Card> currentTrick;
    private String currentPlayer;
    private String dealer;
    private Card.Suit trump;
    private boolean heartsBlocked;
}


