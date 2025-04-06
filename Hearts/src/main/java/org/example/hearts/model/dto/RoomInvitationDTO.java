package org.example.hearts.model.dto;

import lombok.Data;

@Data
public class RoomInvitationDTO {
    private Long roomId;
    private String roomName;
    private String senderUsername;
    private String invitedUsername;
}
