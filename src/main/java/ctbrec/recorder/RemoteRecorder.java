package ctbrec.recorder;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import ctbrec.Config;
import ctbrec.Hmac;
import ctbrec.HttpClient;
import ctbrec.InstantJsonAdapter;
import ctbrec.Model;
import ctbrec.Recording;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RemoteRecorder implements Recorder {

    private static final transient Logger LOG = LoggerFactory.getLogger(RemoteRecorder.class);

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private Moshi moshi = new Moshi.Builder()
            .add(Instant.class, new InstantJsonAdapter())
            .build();
    private JsonAdapter<ModelListResponse> modelListResponseAdapter = moshi.adapter(ModelListResponse.class);
    private JsonAdapter<RecordingListResponse> recordingListResponseAdapter = moshi.adapter(RecordingListResponse.class);
    private JsonAdapter<Model> modelAdapter = moshi.adapter(Model.class);

    private List<Model> models = Collections.emptyList();

    private Config config;
    private HttpClient client;
    private Instant lastSync = Instant.EPOCH;
    private SyncThread syncThread;

    public RemoteRecorder(Config config, HttpClient client) {
        this.config = config;
        this.client = client;

        syncThread = new SyncThread();
        syncThread.start();
    }

    @Override
    public void startRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        sendRequest("start", model);
    }

    @Override
    public void stopRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        sendRequest("stop", model);
    }

    private void sendRequest(String action, Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        String requestTemplate = "{\"action\": \"<<action>>\", \"model\": <<model>>}";
        requestTemplate = requestTemplate.replaceAll("<<action>>", action);
        requestTemplate = requestTemplate.replaceAll("<<model>>", modelAdapter.toJson(model));
        LOG.debug("Sending request to recording server: {}", requestTemplate);
        RequestBody body = RequestBody.create(JSON, requestTemplate);
        Request.Builder builder = new Request.Builder()
                .url("http://" + config.getSettings().httpServer + ":" + config.getSettings().httpPort + "/rec")
                .post(body);
        addHmacIfNeeded(requestTemplate, builder);
        Request request = builder.build();
        Response response = client.execute(request);
        String json = response.body().string();
        if(response.isSuccessful()) {
            ModelListResponse resp = modelListResponseAdapter.fromJson(json);
            if(!resp.status.equals("success")) {
                throw new IOException("Server returned error " + resp.status + " " + resp.msg);
            }

            if("start".equals(action)) {
                models.add(model);
            } else {
                models.remove(model);
            }
        } else {
            throw new IOException("Server returned error. HTTP status: " + response.code());
        }
    }

    private void addHmacIfNeeded(String msg, Builder builder) throws InvalidKeyException, NoSuchAlgorithmException, IllegalStateException, UnsupportedEncodingException {
        if(Config.getInstance().getSettings().requireAuthentication) {
            byte[] key = Config.getInstance().getSettings().key;
            String hmac = Hmac.calculate(msg, key);
            builder.addHeader("CTBREC-HMAC", hmac);
        }
    }

    @Override
    public boolean isRecording(Model model) {
        return models != null && models.contains(model);
    }

    @Override
    public List<Model> getModelsRecording() {
        if(lastSync.isBefore(Instant.now().minusSeconds(60))) {
            throw new RuntimeException("Last sync was over a minute ago");
        }
        return models;
    }

    @Override
    public void shutdown() {
        syncThread.stopThread();
    }

    private class SyncThread extends Thread {
        private volatile boolean running = false;

        public SyncThread() {
            setName("RemoteRecorder SyncThread");
            setDaemon(true);
        }

        @Override
        public void run() {
            running = true;
            while(running) {
                try {
                    String msg = "{\"action\": \"list\"}";
                    RequestBody body = RequestBody.create(JSON, msg);
                    Request.Builder builder = new Request.Builder()
                            .url("http://" + config.getSettings().httpServer + ":" + config.getSettings().httpPort + "/rec")
                            .post(body);
                    addHmacIfNeeded(msg, builder);
                    Request request = builder.build();
                    Response response = client.execute(request);
                    String json = response.body().string();
                    if(response.isSuccessful()) {
                        ModelListResponse resp = modelListResponseAdapter.fromJson(json);
                        if(resp.status.equals("success")) {
                            models = resp.models;
                            lastSync = Instant.now();
                        } else {
                            LOG.error("Server returned error: {} - {}", resp.status, resp.msg);
                        }
                    } else {
                        LOG.error("Couldn't synchronize with server. HTTP status: {} - {}", response.code(), json);
                    }
                } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
                    LOG.error("Couldn't synchronize with server", e);
                }

                sleep();
            }
        }

        private void sleep() {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                // interrupted, probably by stopThread
            }
        }

        public void stopThread() {
            running = false;
            interrupt();
        }
    }

    private static class ModelListResponse {
        public String status;
        public String msg;
        public List<Model> models;
    }

    private static class RecordingListResponse {
        public String status;
        public String msg;
        public List<Recording> recordings;
    }

    @Override
    public List<Recording> getRecordings() throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        String msg = "{\"action\": \"recordings\"}";
        RequestBody body = RequestBody.create(JSON, msg);
        Request.Builder builder = new Request.Builder()
                .url("http://" + config.getSettings().httpServer + ":" + config.getSettings().httpPort + "/rec")
                .post(body);
        addHmacIfNeeded(msg, builder);
        Request request = builder.build();
        Response response = client.execute(request);
        String json = response.body().string();
        if(response.isSuccessful()) {
            LOG.debug(json);
            RecordingListResponse resp = recordingListResponseAdapter.fromJson(json);
            if(resp.status.equals("success")) {
                List<Recording> recordings = resp.recordings;
                return recordings;
            } else {
                LOG.error("Server returned error: {} - {}", resp.status, resp.msg);
            }
        } else {
            LOG.error("Couldn't synchronize with server. HTTP status: {} - {}", response.code(), json);
        }

        return Collections.emptyList();
    }

    @Override
    public void delete(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        String msg = "{\"action\": \"delete\", \"recording\": \""+recording.getPath()+"\"}";
        RequestBody body = RequestBody.create(JSON, msg);
        Request.Builder builder = new Request.Builder()
                .url("http://" + config.getSettings().httpServer + ":" + config.getSettings().httpPort + "/rec")
                .post(body);
        addHmacIfNeeded(msg, builder);
        Request request = builder.build();
        Response response = client.execute(request);
        String json = response.body().string();
        RecordingListResponse resp = recordingListResponseAdapter.fromJson(json);
        if(response.isSuccessful()) {
            if(!resp.status.equals("success")) {
                throw new IOException("Couldn't delete recording: " + resp.msg);
            }
        } else {
            throw new IOException("Couldn't delete recording: " + resp.msg);
            //throw new IOException("Couldn't delete recording: " + response.code() + " " + json);
        }
    }
}
