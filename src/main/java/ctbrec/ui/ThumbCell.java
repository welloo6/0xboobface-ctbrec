package ctbrec.ui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;

import ctbrec.Config;
import ctbrec.HttpClient;
import ctbrec.Model;
import ctbrec.recorder.Chaturbate;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.StreamInfo;
import javafx.animation.FadeTransition;
import javafx.animation.FillTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ThumbCell extends StackPane {

    private static final transient Logger LOG = LoggerFactory.getLogger(ThumbCell.class);

    private static final int WIDTH = 180;
    private static final int HEIGHT = 135;
    private static final Duration ANIMATION_DURATION = new Duration(250);

    // this acts like a cache, once the stream resolution for a model has been determined, we don't do it again (until ctbrec is restarted)
    private static Map<String, int[]> resolutions = new HashMap<>();

    private Model model;
    private ImageView iv;
    private Rectangle resolutionBackground;
    private Rectangle nameBackground;
    private Rectangle topicBackground;
    private Text name;
    private Text topic;
    private Text resolutionTag;
    private Recorder recorder;
    private Circle recordingIndicator;
    private FadeTransition recordingAnimation;
    private int index = 0;
    ContextMenu popup;
    private Color colorNormal = Color.BLACK;
    private Color colorHighlight = Color.WHITE;
    private Color colorRecording = new Color(0.8, 0.28, 0.28, 1);

    private HttpClient client;

    private ThumbOverviewTab parent;
    private ObservableList<Node> thumbCellList;

    public ThumbCell(ThumbOverviewTab parent, Model model, Recorder recorder, HttpClient client) {
        this.parent = parent;
        this.thumbCellList = parent.grid.getChildren();
        this.model = model;
        this.recorder = recorder;
        this.client = client;
        boolean recording = recorder.isRecording(model);

        iv = new ImageView();
        setImage(model.getPreview());
        iv.setFitWidth(WIDTH);
        iv.setFitHeight(HEIGHT);
        iv.setSmooth(true);
        iv.setCache(true);
        getChildren().add(iv);

        nameBackground = new Rectangle(WIDTH, 20);
        nameBackground.setFill(recording ? colorRecording : colorNormal);
        nameBackground.setOpacity(.7);
        StackPane.setAlignment(nameBackground, Pos.BOTTOM_CENTER);
        getChildren().add(nameBackground);

        topicBackground = new Rectangle(WIDTH, 115);
        topicBackground.setFill(Color.BLACK);
        topicBackground.setOpacity(0);
        StackPane.setAlignment(topicBackground, Pos.TOP_LEFT);
        getChildren().add(topicBackground);

        resolutionBackground = new Rectangle(34, 16);
        resolutionBackground.setFill(new Color(0.22, 0.8, 0.29, 1));
        resolutionBackground.setVisible(false);
        resolutionBackground.setArcHeight(5);
        resolutionBackground.setArcWidth(resolutionBackground.getArcHeight());
        StackPane.setAlignment(resolutionBackground, Pos.TOP_RIGHT);
        StackPane.setMargin(resolutionBackground, new Insets(2));
        getChildren().add(resolutionBackground);

        name = new Text(model.getName());
        name.setFill(Color.WHITE);
        name.setFont(new Font("Sansserif", 16));
        name.setTextAlignment(TextAlignment.CENTER);
        name.prefHeight(25);
        StackPane.setAlignment(name, Pos.BOTTOM_CENTER);
        getChildren().add(name);

        topic = new Text(model.getDescription());

        topic.setFill(Color.WHITE);
        topic.setFont(new Font("Sansserif", 13));
        topic.setTextAlignment(TextAlignment.LEFT);
        topic.setOpacity(0);
        topic.prefHeight(110);
        topic.maxHeight(110);
        int margin = 4;
        topic.maxWidth(WIDTH-margin*2);
        topic.setWrappingWidth(WIDTH-margin*2);
        StackPane.setMargin(topic, new Insets(margin));
        StackPane.setAlignment(topic, Pos.TOP_CENTER);
        getChildren().add(topic);

        resolutionTag = new Text();
        resolutionTag.setFill(Color.WHITE);
        resolutionTag.setVisible(false);
        StackPane.setAlignment(resolutionTag, Pos.TOP_RIGHT);
        StackPane.setMargin(resolutionTag, new Insets(2, 4, 2, 2));
        getChildren().add(resolutionTag);

        recordingIndicator = new Circle(8);
        recordingIndicator.setFill(colorRecording);
        StackPane.setMargin(recordingIndicator, new Insets(3));
        StackPane.setAlignment(recordingIndicator, Pos.TOP_LEFT);
        getChildren().add(recordingIndicator);
        recordingAnimation = new FadeTransition(Duration.millis(1000), recordingIndicator);
        recordingAnimation.setInterpolator(Interpolator.EASE_BOTH);
        recordingAnimation.setFromValue(1.0);
        recordingAnimation.setToValue(0);
        recordingAnimation.setCycleCount(FadeTransition.INDEFINITE);
        recordingAnimation.setAutoReverse(true);

        setOnMouseEntered((e) -> {
            new ParallelTransition(changeColor(nameBackground, colorNormal, colorHighlight), changeColor(name, colorHighlight, colorNormal)).playFromStart();
            new ParallelTransition(changeOpacity(topicBackground, 0.7), changeOpacity(topic, 0.7)).playFromStart();
        });
        setOnMouseExited((e) -> {
            new ParallelTransition(changeColor(nameBackground, colorHighlight, colorNormal), changeColor(name, colorNormal, colorHighlight)).playFromStart();
            new ParallelTransition(changeOpacity(topicBackground, 0), changeOpacity(topic, 0)).playFromStart();
        });
        setOnMouseClicked(doubleClickListener);
        addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            parent.suspendUpdates(true);
            popup = createContextMenu();
            popup.show(ThumbCell.this, event.getScreenX(), event.getScreenY());
            popup.setOnHidden((e) -> parent.suspendUpdates(false));
            event.consume();
        });
        addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if(popup != null) {
                popup.hide();
            }
        });

        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);

        setRecording(recording);
        if(Config.getInstance().getSettings().determineResolution) {
            determineResolution();
        }
    }

    private void determineResolution() {
        if(ThumbOverviewTab.resolutionProcessing.contains(model)) {
            return;
        }

        ThumbOverviewTab.resolutionProcessing.add(model);
        int[] res = resolutions.get(model.getName());
        if(res == null) {
            ThumbOverviewTab.threadPool.submit(() -> {
                try {
                    Thread.sleep(500); // throttle down, so that we don't do too many requests
                    int[] resolution = Chaturbate.getResolution(model, client);
                    resolutions.put(model.getName(), resolution);
                    if (resolution[1] > 0) {
                        LOG.trace("Model resolution {} {}x{}", model.getName(), resolution[0], resolution[1]);
                        LOG.trace("Resolution queue size: {}", ThumbOverviewTab.queue.size());
                        final int w = resolution[1];
                        Platform.runLater(() -> {
                            resolutionTag.setText(Integer.toString(w));
                            resolutionTag.setVisible(true);
                            resolutionBackground.setVisible(true);
                            resolutionBackground.setWidth(resolutionTag.getBoundsInLocal().getWidth() + 4);
                        });
                    }
                } catch (IOException | ParseException | PlaylistException | InterruptedException e) {
                    LOG.error("Coulnd't get resolution for model {}", model, e);
                } finally {
                    ThumbOverviewTab.resolutionProcessing.remove(model);
                }
            });
        } else {
            ThumbOverviewTab.resolutionProcessing.remove(model);
            Platform.runLater(() -> {
                resolutionTag.setText(Integer.toString(res[1]));
                resolutionTag.setVisible(true);
                resolutionBackground.setVisible(true);
                resolutionBackground.setWidth(resolutionTag.getBoundsInLocal().getWidth() + 4);
            });
        }
    }

    private void setImage(String url) {
        if(!Objects.equals(System.getenv("CTBREC_THUMBS"), "0")) {
            Image img = new Image(url, true);

            // wait for the image to load, otherwise the ImageView replaces the current image with an "empty" image,
            // which causes to show the grey background until the image is loaded
            img.progressProperty().addListener(new ChangeListener<Number>() {
                @Override
                public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                    if(newValue.doubleValue() == 1.0) {
                        iv.setImage(img);
                    }
                }
            });
        }
    }

    private ContextMenu createContextMenu() {
        MenuItem openInPlayer = new MenuItem("Open in Player");
        openInPlayer.setOnAction((e) -> startPlayer());

        MenuItem start = new MenuItem("Start Recording");
        start.setOnAction((e) -> startStopAction(true));
        MenuItem stop = new MenuItem("Stop Recording");
        stop.setOnAction((e) -> startStopAction(false));
        MenuItem startStop = recorder.isRecording(model) ? stop : start;

        MenuItem follow = new MenuItem("Follow");
        follow.setOnAction((e) -> follow(true));
        MenuItem unfollow = new MenuItem("Unfollow");
        unfollow.setOnAction((e) -> follow(false));

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setAutoHide(true);
        contextMenu.setHideOnEscape(true);
        contextMenu.setAutoFix(true);
        MenuItem followOrUnFollow = parent instanceof FollowedTab ? unfollow : follow;
        contextMenu.getItems().addAll(openInPlayer, startStop , followOrUnFollow);
        return contextMenu;
    }

    private Transition changeColor(Shape shape, Color from, Color to) {
        FillTransition transition = new FillTransition(ANIMATION_DURATION, from, to);
        transition.setShape(shape);
        return transition;
    }

    private Transition changeOpacity(Shape shape, double opacity) {
        FadeTransition transition = new FadeTransition(ANIMATION_DURATION, shape);
        transition.setFromValue(shape.getOpacity());
        transition.setToValue(opacity);
        return transition;
    }

    private EventHandler<MouseEvent> doubleClickListener = new EventHandler<MouseEvent>() {
        @Override
        public void handle(MouseEvent e) {
            if(e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                startPlayer();
            }
        }
    };

    private void startPlayer() {
        try {
            StreamInfo streamInfo = Chaturbate.getStreamInfo(model, client);
            if(streamInfo.room_status.equals("public")) {
                Player.play(streamInfo.url);
            } else {
                Alert alert = new AutosizeAlert(Alert.AlertType.INFORMATION);
                alert.setTitle("Room not public");
                alert.setHeaderText("Room is currently not public");
                alert.showAndWait();
            }
        } catch (IOException e1) {
            LOG.error("Couldn't get stream information for model {}", model, e1);
            Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Couldn't determine stream URL");
            alert.showAndWait();
        }
    }

    private void setRecording(boolean recording) {
        if(recording) {
            recordingAnimation.playFromStart();
            colorNormal = colorRecording;
        } else {
            colorNormal = Color.BLACK;
            recordingAnimation.stop();
        }
        recordingIndicator.setVisible(recording);
    }

    private void startStopAction(boolean start) {
        setCursor(Cursor.WAIT);
        new Thread() {
            @Override
            public void run() {
                try {
                    if(start) {
                        recorder.startRecording(model);
                    } else {
                        recorder.stopRecording(model);
                    }
                    setRecording(start);
                } catch (Exception e1) {
                    LOG.error("Couldn't start/stop recording", e1);
                    Platform.runLater(() -> {
                        Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Couldn't start/stop recording");
                        alert.setContentText("I/O error while starting/stopping the recording: " + e1.getLocalizedMessage());
                        alert.showAndWait();
                    });
                } finally {
                    setCursor(Cursor.DEFAULT);
                }
            }
        }.start();
    }

    private void follow(boolean follow) {
        setCursor(Cursor.WAIT);
        new Thread() {
            @Override
            public void run() {
                try {
                    Request req = new Request.Builder().url(model.getUrl()).build();
                    Response resp = HttpClient.getInstance().execute(req);
                    resp.close();

                    String url = null;
                    if(follow) {
                        url = Launcher.BASE_URI + "/follow/follow/" + model.getName() + "/";
                    } else {
                        url = Launcher.BASE_URI + "/follow/unfollow/" + model.getName() + "/";
                    }

                    RequestBody body = RequestBody.create(null, new byte[0]);
                    req = new Request.Builder()
                            .url(url)
                            .method("POST", body)
                            .header("Accept", "*/*")
                            //.header("Accept-Encoding", "gzip, deflate, br")
                            .header("Accept-Language", "en-US,en;q=0.5")
                            .header("Referer", model.getUrl())
                            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64; rv:59.0) Gecko/20100101 Firefox/59.0")
                            .header("X-CSRFToken", HttpClient.getInstance().getToken())
                            .header("X-Requested-With", "XMLHttpRequest")
                            .build();
                    resp = HttpClient.getInstance().execute(req, true);
                    if(resp.isSuccessful()) {
                        String msg = resp.body().string();
                        if(!msg.equalsIgnoreCase("ok")) {
                            LOG.debug(msg);
                            throw new IOException("Response was " + msg.substring(0, Math.min(msg.length(), 500)));
                        } else {
                            if(!follow) {
                                Platform.runLater(() -> thumbCellList.remove(ThumbCell.this));
                            }
                        }
                    } else {
                        resp.close();
                        throw new IOException("HTTP status " + resp.code() + " " + resp.message());
                    }
                } catch (Exception e1) {
                    LOG.error("Couldn't follow/unfollow model {}", model.getName(), e1);
                    Platform.runLater(() -> {
                        Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
                        alert.setTitle("Error");
                        alert.setHeaderText("Couldn't follow/unfollow model");
                        alert.setContentText("I/O error while following/unfollowing model " + model.getName() + ": " + e1.getLocalizedMessage());
                        alert.showAndWait();
                    });
                } finally {
                    setCursor(Cursor.DEFAULT);
                }
            }
        }.start();
    }

    public Model getModel() {
        return model;
    }

    public void setModel(Model model) {
        this.model = model;
        update();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    private void update() {
        setImage(model.getPreview());
        topic.setText(model.getDescription());
        setRecording(recorder.isRecording(model));
        requestLayout();
        if(Config.getInstance().getSettings().determineResolution) {
            determineResolution();
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((model == null) ? 0 : model.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ThumbCell other = (ThumbCell) obj;
        if (model == null) {
            if (other.model != null)
                return false;
        } else if (!model.equals(other.model))
            return false;
        return true;
    }
}
