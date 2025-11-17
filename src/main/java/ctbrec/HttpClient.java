package ctbrec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squareup.moshi.Moshi;
import com.squareup.moshi.Types;

import ctbrec.ui.CookieJarImpl;
import ctbrec.ui.HtmlParser;
import ctbrec.ui.Launcher;
import okhttp3.ConnectionPool;
import okhttp3.Cookie;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpClient {
    private static final transient Logger LOG = LoggerFactory.getLogger(HttpClient.class);
    private static HttpClient instance = new HttpClient();

    private OkHttpClient client;
    private CookieJarImpl cookieJar = new CookieJarImpl();
    private boolean loggedIn = false;
    private int loginTries = 0;
    private String token;

    private HttpClient() {
        client = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectTimeout(Config.getInstance().getSettings().httpTimeout, TimeUnit.SECONDS)
                .readTimeout(Config.getInstance().getSettings().httpTimeout, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(50, 10, TimeUnit.MINUTES))
                //.addInterceptor(new LoggingInterceptor())
                .build();
        // Attempt to seed cookie jar from user AppData folder to reuse logged-in sessions
        try {
            seedCookiesFromAppData();
        } catch (Throwable t) {
            LOG.debug("cookie seeding skipped: {}", t.getMessage());
        }
    }

    private static void safeSetProperty(String key, String value) {
        if (key == null || value == null) return;
        try {
            System.setProperty(key, value);
        } catch (SecurityException se) {
            LOG.debug("Unable to set system property {}: {}", key, se.getMessage());
        }
    }

    /**
     * Load cookie files from %APPDATA%\ctbrec\*cookies*.json and seed into the in-memory CookieJar.
     * Uses Moshi to parse JSON arrays of cookie objects (name,value,domain,path,expiresAt,secure,httpOnly).
     */
    private void seedCookiesFromAppData() {
        String appdata = System.getenv("APPDATA");
        if (appdata == null) {
            LOG.debug("APPDATA not set; skipping cookie seeding");
            return;
        }
        Path dir = Paths.get(appdata, "ctbrec");
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            LOG.debug("No ctbrec appdata dir: {}", dir.toString());
            return;
        }
        Moshi moshi = new Moshi.Builder().build();
        Type listOfMap = Types.newParameterizedType(List.class, Map.class);
        com.squareup.moshi.JsonAdapter<List<Map<String, Object>>> adapter = moshi.adapter(listOfMap);
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*cookies*.json")) {
            for (Path p : ds) {
                try {
                    String json = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    List<Map<String, Object>> list = adapter.fromJson(json);
                    if (list == null) continue;
                    int count = 0;
                    for (Map<String, Object> m : list) {
                        try {
                            String name = (String) m.get("name");
                            String value = (String) m.get("value");
                            String domain = (String) m.get("domain");
                            String pathS = m.get("path") != null ? (String) m.get("path") : "/";
                            boolean secure = m.get("secure") instanceof Boolean ? (Boolean) m.get("secure") : false;
                            boolean httpOnly = m.get("httpOnly") instanceof Boolean ? (Boolean) m.get("httpOnly") : false;
                            long expiresAt = 0L;
                            Object exo = m.get("expiresAt");
                            if (exo instanceof Number) {
                                expiresAt = ((Number) exo).longValue();
                            } else if (exo instanceof String) {
                                try { expiresAt = Long.parseLong((String)exo); } catch(Exception e) { }
                            }
                            Cookie.Builder cb = new Cookie.Builder()
                                    .name(name)
                                    .value(value)
                                    .path(pathS);
                            if (domain != null) cb.domain(domain);
                            if (secure) cb.secure();
                            if (httpOnly) cb.httpOnly();
                            if (expiresAt > 0) cb.expiresAt(expiresAt);
                            Cookie cookie = cb.build();
                            cookieJar.addCookie(cookie);
                            count++;
                        } catch (Exception e) {
                            LOG.debug("failed to parse cookie entry in {}: {}", p, e.getMessage());
                        }
                    }
                    LOG.info("Loaded {} cookies from {}", count, p.toString());
                } catch (Exception e) {
                    LOG.debug("failed to read cookie file {}: {}", p, e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.debug("cookie seeding failed: {}", e.getMessage());
        }
    }

    public static HttpClient getInstance() {
        return instance;
    }

    public Response execute(Request request) throws IOException {
        Response resp = execute(request, false);
        return resp;
    }

    private void extractCsrfToken(Request request) {
        try {
            Cookie csrfToken = cookieJar.getCookie(request.url(), "csrftoken");
            token = csrfToken.value();
        } catch(NoSuchElementException e) {
            LOG.trace("CSRF token not found in cookies");
        }
    }

    public Response execute(Request req, boolean requiresLogin) throws IOException {
        if(requiresLogin && !loggedIn) {
            boolean loginSuccessful = login();
            if(!loginSuccessful) {
                throw new IOException("403 Unauthorized");
            }
        }
        Response resp = client.newCall(req).execute();
        extractCsrfToken(req);
        return resp;
    }

    public boolean login() throws IOException {
        try {
            Request login = new Request.Builder()
                    .url(Launcher.BASE_URI + "/auth/login/")
                    .build();
            Response response = client.newCall(login).execute();
            String content = response.body().string();
            token = HtmlParser.getTag(content, "input[name=csrfmiddlewaretoken]").attr("value");
            LOG.debug("csrf token is {}", token);

            RequestBody body = new FormBody.Builder()
                    .add("username", Config.getInstance().getSettings().username)
                    .add("password", Config.getInstance().getSettings().password)
                    .add("next", "")
                    .add("csrfmiddlewaretoken", token)
                    .build();
            login = new Request.Builder()
                    .url(Launcher.BASE_URI + "/auth/login/")
                    .header("Referer", Launcher.BASE_URI + "/auth/login/")
                    .post(body)
                    .build();

            response = client.newCall(login).execute();
            if(response.isSuccessful()) {
                content = response.body().string();
                if(content.contains("Login, Chaturbate login")) {
                    loggedIn = false;
                } else {
                    loggedIn = true;
                    extractCsrfToken(login);
                }
            } else {
                if(loginTries++ < 3) {
                    login();
                } else {
                    throw new IOException("Login failed: " + response.code() + " " + response.message());
                }
            }
            response.close();
        } finally {
            loginTries = 0;
        }
        return loggedIn;
    }

    public String getToken() throws IOException {
        if(token == null) {
            login();
        }
        return token;
    }

    public void shutdown() {
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
    }
}
