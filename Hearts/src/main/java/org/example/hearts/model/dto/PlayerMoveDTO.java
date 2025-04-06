package org.example.hearts.model.dto;

import lombok.Data;
import org.example.hearts.model.game.Card;

@Data
public class PlayerMoveDTO {
    private Long gameId;
    private String username;
    private Card playedCard;
}
