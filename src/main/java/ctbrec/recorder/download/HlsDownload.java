package ctbrec.recorder.download;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iheartradio.m3u8.Encoding;
import com.iheartradio.m3u8.Format;
import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import com.iheartradio.m3u8.PlaylistParser;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.MediaPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.TrackData;

import ctbrec.Config;
import ctbrec.HttpClient;
import ctbrec.Model;
import ctbrec.recorder.Chaturbate;
import ctbrec.recorder.StreamInfo;

public class HlsDownload implements Download {

    private static final transient Logger LOG = LoggerFactory.getLogger(HlsDownload.class);
    private HttpClient client;
    private ExecutorService threadPool = Executors.newFixedThreadPool(5);
    private volatile boolean running = false;
    private volatile boolean alive = true;
    private Path downloadDir;

    public HlsDownload(HttpClient client) {
        this.client = client;
    }

    @Override
    public void start(Model model, Config config) throws IOException {
        try {
            running = true;
            StreamInfo streamInfo = Chaturbate.getStreamInfo(model, client);
            if(!Objects.equals(streamInfo.room_status, "public")) {
                throw new IOException(model.getName() +"'s room is not public");
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
            String startTime = sdf.format(new Date());
            Path modelDir = FileSystems.getDefault().getPath(config.getSettings().recordingsDir, model.getName());
            downloadDir = FileSystems.getDefault().getPath(modelDir.toString(), startTime);
            if (!Files.exists(downloadDir, LinkOption.NOFOLLOW_LINKS)) {
                Files.createDirectories(downloadDir);
            }

            String segments = parseMaster(streamInfo.url);
            if(segments != null) {
                int lastSegment = 0;
                int nextSegment = 0;
                while(running) {
                    LiveStreamingPlaylist lsp = parseSegments(segments);
                    if(nextSegment > 0 && lsp.seq > nextSegment) {
                        LOG.warn("Missed segments {} < {} in download for {}", nextSegment, lsp.seq, model);
                        String first = lsp.segments.get(0);
                        int seq = lsp.seq;
                        for (int i = nextSegment; i < lsp.seq; i++) {
                            URL segmentUrl = new URL(first.replaceAll(Integer.toString(seq), Integer.toString(i)));
                            LOG.debug("Reloading segment {} for model {}", i, model.getName());
                            threadPool.submit(new SegmentDownload(segmentUrl, downloadDir));
                        }
                        // TODO switch to a lower bitrate/resolution ?!?
                    }
                    int skip = nextSegment - lsp.seq;
                    for (String segment : lsp.segments) {
                        if(skip > 0) {
                            skip--;
                        } else {
                            URL segmentUrl = new URL(segment);
                            threadPool.submit(new SegmentDownload(segmentUrl, downloadDir));
                            //new SegmentDownload(segment, downloadDir).call();
                        }
                    }

                    long wait = 0;
                    if(lastSegment == lsp.seq) {
                        // playlist didn't change -> wait for at least half the target duration
                        wait = (long) lsp.targetDuration * 1000 / 2;
                        LOG.trace("Playlist didn't change... waiting for {}ms", wait);
                    } else {
                        // playlist did change -> wait for at least last segment duration
                        wait = 1;//(long) lsp.lastSegDuration * 1000;
                        LOG.trace("Playlist changed... waiting for {}ms", wait);
                    }

                    try {
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        if(running) {
                            LOG.error("Couldn't sleep between segment downloads. This might mess up the download!");
                        }
                    }

                    lastSegment = lsp.seq;
                    nextSegment = lastSegment + lsp.segments.size();
                }
            } else {
                throw new IOException("Couldn't determine segments uri");
            }
        } catch(ParseException e) {
            throw new IOException("Couldn't parse stream information", e);
        } catch(PlaylistException e) {
            throw new IOException("Couldn't parse HLS playlist", e);
        } catch(Exception e) {
            throw new IOException("Couldn't download segment", e);
        } finally {
            alive = false;
            LOG.debug("Download for {} terminated", model);
        }
    }

    @Override
    public void stop() {
        running = false;
        alive = false;
    }

    private LiveStreamingPlaylist parseSegments(String segments) throws IOException, ParseException, PlaylistException {
        URL segmentsUrl = new URL(segments);
        InputStream inputStream = segmentsUrl.openStream();
        PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8);
        Playlist playlist = parser.parse();
        if(playlist.hasMediaPlaylist()) {
            MediaPlaylist mediaPlaylist = playlist.getMediaPlaylist();
            LiveStreamingPlaylist lsp = new LiveStreamingPlaylist();
            lsp.seq = mediaPlaylist.getMediaSequenceNumber();
            lsp.targetDuration = mediaPlaylist.getTargetDuration();
            List<TrackData> tracks = mediaPlaylist.getTracks();
            for (TrackData trackData : tracks) {
                String uri = trackData.getUri();
                if(!uri.startsWith("http")) {
                    String _url = segmentsUrl.toString();
                    _url = _url.substring(0, _url.lastIndexOf('/') + 1);
                    String segmentUri = _url + uri;
                    lsp.totalDuration += trackData.getTrackInfo().duration;
                    lsp.lastSegDuration = trackData.getTrackInfo().duration;
                    lsp.segments.add(segmentUri);
                }
            }
            return lsp;
        }
        return null;
    }

    private String parseMaster(String url) throws IOException, ParseException, PlaylistException {
        URL masterUrl = new URL(url);
        InputStream inputStream = masterUrl.openStream();
        PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8);
        Playlist playlist = parser.parse();
        if(playlist.hasMasterPlaylist()) {
            MasterPlaylist master = playlist.getMasterPlaylist();
            PlaylistData bestQuality = master.getPlaylists().get(master.getPlaylists().size()-1);
            String uri = bestQuality.getUri();
            if(!uri.startsWith("http")) {
                String _masterUrl = masterUrl.toString();
                _masterUrl = _masterUrl.substring(0, _masterUrl.lastIndexOf('/') + 1);
                String segmentUri = _masterUrl + uri;
                return segmentUri;
            }
        }
        return null;
    }

    public static class LiveStreamingPlaylist {
        public int seq = 0;
        public float totalDuration = 0;
        public float lastSegDuration = 0;
        public float targetDuration = 0;
        public List<String> segments = new ArrayList<>();
    }

    private static class SegmentDownload implements Callable<Boolean> {
        private URL url;
        private Path file;

        public SegmentDownload(URL url, Path dir) {
            this.url = url;
            File path = new File(url.getPath());
            file = FileSystems.getDefault().getPath(dir.toString(), path.getName());
        }

        @Override
        public Boolean call() throws Exception {
            LOG.trace("Downloading segment to " + file);
            for (int i = 0; i < 3; i++) {
                try( FileOutputStream fos = new FileOutputStream(file.toFile());
                        InputStream in = url.openStream())
                {
                    byte[] b = new byte[1024 * 100];
                    int length = -1;
                    while( (length = in.read(b)) >= 0 ) {
                        fos.write(b, 0, length);
                    }
                    return true;
                } catch(FileNotFoundException e) {
                    LOG.debug("Segment does not exist {}", url.getFile());
                    break;
                } catch(Exception e) {
                    LOG.error("Error while downloading segment. Retrying " + i, e);
                }
            }
            return false;
        }
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public File getDirectory() {
        return downloadDir.toFile();
    }
}
