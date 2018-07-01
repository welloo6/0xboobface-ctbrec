package ctbrec.recorder;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.Moshi;

import ctbrec.HttpClient;
import ctbrec.Model;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;

public class Chaturbate {
    private static final transient Logger LOG = LoggerFactory.getLogger(Chaturbate.class);

    public static StreamInfo getStreamInfo(Model model, HttpClient client) throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("room_slug", model.getName())
                .add("bandwidth", "high")
                .build();
        Request req = new Request.Builder()
                .url("https://chaturbate.com/get_edge_hls_url_ajax/")
                .post(body)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build();
        String content = client.execute(req).body().string();
        LOG.debug("Raw stream info: {}", content);
        Moshi moshi = new Moshi.Builder().build();
        JsonAdapter<StreamInfo> adapter = moshi.adapter(StreamInfo.class);
        StreamInfo streamInfo = adapter.fromJson(content);
        return streamInfo;
    }
}
