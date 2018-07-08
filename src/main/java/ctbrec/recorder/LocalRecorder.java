package ctbrec.recorder;
import static ctbrec.Recording.STATUS.FINISHED;
import static ctbrec.Recording.STATUS.GENERATING_PLAYLIST;
import static ctbrec.Recording.STATUS.RECORDING;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;

import ctbrec.Config;
import ctbrec.HttpClient;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.Recording.STATUS;
import ctbrec.recorder.PlaylistGenerator.InvalidPlaylistException;
import ctbrec.recorder.download.Download;
import ctbrec.recorder.download.HlsDownload;

public class LocalRecorder implements Recorder {

    private static final transient Logger LOG = LoggerFactory.getLogger(LocalRecorder.class);

    private List<Model> models = Collections.synchronizedList(new ArrayList<>());
    private Lock lock = new ReentrantLock();
    private Map<Model, Download> recordingProcesses = Collections.synchronizedMap(new HashMap<>());
    private Map<File, PlaylistGenerator> playlistGenerators = new HashMap<>();
    private Map<File, SegmentMerger> segmentMergers = new HashMap<>();
    private Config config;
    private ProcessMonitor processMonitor;
    private OnlineMonitor onlineMonitor;
    private PlaylistGeneratorTrigger playlistGenTrigger;
    private HttpClient client = HttpClient.getInstance();
    private volatile boolean recording = true;
    private List<File> deleteInProgress = Collections.synchronizedList(new ArrayList<>());

    public LocalRecorder(Config config) {
        this.config = config;
        config.getSettings().models.stream().forEach((m) -> {
            m.setOnline(false);
            models.add(m);
        });

        recording = true;
        processMonitor = new ProcessMonitor();
        processMonitor.start();
        onlineMonitor = new OnlineMonitor();
        onlineMonitor.start();
        playlistGenTrigger = new PlaylistGeneratorTrigger();
        playlistGenTrigger.start();

        LOG.debug("Recorder initialized");
        LOG.info("Models to record: {}", models);
        LOG.info("Saving recordings in {}", config.getSettings().recordingsDir);
    }

    @Override
    public void startRecording(Model model) throws IOException {
        lock.lock();
        if(!models.contains(model)) {
            LOG.info("Model {} added", model);
            models.add(model);
            config.getSettings().models.add(model);
            onlineMonitor.interrupt();
        }
        lock.unlock();
    }

    @Override
    public void stopRecording(Model model) throws IOException {
        lock.lock();
        try {
            if (models.contains(model)) {
                models.remove(model);
                config.getSettings().models.remove(model);
                if(recordingProcesses.containsKey(model)) {
                    stopRecordingProcess(model);
                }
                LOG.info("Model {} removed", model);
            }
        } finally {
            lock.unlock();
        }
    }

    private void startRecordingProcess(Model model) throws IOException {
        lock.lock();
        LOG.debug("Waiting for lock to restart recording for {}", model.getName());
        try {
            LOG.debug("Restart recording for model {}", model.getName());
            if(recordingProcesses.containsKey(model)) {
                LOG.error("A recording for model {} is already running", model);
                return;
            }

            if(!models.contains(model)) {
                LOG.info("Model {} has been removed. Restarting of recording cancelled.", model);
                return;
            }

            Download download = new HlsDownload(client);
            recordingProcesses.put(model, download);
            new Thread() {
                @Override
                public void run() {
                    try {
                        download.start(model, config);
                    } catch (IOException e) {
                        LOG.error("Download failed. Download alive: {}", download.isAlive(), e);
                    }
                }
            }.start();
        } finally {
            lock.unlock();
        }
    }

    private void stopRecordingProcess(Model model) throws IOException {
        lock.lock();
        try {
            Download download = recordingProcesses.get(model);
            download.stop();
            recordingProcesses.remove(model);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isRecording(Model model) {
        lock.lock();
        try {
            return models.contains(model);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Model> getModelsRecording() {
        return Collections.unmodifiableList(models);
    }

    @Override
    public void shutdown() {
        LOG.info("Shutting down");
        recording = false;
        LOG.debug("Stopping monitor threads");
        onlineMonitor.running = false;
        processMonitor.running = false;
        playlistGenTrigger.running = false;
        LOG.debug("Stopping all recording processes");
        stopRecordingProcesses();
    }

    private void stopRecordingProcesses() {
        lock.lock();
        try {
            for (Model model : models) {
                Download recordingProcess = recordingProcesses.get(model);
                if(recordingProcess != null) {
                    try {
                        recordingProcess.stop();
                        LOG.debug("Stopped recording for {}", model);
                    } catch (Exception e) {
                        LOG.error("Couldn't stop recording for model {}", model, e);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean checkIfOnline(Model model) throws IOException {
        StreamInfo streamInfo = Chaturbate.getStreamInfo(model, client);
        return Objects.equals(streamInfo.room_status, "public");
    }

    private void tryRestartRecording(Model model) {
        if(!recording) {
            // recorder is not in recording state
            return;
        }

        try {
            lock.lock();
            boolean modelInRecordingList = models.contains(model);
            boolean online = checkIfOnline(model);
            if(modelInRecordingList && online) {
                LOG.info("Restarting recording for model {}", model);
                recordingProcesses.remove(model);
                startRecordingProcess(model);
            }
        } catch (Exception e) {
            LOG.error("Couldn't restart recording for model {}", model);
        } finally {
            lock.unlock();
        }
    }

    private class ProcessMonitor extends Thread {
        private volatile boolean running = false;

        public ProcessMonitor() {
            setName("ProcessMonitor");
            setDaemon(true);
        }

        @Override
        public void run() {
            running = true;
            while(running) {
                lock.lock();
                try {
                    List<Model> restart = new ArrayList<Model>();
                    for (Iterator<Entry<Model,Download>> iterator = recordingProcesses.entrySet().iterator(); iterator.hasNext();) {
                        Entry<Model, Download> entry = iterator.next();
                        Model m = entry.getKey();
                        Download d = entry.getValue();
                        if(!d.isAlive()) {
                            LOG.debug("Recording terminated for model {}", m.getName());
                            iterator.remove();
                            restart.add(m);
                            finishRecording(d.getDirectory());
                        }
                    }
                    for (Model m : restart) {
                        tryRestartRecording(m);
                    }
                }
                finally {
                    lock.unlock();
                }

                try {
                    if(running) Thread.sleep(1000);
                } catch (InterruptedException e) {
                    LOG.error("Couldn't sleep", e);
                }
            }
            LOG.debug(getName() + " terminated");
        }
    }

    private void finishRecording(File directory) {
        Thread t = new Thread() {
            @Override
            public void run() {
                boolean local = Config.getInstance().getSettings().localRecording;
                boolean automerge = Config.getInstance().getSettings().automerge;
                generatePlaylist(directory);
                if(local && automerge) {
                    File mergedFile = merge(directory);
                    if(mergedFile != null && mergedFile.exists() && mergedFile.length() > 0) {
                        LOG.debug("Merged file {}", mergedFile.getAbsolutePath());
                        if (!Config.getInstance().getSettings().automergeKeepSegments) {
                            try {
                                LOG.debug("Deleting directory {}", directory);
                                delete(directory, mergedFile);
                            } catch (IOException e) {
                                LOG.error("Couldn't delete directory {}", directory, e);
                            }
                        }
                    } else {
                        LOG.error("Merged file not found {}", mergedFile);
                    }
                }
            }
        };
        t.setDaemon(true);
        t.setName("Postprocessing" + directory.toString());
        t.start();
    }


    private File merge(File recDir) {
        SegmentMerger segmentMerger = new SegmentMerger();
        segmentMergers.put(recDir, segmentMerger);
        try {
            File mergedFile = Recording.mergedFileFromDirectory(recDir);
            segmentMerger.merge(recDir, mergedFile);
            return mergedFile;
        } catch (IOException e) {
            LOG.error("Couldn't generate playlist file", e);
        } catch (ParseException | PlaylistException | InvalidPlaylistException e) {
            LOG.error("Playlist is invalid", e);
        } finally {
            segmentMergers.remove(recDir);
        }
        return null;
    }

    private void generatePlaylist(File recDir) {
        PlaylistGenerator playlistGenerator = new PlaylistGenerator();
        playlistGenerators.put(recDir, playlistGenerator);
        try {
            playlistGenerator.generate(recDir);
            playlistGenerator.validate(recDir);
        } catch (IOException | ParseException | PlaylistException e) {
            LOG.error("Couldn't generate playlist file", e);
        } catch (InvalidPlaylistException e) {
            LOG.error("Playlist is invalid", e);
            File playlist = new File(recDir, "playlist.m3u8");
            playlist.delete();
        } finally {
            playlistGenerators.remove(recDir);
        }
    }

    private class OnlineMonitor extends Thread {
        private volatile boolean running = false;

        public OnlineMonitor() {
            setName("OnlineMonitor");
            setDaemon(true);
        }

        @Override
        public void run() {
            running = true;
            while(running) {
                lock.lock();
                try {
                    for (Model model : models) {
                        if(!recordingProcesses.containsKey(model)) {
                            try {
                                LOG.trace("Checking online state for {}", model);
                                boolean isOnline = checkIfOnline(model);
                                boolean wasOnline = model.isOnline();
                                model.setOnline(isOnline);
                                if(wasOnline != isOnline && isOnline) {
                                    LOG.info("Model {}'s room back to public. Starting recording", model);
                                    startRecordingProcess(model);
                                }
                            } catch (IOException e) {
                                LOG.error("Couldn't check if model {} is online", model.getName(), e);
                            }
                        }
                    }
                } finally {
                    lock.unlock();
                }

                try {
                    if(running) Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOG.trace("Sleep interrupted");
                }
            }
            LOG.debug(getName() + " terminated");
        }
    }

    private class PlaylistGeneratorTrigger extends Thread {
        private volatile boolean running = false;

        public PlaylistGeneratorTrigger() {
            setName("PlaylistGeneratorTrigger");
            setDaemon(true);
        }

        @Override
        public void run() {
            running = true;
            while(running) {
                try {
                    List<Recording> recs = getRecordings();
                    for (Recording rec : recs) {
                        if(rec.getStatus() == RECORDING) {
                            boolean recordingProcessFound = false;
                            File recordingsDir = new File(config.getSettings().recordingsDir);
                            File recDir = new File(recordingsDir, rec.getPath());
                            for(Entry<Model, Download> download : recordingProcesses.entrySet()) {
                                if(download.getValue().getDirectory().equals(recDir)) {
                                    recordingProcessFound = true;
                                }
                            }
                            if(!recordingProcessFound) {
                                if(deleteInProgress.contains(recDir)) {
                                    LOG.debug("{} is being deleted. Not going to generate a playlist", recDir);
                                } else {
                                    finishRecording(recDir);
                                }
                            }
                        }
                    }

                    if(running) Thread.sleep(10000);
                } catch (InterruptedException e) {
                    LOG.error("Couldn't sleep", e);
                } catch (Exception e) {
                    LOG.error("Unexpected error in playlist trigger thread", e);
                }
            }
            LOG.debug(getName() + " terminated");
        }
    }

    @Override
    public List<Recording> getRecordings() {
        List<Recording> recordings = new ArrayList<>();
        File recordingsDir = new File(config.getSettings().recordingsDir);
        File[] subdirs = recordingsDir.listFiles();
        if(subdirs == null ) {
            return Collections.emptyList();
        }

        for (File subdir : subdirs) {
            if(!subdir.isDirectory()) {
                continue;
            }

            File[] recordingsDirs = subdir.listFiles();
            for (File rec : recordingsDirs) {
                String pattern = "yyyy-MM-dd_HH-mm";
                SimpleDateFormat sdf = new SimpleDateFormat(pattern);
                if(rec.isDirectory()) {
                    try {
                        if(rec.getName().length() != pattern.length()) {
                            continue;
                        }

                        Date startDate = sdf.parse(rec.getName());
                        Recording recording = new Recording();
                        recording.setModelName(subdir.getName());
                        recording.setStartDate(Instant.ofEpochMilli(startDate.getTime()));
                        recording.setPath(recording.getModelName() + "/" + rec.getName());
                        recording.setSizeInByte(getSize(rec));
                        File playlist = new File(rec, "playlist.m3u8");
                        recording.setHasPlaylist(playlist.exists());
                        if(recording.hasPlaylist()) {
                            recording.setStatus(FINISHED);
                        } else {
                            // this might be a merged recording
                            if(Recording.isMergedRecording(rec)) {
                                recording.setStatus(FINISHED);
                            } else {
                                PlaylistGenerator playlistGenerator = playlistGenerators.get(rec);
                                if(playlistGenerator != null) {
                                    recording.setStatus(GENERATING_PLAYLIST);
                                    recording.setProgress(playlistGenerator.getProgress());
                                } else {
                                    SegmentMerger merger = segmentMergers.get(rec);
                                    if(merger != null) {
                                        recording.setStatus(STATUS.MERGING);
                                        recording.setProgress(merger.getProgress());
                                    } else {
                                        recording.setStatus(RECORDING);
                                    }
                                }
                            }
                        }
                        recordings.add(recording);
                    } catch(Exception e) {
                        LOG.debug("Ignoring {}", rec.getAbsolutePath());
                    }
                }
            }
        }
        return recordings;
    }

    private long getSize(File rec) {
        long size = 0;
        File[] files = rec.listFiles();
        for (File file : files) {
            size += file.length();
        }
        return size;
    }

    @Override
    public void delete(Recording recording) throws IOException {
        File recordingsDir = new File(config.getSettings().recordingsDir);
        File directory = new File(recordingsDir, recording.getPath());
        delete(directory, new File[] {});
    }

    private void delete(File directory, File...excludes) throws IOException {
        if(!directory.exists()) {
            throw new IOException("Recording does not exist");
        }

        try {
            deleteInProgress.add(directory);
            File[] files = directory.listFiles();
            boolean deletedAllFiles = true;
            for (File file : files) {
                boolean skip = false;
                for (File exclude : excludes) {
                    if(file.equals(exclude)) {
                        skip = true;
                        break;
                    }
                }
                if(skip) {
                    continue;
                }

                try {
                    Files.delete(file.toPath());
                } catch (Exception e) {
                    deletedAllFiles = false;
                    LOG.debug("Couldn't delete {}", file, e);
                }
            }

            if(deletedAllFiles) {
                if(directory.list().length == 0) {
                    boolean deleted = directory.delete();
                    if(deleted) {
                        if(directory.getParentFile().list().length == 0) {
                            directory.getParentFile().delete();
                        }
                    } else {
                        throw new IOException("Couldn't delete " + directory);
                    }
                }
            } else {
                throw new IOException("Couldn't delete all files in " + directory);
            }
        } finally {
            deleteInProgress.remove(directory);
        }
    }

    @Override
    public void merge(Recording rec, boolean keepSegments) throws IOException {
        File recordingsDir = new File(config.getSettings().recordingsDir);
        File directory = new File(recordingsDir, rec.getPath());
        merge(directory);
        if(!keepSegments) {
            File mergedFile = Recording.mergedFileFromDirectory(directory);
            delete(directory, mergedFile);
        }
    }
}
