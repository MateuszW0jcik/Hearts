package org.example.hearts.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WebSocketMessage<T> {
    private String type;
    private T payload;
}
