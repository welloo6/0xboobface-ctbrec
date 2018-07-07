package ctbrec.ui;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.Hmac;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;;

public class SettingsTab extends Tab {

    private static final transient Logger LOG = LoggerFactory.getLogger(SettingsTab.class);

    private TextField recordingsDirectory;
    private TextField mediaPlayer;
    private TextField username;
    private TextField server;
    private TextField port;
    private CheckBox loadResolution;
    private CheckBox secureCommunication;
    private PasswordField password;
    private RadioButton recordLocal;
    private RadioButton recordRemote;
    private ToggleGroup recordLocation;

    public SettingsTab() {
        setText("Settings");
        createGui();
        setClosable(false);
    }

    private void createGui() {
        GridPane layout = new GridPane();
        layout.setOpacity(1);
        layout.setPadding(new Insets(5));
        layout.setHgap(5);
        layout.setVgap(5);
        setContent(layout);

        int row = 0;
        layout.add(new Label("Recordings Directory"), 0, row);
        recordingsDirectory = new TextField(Config.getInstance().getSettings().recordingsDir);
        recordingsDirectory.focusedProperty().addListener(createRecordingsDirectoryFocusListener());
        recordingsDirectory.setPrefWidth(400);
        GridPane.setFillWidth(recordingsDirectory, true);
        GridPane.setHgrow(recordingsDirectory, Priority.ALWAYS);
        GridPane.setColumnSpan(recordingsDirectory, 2);
        layout.add(recordingsDirectory, 1, row);
        layout.add(createRecordingsBrowseButton(), 3, row);

        layout.add(new Label("Player"), 0, ++row);
        mediaPlayer = new TextField(Config.getInstance().getSettings().mediaPlayer);
        mediaPlayer.focusedProperty().addListener(createMpvFocusListener());
        GridPane.setFillWidth(mediaPlayer, true);
        GridPane.setHgrow(mediaPlayer, Priority.ALWAYS);
        GridPane.setColumnSpan(mediaPlayer, 2);
        layout.add(mediaPlayer, 1, row);
        layout.add(createMpvBrowseButton(), 3, row);

        layout.add(new Label("Chaturbate User"), 0, ++row);
        username = new TextField(Config.getInstance().getSettings().username);
        username.focusedProperty().addListener((e) -> Config.getInstance().getSettings().username = username.getText());
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        layout.add(username, 1, row);

        layout.add(new Label("Chaturbate Password"), 0, ++row);
        password = new PasswordField();
        password.setText(Config.getInstance().getSettings().password);
        password.focusedProperty().addListener((e) -> {
            if(!password.getText().isEmpty()) {
                Config.getInstance().getSettings().password = password.getText();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        layout.add(password, 1, row);

        layout.add(new Label("Display stream resolution in overview"), 0, ++row);
        loadResolution = new CheckBox();
        loadResolution.setSelected(Config.getInstance().getSettings().determineResolution);
        loadResolution.setOnAction((e) -> {
            Config.getInstance().getSettings().determineResolution = loadResolution.isSelected();
            if(!loadResolution.isSelected()) {
                ThumbOverviewTab.queue.clear();
            }
        });
        layout.add(loadResolution, 1, row);

        layout.add(new Label(), 0, ++row);

        layout.add(new Label("Record Location"), 0, ++row);
        recordLocation = new ToggleGroup();
        recordLocal = new RadioButton("Local");
        recordRemote = new RadioButton("Remote");
        recordLocal.setToggleGroup(recordLocation);
        recordRemote.setToggleGroup(recordLocation);
        recordLocal.setSelected(Config.getInstance().getSettings().localRecording);
        recordRemote.setSelected(!recordLocal.isSelected());
        layout.add(recordLocal, 1, row);
        layout.add(recordRemote, 2, row);
        recordLocation.selectedToggleProperty().addListener((e) -> {
            Config.getInstance().getSettings().localRecording = recordLocal.isSelected();
            server.setDisable(recordLocal.isSelected());
            port.setDisable(recordLocal.isSelected());

            Alert restart = new AutosizeAlert(AlertType.INFORMATION);
            restart.setTitle("Restart required");
            restart.setHeaderText("Restart required");
            restart.setContentText("Changes get applied after a restart of the application");
            restart.show();
        });

        layout.add(new Label("Server"), 0, ++row);
        server = new TextField(Config.getInstance().getSettings().httpServer);
        server.focusedProperty().addListener((e) -> {
            if(!server.getText().isEmpty()) {
                Config.getInstance().getSettings().httpServer = server.getText();
            }
        });
        GridPane.setFillWidth(server, true);
        GridPane.setHgrow(server, Priority.ALWAYS);
        GridPane.setColumnSpan(server, 2);
        layout.add(server, 1, row);

        layout.add(new Label("Port"), 0, ++row);
        port = new TextField(Integer.toString(Config.getInstance().getSettings().httpPort));
        port.focusedProperty().addListener((e) -> {
            if(!port.getText().isEmpty()) {
                try {
                    Config.getInstance().getSettings().httpPort = Integer.parseInt(port.getText());
                    port.setBorder(Border.EMPTY);
                    port.setTooltip(null);
                } catch (NumberFormatException e1) {
                    port.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.DASHED, new CornerRadii(2), new BorderWidths(2))));
                    port.setTooltip(new Tooltip("Port has to be a number in the range 1 - 65536"));
                }
            }
        });
        GridPane.setFillWidth(port, true);
        GridPane.setHgrow(port, Priority.ALWAYS);
        GridPane.setColumnSpan(port, 2);
        layout.add(port, 1, row);

        layout.add(new Label("Require authentication"), 0, ++row);
        secureCommunication = new CheckBox();
        secureCommunication.setSelected(Config.getInstance().getSettings().requireAuthentication);
        secureCommunication.setOnAction((e) -> {
            Config.getInstance().getSettings().requireAuthentication = secureCommunication.isSelected();
            if(secureCommunication.isSelected()) {
                byte[] key = Config.getInstance().getSettings().key;
                if(key == null) {
                    key = Hmac.generateKey();
                    Config.getInstance().getSettings().key = key;
                }
                TextInputDialog keyDialog = new TextInputDialog();
                keyDialog.setResizable(true);
                keyDialog.setTitle("Server Authentication");
                keyDialog.setHeaderText("A key has been generated");
                keyDialog.setContentText("Add this setting to your server's config.json:\n");
                keyDialog.getEditor().setText("\"key\": " + Arrays.toString(key));
                keyDialog.getEditor().setEditable(false);
                keyDialog.setWidth(800);
                keyDialog.setHeight(200);
                keyDialog.show();
            }
        });
        layout.add(secureCommunication, 1, row);


        server.setDisable(recordLocal.isSelected());
        port.setDisable(recordLocal.isSelected());
    }

    private ChangeListener<? super Boolean> createRecordingsDirectoryFocusListener() {
        return new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldPropertyValue, Boolean newPropertyValue) {
                if (newPropertyValue) {
                    recordingsDirectory.setBorder(Border.EMPTY);
                    recordingsDirectory.setTooltip(null);
                } else {
                    String input = recordingsDirectory.getText();
                    File newDir = new File(input);
                    setRecordingsDir(newDir);
                }
            }
        };
    }

    private ChangeListener<? super Boolean> createMpvFocusListener() {
        return new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> arg0, Boolean oldPropertyValue, Boolean newPropertyValue) {
                if (newPropertyValue) {
                    mediaPlayer.setBorder(Border.EMPTY);
                    mediaPlayer.setTooltip(null);
                } else {
                    String input = mediaPlayer.getText();
                    File program = new File(input);
                    setMpv(program);
                }
            }
        };
    }

    private void setMpv(File program) {
        String msg = validateProgram(program);
        if (msg != null) {
            mediaPlayer.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.DASHED, new CornerRadii(2), new BorderWidths(2))));
            mediaPlayer.setTooltip(new Tooltip(msg));
        } else {
            Config.getInstance().getSettings().mediaPlayer = mediaPlayer.getText();
        }
    }

    private String validateProgram(File program) {
        if (program == null || !program.exists()) {
            return "File does not exist";
        } else if (!program.isFile() || !program.canExecute()) {
            return "This is not an executable application";
        }
        return null;
    }

    private Node createRecordingsBrowseButton() {
        Button button = new Button("Select");
        button.setOnAction((e) -> {
            DirectoryChooser chooser = new DirectoryChooser();
            File currentDir = new File(Config.getInstance().getSettings().recordingsDir);
            if (currentDir.exists() && currentDir.isDirectory()) {
                chooser.setInitialDirectory(currentDir);
            }
            File selectedDir = chooser.showDialog(null);
            if(selectedDir != null) {
                setRecordingsDir(selectedDir);
            }
        });
        return button;
    }

    private Node createMpvBrowseButton() {
        Button button = new Button("Select");
        button.setOnAction((e) -> {
            FileChooser chooser = new FileChooser();
            File program = chooser.showOpenDialog(null);
            if(program != null) {
                try {
                    mediaPlayer.setText(program.getCanonicalPath());
                } catch (IOException e1) {
                    LOG.error("Couldn't determine path", e1);
                    Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
                    alert.setTitle("Whoopsie");
                    alert.setContentText("Couldn't determine path");
                    alert.showAndWait();
                }
                setMpv(program);
            }
        });
        return button;
    }

    private void setRecordingsDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            try {
                String path = dir.getCanonicalPath();
                Config.getInstance().getSettings().recordingsDir = path;
                recordingsDirectory.setText(path);
            } catch (IOException e1) {
                LOG.error("Couldn't determine directory path", e1);
                Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
                alert.setTitle("Whoopsie");
                alert.setContentText("Couldn't determine directory path");
                alert.showAndWait();
            }
        } else {
            recordingsDirectory.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.DASHED, new CornerRadii(2), new BorderWidths(2))));
            if (!dir.isDirectory()) {
                recordingsDirectory.setTooltip(new Tooltip("This is not a directory"));
            }
            if (!dir.exists()) {
                recordingsDirectory.setTooltip(new Tooltip("Directory does not exist"));
            }

        }
    }
}
