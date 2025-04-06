package org.example.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.client.model.game.Card;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerMoveDTO {
    private Long gameId;
    private String username;
    private Card playedCard;
}
