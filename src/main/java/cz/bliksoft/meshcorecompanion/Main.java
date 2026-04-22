package cz.bliksoft.meshcorecompanion;

import java.io.File;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cz.bliksoft.javautils.EnvironmentUtils;
import cz.bliksoft.javautils.PropertiesUtils;
import cz.bliksoft.javautils.app.ui.BSAppUI;
import cz.bliksoft.javautils.logging.LogUtils;
import cz.bliksoft.javautils.modules.Modules;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

	private static Logger log;

	public static void main(String[] args) {
		EnvironmentUtils.setAppName("BSMeshcoreCompanion");
		try {
			EnvironmentUtils.init();
			LogUtils.init(PropertiesUtils.loadFromFile(new File("config/app.properties")));
			log = LogManager.getLogger();
			launch(args);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void start(Stage stage) {
		log.info("BSMeshcoreCompanion started");
		Modules.autoloadModule(BSMeshcoreCompanionModule.class);
		BSAppUI.init(this, stage);
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		log.info("BSMeshcoreCompanion terminated");
	}
}
