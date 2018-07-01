package ctbrec.recorder.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;

import ctbrec.Config;

public class HlsServlet extends HttpServlet {

    private static final transient Logger LOG = LoggerFactory.getLogger(HlsServlet.class);

    private Config config;

    public HlsServlet(Config config) {
        this.config = config;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String request = req.getRequestURI().substring(5);
        File recordingsDir = new File(config.getSettings().recordingsDir);
        File requestedFile = new File(recordingsDir, request);

        if (requestedFile.getCanonicalPath().startsWith(config.getSettings().recordingsDir)) {
            if (requestedFile.getName().equals("playlist.m3u8")) {
                try {
                    servePlaylist(req, resp, requestedFile);
                } catch (ParseException | PlaylistException e) {
                    LOG.error("Error while generating playlist file", e);
                    throw new IOException("Couldn't generate playlist file " + requestedFile, e);
                }
            } else {
                if (requestedFile.exists()) {
                    serveSegment(req, resp, requestedFile);
                } else {
                    error404(req, resp);
                }
            }
        } else {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            resp.getWriter().println("Stop it!");
        }
    }

    private void error404(HttpServletRequest req, HttpServletResponse resp) {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void serveSegment(HttpServletRequest req, HttpServletResponse resp, File requestedFile) throws FileNotFoundException, IOException {
        serveFile(resp, requestedFile, "application/octet-stream");
    }

    private void servePlaylist(HttpServletRequest req, HttpServletResponse resp, File requestedFile) throws FileNotFoundException, IOException, ParseException, PlaylistException {
        serveFile(resp, requestedFile, "application/x-mpegURL");
    }

    private void serveFile(HttpServletResponse resp, File file, String contentType) throws FileNotFoundException, IOException {
        LOG.trace("Serving segment {}", file.getAbsolutePath());
        resp.setStatus(200);
        resp.setContentLength((int) file.length());
        resp.setContentType(contentType);
        try(FileInputStream fin = new FileInputStream(file)) {
            byte[] buffer = new byte[1024 * 100];
            int length = -1;
            while( (length = fin.read(buffer)) >= 0) {
                resp.getOutputStream().write(buffer, 0, length);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }
}
