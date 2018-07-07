package ctbrec.recorder.server;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import ctbrec.Config;
import ctbrec.Hmac;
import ctbrec.InstantJsonAdapter;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.recorder.Recorder;

public class RecorderServlet extends HttpServlet {

    private static final transient Logger LOG = LoggerFactory.getLogger(RecorderServlet.class);

    private Recorder recorder;

    public RecorderServlet(Recorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setStatus(SC_OK);
        resp.setContentType("application/json");

        try {
            String json = body(req);
            boolean isRequestAuthenticated = checkAuthentication(req, json);
            if(!isRequestAuthenticated) {
                resp.setStatus(SC_UNAUTHORIZED);
                String response = "{\"status\": \"error\", \"msg\": \"HMAC does not match\"}";
                resp.getWriter().write(response);
                return;
            }

            LOG.debug("Request: {}", json);
            Moshi moshi = new Moshi.Builder()
                    .add(Instant.class, new InstantJsonAdapter())
                    .build();
            JsonAdapter<Request> requestAdapter = moshi.adapter(Request.class);
            Request request = requestAdapter.fromJson(json);
            if(request.action != null) {
                switch (request.action) {
                case "start":
                    LOG.debug("Starting recording for model {} - {}", request.model.getName(), request.model.getUrl());
                    recorder.startRecording(request.model);
                    String response = "{\"status\": \"success\", \"msg\": \"Recording started\"}";
                    resp.getWriter().write(response);
                    break;
                case "stop":
                    response = "{\"status\": \"success\", \"msg\": \"Recording stopped\"}";
                    recorder.stopRecording(request.model);
                    resp.getWriter().write(response);
                    break;
                case "list":
                    resp.getWriter().write("{\"status\": \"success\", \"msg\": \"List of models\", \"models\": [");
                    JsonAdapter<Model> modelAdapter = moshi.adapter(Model.class);
                    List<Model> models = recorder.getModelsRecording();
                    for (Iterator<Model> iterator = models.iterator(); iterator.hasNext();) {
                        Model model = iterator.next();
                        resp.getWriter().write(modelAdapter.toJson(model));
                        if(iterator.hasNext()) {
                            resp.getWriter().write(',');
                        }
                    }
                    resp.getWriter().write("]}");
                    break;
                case "recordings":
                    resp.getWriter().write("{\"status\": \"success\", \"msg\": \"List of recordings\", \"recordings\": [");
                    JsonAdapter<Recording> recAdapter = moshi.adapter(Recording.class);
                    List<Recording> recordings = recorder.getRecordings();
                    for (Iterator<Recording> iterator = recordings.iterator(); iterator.hasNext();) {
                        Recording recording = iterator.next();
                        resp.getWriter().write(recAdapter.toJson(recording));
                        if (iterator.hasNext()) {
                            resp.getWriter().write(',');
                        }
                    }
                    resp.getWriter().write("]}");
                    break;
                case "delete":
                    String path = request.recording;
                    Recording rec = new Recording(path);
                    recorder.delete(rec);
                    recAdapter = moshi.adapter(Recording.class);
                    resp.getWriter().write("{\"status\": \"success\", \"msg\": \"List of recordings\", \"recordings\": [");
                    resp.getWriter().write(recAdapter.toJson(rec));
                    resp.getWriter().write("]}");
                    break;
                default:
                    resp.setStatus(SC_BAD_REQUEST);
                    response = "{\"status\": \"error\", \"msg\": \"Unknown action\"}";
                    resp.getWriter().write(response);
                    break;
                }
            } else {
                resp.setStatus(SC_BAD_REQUEST);
                String response = "{\"status\": \"error\", \"msg\": \"action is missing\"}";
                resp.getWriter().write(response);
            }
        } catch(Throwable t) {
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
            String response = "{\"status\": \"error\", \"msg\": \"An unexpected error occured\"}";
            resp.getWriter().write(response);
            LOG.error("Unexpected error", t);
        }
    }

    private boolean checkAuthentication(HttpServletRequest req, String body) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException {
        boolean authenticated = false;
        if(Config.getInstance().getSettings().key != null) {
            if(req.getHeader("CTBREC-HMAC") == null) {
                authenticated = false;
            }

            byte[] key = Config.getInstance().getSettings().key;
            authenticated = Hmac.validate(body, key, req.getHeader("CTBREC-HMAC"));
        } else {
            authenticated = true;
        }
        return authenticated;
    }

    private String body(HttpServletRequest req) throws IOException {
        StringBuilder body = new StringBuilder();
        BufferedReader br = req.getReader();
        String line= null;
        while( (line = br.readLine()) != null ) {
            body.append(line).append("\n");
        }
        return body.toString().trim();
    }

    private static class Request {
        public String action;
        public Model model;
        public String recording;
    }
}
