package org.example.hearts.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomDTO {
    private Long id;
    private String name;
    private String owner;
    private List<String> players;
    private RoomStatus status;
    private boolean isPrivate;
}
