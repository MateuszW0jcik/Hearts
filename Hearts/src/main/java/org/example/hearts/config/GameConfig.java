package org.example.hearts.config;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@Configuration
@ImportResource("classpath:game-config.xml")
public class GameConfig {

    @Bean
    public org.example.hearts.config.GameConfiguration gameConfiguration() {
        try {
            Resource resource = new ClassPathResource("/game-config.xml");
            JAXBContext context = JAXBContext.newInstance(org.example.hearts.config.GameConfiguration.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (GameConfiguration) unmarshaller.unmarshal(resource.getInputStream());
        } catch (Exception e) {
            throw new RuntimeException("Could not load game configuration", e);
        }
    }

    @Bean
    public RoomSettings roomSettings(GameConfiguration gameConfiguration) {
        return gameConfiguration.getRoomSettings();
    }

    @Bean
    public GameSettings gameSettings(GameConfiguration gameConfiguration) {
        return gameConfiguration.getGameSettings();
    }

}
