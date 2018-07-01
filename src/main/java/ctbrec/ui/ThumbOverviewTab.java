package ctbrec.ui;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.HttpClient;
import ctbrec.Model;
import ctbrec.ModelParser;
import ctbrec.recorder.Recorder;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.Worker.State;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import okhttp3.Request;
import okhttp3.Response;

public class ThumbOverviewTab extends Tab implements TabSelectionListener {
    private static final transient Logger LOG = LoggerFactory.getLogger(ThumbOverviewTab.class);

    ScheduledService<List<Model>> updateService;
    Recorder recorder;
    List<ThumbCell> filteredThumbCells = Collections.synchronizedList(new ArrayList<>());
    String filter;
    FlowPane grid = new FlowPane();
    ReentrantLock gridLock = new ReentrantLock();
    ScrollPane scrollPane = new ScrollPane();
    String url;
    boolean loginRequired;
    HttpClient client = HttpClient.getInstance();
    int page = 1;
    TextField pageInput = new TextField(Integer.toString(page));
    Button pagePrev = new Button("◀");
    Button pageNext = new Button("▶");
    private volatile boolean updatesSuspended = false;

    public ThumbOverviewTab(String title, String url, boolean loginRequired) {
        super(title);
        this.url = url;
        this.loginRequired = loginRequired;
        setClosable(false);
        createGui();
        initializeUpdateService();
    }

    private void createGui() {
        grid.setPadding(new Insets(5));
        grid.setHgap(5);
        grid.setVgap(5);

        TextField search = new TextField();
        search.setPromptText("Filter");
        search.textProperty().addListener( (observableValue, oldValue, newValue) -> {
            filter = search.getText();
            filter();
        });
        BorderPane.setMargin(search, new Insets(5));

        scrollPane.setContent(grid);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        BorderPane.setMargin(scrollPane, new Insets(5));

        HBox pagination = new HBox(5);
        pagination.getChildren().add(pagePrev);
        pagination.getChildren().add(pageNext);
        pagination.getChildren().add(pageInput);
        BorderPane.setMargin(pagination, new Insets(5));
        pageInput.setPrefWidth(50);
        pageInput.setOnAction((e) -> handlePageNumberInput());
        pagePrev.setOnAction((e) -> {
            page = Math.max(1, --page);
            pageInput.setText(Integer.toString(page));
            restartUpdateService();
        });
        pageNext.setOnAction((e) -> {
            page++;
            pageInput.setText(Integer.toString(page));
            restartUpdateService();
        });

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(5));
        root.setTop(search);
        root.setCenter(scrollPane);
        root.setBottom(pagination);
        setContent(root);
    }

    private void handlePageNumberInput() {
        try {
            page = Integer.parseInt(pageInput.getText());
            page = Math.max(1, page);
            restartUpdateService();
        } catch(NumberFormatException e) {
        } finally {
            pageInput.setText(Integer.toString(page));
        }
    }

    private void restartUpdateService() {
        gridLock.lock();
        try {
            grid.getChildren().clear();
            filteredThumbCells.clear();
            deselected();
            selected();
        } finally {
            gridLock.unlock();
        }
    }

    void initializeUpdateService() {
        updateService = createUpdateService();
        updateService.setPeriod(new Duration(TimeUnit.SECONDS.toMillis(10)));
        updateService.setOnSucceeded((event) -> onSuccess());
        updateService.setOnFailed((event) -> onFail(event));
    }

    protected void onSuccess() {
        if(updatesSuspended) {
            return;
        }
        gridLock.lock();
        try {
            List<Model> models = updateService.getValue();
            ObservableList<Node> nodes = grid.getChildren();

            // first remove models, which are not in the updated list
            for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
                Node node = iterator.next();
                if (!(node instanceof ThumbCell)) continue;
                ThumbCell cell = (ThumbCell) node;
                if(!models.contains(cell.getModel())) {
                    iterator.remove();
                }
            }

            List<ThumbCell> positionChangedOrNew = new ArrayList<>();
            int index = 0;
            for (Model model : models) {
                boolean found = false;
                for (Iterator<Node> iterator = nodes.iterator(); iterator.hasNext();) {
                    Node node = iterator.next();
                    if (!(node instanceof ThumbCell)) continue;
                    ThumbCell cell = (ThumbCell) node;
                    if(cell.getModel().equals(model)) {
                        found = true;
                        cell.setModel(model);
                        if(index != cell.getIndex()) {
                            cell.setIndex(index);
                            positionChangedOrNew.add(cell);
                        }
                    }
                }
                if(!found) {
                    ThumbCell newCell = new ThumbCell(this, model, recorder, client);
                    newCell.setIndex(index);
                    positionChangedOrNew.add(newCell);
                }
                index++;
            }
            for (ThumbCell thumbCell : positionChangedOrNew) {
                nodes.remove(thumbCell);
                if(thumbCell.getIndex() < nodes.size()) {
                    nodes.add(thumbCell.getIndex(), thumbCell);
                } else {
                    nodes.add(thumbCell);
                }
            }
        } finally {
            gridLock.unlock();
        }

        filter();
    }

    protected void onFail(WorkerStateEvent event) {
        if(updatesSuspended) {
            return;
        }
        Alert alert = new AutosizeAlert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Couldn't fetch model list");
        if(event.getSource().getException() != null) {
            alert.setContentText(event.getSource().getException().getLocalizedMessage());
        } else {
            alert.setContentText(event.getEventType().toString());
        }
        alert.showAndWait();
    }

    private void filter() {
        Collections.sort(filteredThumbCells, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                ThumbCell c1 = (ThumbCell) o1;
                ThumbCell c2 = (ThumbCell) o2;

                if(c1.getIndex() < c2.getIndex()) return -1;
                if(c1.getIndex() > c2.getIndex()) return 1;
                return c1.getModel().getName().compareTo(c2.getModel().getName());
            }
        });


        gridLock.lock();
        try {
            if (filter == null || filter.isEmpty()) {
                for (ThumbCell thumbCell : filteredThumbCells) {
                    insert(thumbCell);
                }
                moveActiveRecordingsToFront();
                return;
            }

            // remove the ones from grid, which don't match
            for (Iterator<Node> iterator = grid.getChildren().iterator(); iterator.hasNext();) {
                Node node = iterator.next();
                ThumbCell cell = (ThumbCell) node;
                Model m = cell.getModel();
                if(!matches(m, filter)) {
                    iterator.remove();
                    filteredThumbCells.add(cell);
                }
            }

            // add the ones, which might have been filtered before, but now match
            for (Iterator<ThumbCell> iterator = filteredThumbCells.iterator(); iterator.hasNext();) {
                ThumbCell thumbCell = iterator.next();
                Model m = thumbCell.getModel();
                if(matches(m, filter)) {
                    iterator.remove();
                    insert(thumbCell);
                }
            }

            moveActiveRecordingsToFront();
        } finally {
            gridLock.unlock();
        }
    }

    private void moveActiveRecordingsToFront() {
        // move active recordings to the front
        ObservableList<Node> thumbs = grid.getChildren();
        for (int i = thumbs.size()-1; i > 0; i--) {
            ThumbCell thumb = (ThumbCell) thumbs.get(i);
            if(recorder.isRecording(thumb.getModel())) {
                thumbs.remove(i);
                thumbs.add(0, thumb);
            }
        }
    }

    private void insert(ThumbCell thumbCell) {
        if(grid.getChildren().contains(thumbCell)) {
            return;
        }

        if(thumbCell.getIndex() < grid.getChildren().size()-1) {
            grid.getChildren().add(thumbCell.getIndex(), thumbCell);
        } else {
            grid.getChildren().add(thumbCell);
        }
    }

    private boolean matches(Model m, String filter) {
        String[] tokens = filter.split(" ");
        StringBuilder searchTextBuilder = new StringBuilder(m.getName());
        searchTextBuilder.append(' ');
        for (String tag : m.getTags()) {
            searchTextBuilder.append(tag).append(' ');
        }
        String searchText = searchTextBuilder.toString().trim();
        boolean tokensMissing = false;
        for (String token : tokens) {
            if(!searchText.contains(token)) {
                tokensMissing = true;
            }
        }
        return !tokensMissing;
    }

    private ScheduledService<List<Model>> createUpdateService() {
        ScheduledService<List<Model>> updateService = new ScheduledService<List<Model>>() {
            @Override
            protected Task<List<Model>> createTask() {
                return new Task<List<Model>>() {
                    @Override
                    public List<Model> call() throws IOException {
                        String url = ThumbOverviewTab.this.url + "?page="+page+"&keywords=&_=" + System.currentTimeMillis();
                        LOG.debug("Fetching page {}", url);
                        Request request = new Request.Builder().url(url).build();
                        Response response = client.execute(request, loginRequired);
                        if (response.isSuccessful()) {
                            List<Model> models = ModelParser.parseModels(response.body().string());
                            response.close();
                            return models;
                        } else {
                            int code = response.code();
                            response.close();
                            throw new IOException("HTTP status " + code);
                        }
                    }
                };
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("ThumbOverviewTab UpdateService");
                return t;
            }
        });
        updateService.setExecutor(executor);
        return updateService;
    }

    public void setRecorder(Recorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void selected() {
        if(updateService != null) {
            State s = updateService.getState();
            if (s != State.SCHEDULED && s != State.RUNNING) {
                updateService.reset();
                updateService.restart();
            }
        }
    }

    @Override
    public void deselected() {
        if(updateService != null) {
            updateService.cancel();
        }
    }

    void suspendUpdates(boolean suspend) {
        this.updatesSuspended = suspend;
    }
}
