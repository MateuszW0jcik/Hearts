package org.example.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.example.client.controller.RoomsViewController;
import org.example.client.utils.SceneManager;
import org.springframework.boot.Banner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class ClientApplication extends Application {
	private ConfigurableApplicationContext springContext;

	@Override
	public void init(){
		springContext = new SpringApplicationBuilder(ClientApplication.class)
				.bannerMode(Banner.Mode.OFF)
				.run(getParameters().getRaw().toArray(new String[0]));
	}

	@Override
	public void start(Stage primaryStage) {
		springContext.getBeanFactory().registerSingleton("primaryStage", primaryStage);

		RoomsViewController roomsViewController = springContext.getBean(RoomsViewController.class);
		SceneManager sceneManager = springContext.getBean(SceneManager.class);
		sceneManager.setPrimaryStage(primaryStage);

		sceneManager.loadScenes();
		sceneManager.switchToLogin();

		primaryStage.setTitle("Hearts");
		primaryStage.setResizable(false);

		primaryStage.setOnCloseRequest(event -> {
			roomsViewController.handleLeaveAllRooms();
			sceneManager.closeAllStages();
		});

		primaryStage.show();
	}

	@Override
	public void stop() {
		springContext.close();
		Platform.exit();
	}

	public static void main(String[] args) {
		launch(args);
	}
}


