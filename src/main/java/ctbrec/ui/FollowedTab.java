package ctbrec.ui;

import javafx.concurrent.WorkerStateEvent;
import javafx.scene.control.Label;

public class FollowedTab extends ThumbOverviewTab {
    private Label status;

    public FollowedTab(String title, String url) {
        super(title, url, true);
        status = new Label("Logging in...");
        grid.getChildren().add(status);
    }

    @Override
    protected void onSuccess() {
        grid.getChildren().remove(status);
        super.onSuccess();
    }

    @Override
    protected void onFail(WorkerStateEvent event) {
        status.setText("Login failed");
        super.onFail(event);
    }

    @Override
    public void selected() {
        status.setText("Logging in...");
        super.selected();
    }
}
