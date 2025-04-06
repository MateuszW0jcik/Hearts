package org.example.client.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VoiceMessage {
    private byte[] audioData;
    private String username;
    private Long timestamp;
    private Long roomId;
}
