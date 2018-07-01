package ctbrec.recorder;

import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamRedirectThread implements Runnable {
    private static final transient Logger LOG = LoggerFactory.getLogger(StreamRedirectThread.class);

    private InputStream in;
    private OutputStream out;

    public StreamRedirectThread(InputStream in, OutputStream out) {
        super();
        this.in = in;
        this.out = out;
    }

    @Override
    public void run() {
        try {
            int length = -1;
            byte[] buffer = new byte[1024*1024];
            while(in != null && (length = in.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
            LOG.debug("Stream redirect thread ended");
        } catch(Exception e) {
            LOG.error("Couldn't redirect stream: {}", e.getLocalizedMessage());
        }
    }
}
