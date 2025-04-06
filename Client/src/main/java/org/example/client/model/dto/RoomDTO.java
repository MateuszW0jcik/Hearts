package org.example.client.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoomDTO {
    private Long id;
    private String name;
    private String owner;
    private List<String> players;
    private RoomStatus status;
    private boolean isPrivate;
}
