package org.example.client.model.game;

import lombok.Data;
import org.example.client.model.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class Player {
    private User user;
    private List<Card> hand;
    private int currentScore;
    private boolean hasPlayedHearts;
}
