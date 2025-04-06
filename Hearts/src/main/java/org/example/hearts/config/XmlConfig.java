package org.example.hearts.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "game")
@Data
public class XmlConfig {
    private GameSequence gameSequence;
    private RoomSettings roomSettings;

    @Data
    public static class GameSequence {
        private int rounds;
        private int pointsLimit;
        private int timeoutSeconds;
    }

    @Data
    public static class RoomSettings {
        private int maxRooms;
        private int playersPerRoom;
    }
}

