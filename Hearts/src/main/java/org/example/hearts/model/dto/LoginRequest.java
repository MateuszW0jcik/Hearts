package org.example.hearts.model.dto;

import lombok.Data;
import org.example.hearts.model.game.Card;

import java.util.List;
import java.util.Map;

@Data
public class LoginRequest {
    private String username;
    private String password;
}

