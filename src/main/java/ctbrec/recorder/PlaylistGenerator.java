package ctbrec.recorder.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jcodec.common.Demuxer;
import org.jcodec.common.DemuxerTrack;
import org.jcodec.common.TrackType;
import org.jcodec.common.Tuple;
import org.jcodec.common.Tuple._2;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Packet;
import org.jcodec.containers.mps.MPSDemuxer;
import org.jcodec.containers.mps.MTSDemuxer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iheartradio.m3u8.Encoding;
import com.iheartradio.m3u8.Format;
import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import com.iheartradio.m3u8.PlaylistParser;
import com.iheartradio.m3u8.PlaylistWriter;
import com.iheartradio.m3u8.data.MediaPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistType;
import com.iheartradio.m3u8.data.TrackData;
import com.iheartradio.m3u8.data.TrackInfo;


public class PlaylistGenerator {
    private static final transient Logger LOG = LoggerFactory.getLogger(PlaylistGenerator.class);

    private int lastPercentage;
    private List<ProgressListener> listeners = new ArrayList<>();

    public void generate(File directory) throws IOException, ParseException, PlaylistException {
        LOG.debug("Starting playlist generation for {}", directory);
        // get a list of all ts files and sort them by sequence
        File[] files = directory.listFiles((f) -> f.getName().endsWith(".ts"));
        Arrays.sort(files, (f1, f2) -> {
            String n1 = f1.getName();
            n1 = n1.substring(0, n1.length()-3);
            int seq1 = Integer.parseInt(n1.substring(n1.lastIndexOf('_')+1));

            String n2 = f2.getName();
            n2 = n2.substring(0, n2.length()-3);
            int seq2 = Integer.parseInt(n2.substring(n2.lastIndexOf('_')+1));

            if(seq1 < seq2) return -1;
            if(seq1 > seq2) return 1;
            return 0;
        });

        // create a track containing all files
        List<TrackData> track = new ArrayList<>();
        int total = files.length;
        int done = 0;
        for (File file : files) {
            try {
                track.add(new TrackData.Builder()
                        .withUri(file.getName())
                        .withTrackInfo(new TrackInfo((float) getFileDuration(file), file.getName()))
                        .build());
            } catch(Exception e) {
                LOG.warn("Couldn't determine duration for {}. Skipping this file.", file.getName());
                file.renameTo(new File(directory, file.getName()+".corrupt"));
            }
            done++;
            double percentage = (double)done / (double) total;
            updateProgressListeners(percentage);
        }

        // create a media playlist
        float targetDuration = getAvgDuration(track);
        MediaPlaylist playlist = new MediaPlaylist.Builder()
                .withPlaylistType(PlaylistType.VOD)
                .withMediaSequenceNumber(0)
                .withTargetDuration((int) targetDuration)
                .withTracks(track).build();

        // create a master playlist containing the media playlist
        Playlist master = new Playlist.Builder()
                .withCompatibilityVersion(4)
                .withExtended(true)
                .withMediaPlaylist(playlist)
                .build();

        // write the playlist to a file
        File output = new File(directory, "playlist.m3u8");
        try(FileOutputStream fos = new FileOutputStream(output)) {
            PlaylistWriter writer = new PlaylistWriter.Builder()
                    .withFormat(Format.EXT_M3U)
                    .withEncoding(Encoding.UTF_8)
                    .withOutputStream(fos)
                    .build();
            writer.write(master);
            LOG.debug("Finished playlist generation for {}", directory);
        }
    }

    private void updateProgressListeners(double percentage) {
        int p = (int) (percentage*100);
        if(p > lastPercentage) {
            for (ProgressListener progressListener : listeners) {
                progressListener.update(p);
            }
            lastPercentage = p;
        }
    }

    private float getAvgDuration(List<TrackData> track) {
        float targetDuration = 0;
        for (TrackData trackData : track) {
            targetDuration += trackData.getTrackInfo().duration;
        }
        targetDuration /= track.size();
        return targetDuration;
    }

    private double getFileDuration(File file) throws IOException {
        try(FileChannelWrapper ch = NIOUtils.readableChannel(file)) {
            _2<Integer,Demuxer> m2tsDemuxer = createM2TSDemuxer(ch, TrackType.VIDEO);
            Demuxer demuxer = m2tsDemuxer.v1;
            DemuxerTrack videoDemux = demuxer.getTracks().get(0);
            Packet videoFrame = null;
            double totalDuration = 0;
            while( (videoFrame = videoDemux.nextFrame()) != null) {
                totalDuration += videoFrame.getDurationD();
            }
            return totalDuration;
        }
    }

    public static _2<Integer, Demuxer> createM2TSDemuxer(FileChannelWrapper ch, TrackType targetTrack) throws IOException {
        MTSDemuxer mts = new MTSDemuxer(ch);
        Set<Integer> programs = mts.getPrograms();
        if (programs.size() == 0) {
            LOG.error("The MPEG TS stream contains no programs");
            return null;
        }
        Tuple._2<Integer, Demuxer> found = null;
        for (Integer pid : programs) {
            ReadableByteChannel program = mts.getProgram(pid);
            if (found != null) {
                program.close();
                continue;
            }
            MPSDemuxer demuxer = new MPSDemuxer(program);
            if (targetTrack == TrackType.AUDIO && demuxer.getAudioTracks().size() > 0
                    || targetTrack == TrackType.VIDEO && demuxer.getVideoTracks().size() > 0) {
                found = org.jcodec.common.Tuple._2(pid, (Demuxer) demuxer);
            } else {
                program.close();
            }
        }
        return found;
    }

    public void addProgressListener(ProgressListener l) {
        listeners.add(l);
    }

    public int getProgress() {
        return lastPercentage;
    }

    public void validate(File recDir) throws IOException, ParseException, PlaylistException {
        File playlist = new File(recDir, "playlist.m3u8");
        if(playlist.exists()) {
            PlaylistParser playlistParser = new PlaylistParser(new FileInputStream(playlist), Format.EXT_M3U, Encoding.UTF_8);
            Playlist m3u = playlistParser.parse();
            MediaPlaylist mediaPlaylist = m3u.getMediaPlaylist();
            int playlistSize = mediaPlaylist.getTracks().size();
            File[] segments = recDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("media_") && name.endsWith(".ts");
                }
            });
            if(segments.length != playlistSize) {
                throw new InvalidPlaylistException("Playlist size and amount of segments differ");
            } else {
                LOG.debug("Generated playlist looks good");
            }
        } else {
            throw new FileNotFoundException(playlist.getAbsolutePath() + " does not exist");
        }
    }

    public static class InvalidPlaylistException extends RuntimeException {
        public InvalidPlaylistException(String msg) {
            super(msg);
        }
    }
}
