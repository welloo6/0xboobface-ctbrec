package ctbrec.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;

public class AutosizeAlert extends Alert {

    public AutosizeAlert(AlertType type) {
        super(type);
        init();
    }

    public AutosizeAlert(AlertType type, String text, ButtonType... buttons) {
        super(type, text, buttons);
        init();
    }

    private void init() {
        setResizable(true);
        getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
    }
}
