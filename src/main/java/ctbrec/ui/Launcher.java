package ctbrec.ui;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.HttpClient;
import ctbrec.recorder.LocalRecorder;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.RemoteRecorder;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TabPane.TabClosingPolicy;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Launcher extends Application {

    private static final transient Logger LOG = LoggerFactory.getLogger(Launcher.class);
    public static final String BASE_URI = "https://chaturbate.com";

    private Recorder recorder;
    private HttpClient client;
    private static HostServices hostServices;

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            Config.init();
        } catch (Exception e) {
            Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
            alert.setTitle("Whoopsie");
            alert.setContentText("Couldn't load settings.");
            alert.showAndWait();
            System.exit(1);
        }
        hostServices = getHostServices();
        Config config = Config.getInstance();
        client = HttpClient.getInstance();
        if(config.getSettings().localRecording) {
            recorder = new LocalRecorder(config);
        } else {
            recorder = new RemoteRecorder(config, client);
        }
        if(config.getSettings().username != null && !config.getSettings().username.isEmpty()) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        client.login();
                    } catch (IOException e1) {
                        LOG.warn("Initial login failed" , e1);
                    }
                };
            }.start();
        }

        LOG.debug("Creating GUI");
        primaryStage.setTitle("CTB Recorder");
        InputStream icon = getClass().getResourceAsStream("/icon.png");
        primaryStage.getIcons().add(new Image(icon));
        TabPane root = new TabPane();
        root.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {
            @Override
            public void changed(ObservableValue<? extends Tab> ov, Tab from, Tab to) {
                if(from != null && from instanceof TabSelectionListener) {
                    ((TabSelectionListener) from).deselected();
                }
                if(to != null && to instanceof TabSelectionListener) {
                    ((TabSelectionListener) to).selected();
                }
            }
        });
        root.setTabClosingPolicy(TabClosingPolicy.SELECTED_TAB);
        root.getTabs().add(createTab("Featured", BASE_URI + "/"));
        root.getTabs().add(createTab("Female", BASE_URI + "/female-cams/"));
        root.getTabs().add(createTab("Male", BASE_URI + "/male-cams/"));
        root.getTabs().add(createTab("Couples", BASE_URI + "/couple-cams/"));
        root.getTabs().add(createTab("Trans", BASE_URI + "/trans-cams/"));
        FollowedTab tab = new FollowedTab("Followed", BASE_URI + "/followed-cams/");
        tab.setRecorder(recorder);
        root.getTabs().add(tab);
        RecordedModelsTab modelsTab = new RecordedModelsTab("Recording", recorder);
        root.getTabs().add(modelsTab);
        RecordingsTab recordingsTab = new RecordingsTab("Recordings", recorder, config);
        root.getTabs().add(recordingsTab);
        root.getTabs().add(new SettingsTab());
        root.getTabs().add(new DonateTabFx());

        primaryStage.setScene(new Scene(root, 1340, 720));
        primaryStage.show();
        primaryStage.setOnCloseRequest((e) -> {
            e.consume();
            Alert shutdownInfo = new AutosizeAlert(Alert.AlertType.INFORMATION);
            shutdownInfo.setTitle("Shutdown");
            shutdownInfo.setContentText("Shutting down. Please wait a few seconds...");
            shutdownInfo.show();

            new Thread() {
                @Override
                public void run() {
                    recorder.shutdown();
                    client.shutdown();
                    try {
                        Config.getInstance().save();
                        LOG.info("Shutdown complete. Goodbye!");
                        Platform.exit();
                        // This is needed, because OkHttp?! seems to block the shutdown with its writer threads. They are not daemon threads :(
                        System.exit(0);
                    } catch (IOException e1) {
                        Platform.runLater(() -> {
                            Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
                            alert.setTitle("Error saving settings");
                            alert.setContentText("Couldn't save settings: " + e1.getLocalizedMessage());
                            alert.showAndWait();
                            System.exit(1);
                        });
                    }
                }
            }.start();
        });
    }

    Tab createTab(String title, String url) {
        ThumbOverviewTab tab = new ThumbOverviewTab(title, url, false);
        tab.setRecorder(recorder);
        return tab;
    }

    public static void open(String uri) {
        hostServices.showDocument(uri);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
