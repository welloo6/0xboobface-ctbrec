package ctbrec;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import ctbrec.recorder.OS;
import ctbrec.ui.AutosizeAlert;
import javafx.scene.control.Alert;
import okio.Buffer;
import okio.BufferedSource;

public class Config {

    private static final transient Logger LOG = LoggerFactory.getLogger(Config.class);

    private static Config instance = new Config();
    private Settings settings;
    private String filename;

    private Config() {
        if(System.getProperty("ctbrec.config") != null) {
            filename = System.getProperty("ctbrec.config");
        } else {
            filename = "settings.json";
        }
        load();
    }

    private void load() {
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<Settings> adapter = moshi.adapter(Settings.class);
        File configDir = OS.getConfigDir();
        File configFile = new File(configDir, filename);
        LOG.debug("Loading config from {}", configFile.getAbsolutePath());
        if(configFile.exists()) {
            try(FileInputStream fin = new FileInputStream(configFile); Buffer buffer = new Buffer()) {
                BufferedSource source =  buffer.readFrom(fin);
                settings = adapter.fromJson(source);
            } catch(Exception e) {
                Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
                alert.setTitle("Whoopsie");
                alert.setContentText("Couldn't load settings.");
                alert.showAndWait();
                System.exit(1);
            }
        } else {
            LOG.error("Config file does not exist. Falling back to default values.");
            settings = OS.getDefaultSettings();
        }
    }

    public static Config getInstance() {
        return instance;
    }

    public Settings getSettings() {
        return settings;
    }

    public void save() throws IOException {
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<Settings> adapter = moshi.adapter(Settings.class);
        String json = adapter.toJson(settings);
        File configDir = OS.getConfigDir();
        File configFile = new File(configDir, filename);
        LOG.debug("Saving config to {}", configFile.getAbsolutePath());
        Files.createDirectories(configDir.toPath());
        Files.write(configFile.toPath(), json.getBytes("utf-8"), CREATE, WRITE, TRUNCATE_EXISTING);
    }
}
