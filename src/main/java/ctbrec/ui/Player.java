package ctbrec.ui;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.recorder.OS;
import ctbrec.recorder.StreamRedirectThread;

public class Player {
    private static final transient Logger LOG = LoggerFactory.getLogger(Player.class);
    private static PlayerThread playerThread;

    public static void play(String url) {
        try {
            if (playerThread != null && playerThread.isRunning()) {
                playerThread.stopThread();
            }

            playerThread = new PlayerThread(url);
        } catch (Exception e1) {
            LOG.error("Couldn't start player", e1);
        }
    }

    public static void play(Recording rec) {
        try {
            if (playerThread != null && playerThread.isRunning()) {
                playerThread.stopThread();
            }

            playerThread = new PlayerThread(rec);
        } catch (Exception e1) {
            LOG.error("Couldn't start player", e1);
        }
    }

    public static void stop() {
        if (playerThread != null) {
            playerThread.stopThread();
        }
    }

    private static class PlayerThread extends Thread {
        private boolean running = false;
        private Process playerProcess;
        private String url;
        private Recording rec;

        PlayerThread(String url) {
            this.url = url;
            setName(getClass().getName());
            start();
        }

        PlayerThread(Recording rec) {
            this.rec = rec;
            setName(getClass().getName());
            start();
        }

        @Override
        public void run() {
            running = true;
            Runtime rt = Runtime.getRuntime();
            try {
                if (Config.getInstance().getSettings().localRecording && rec != null) {
                    File dir = new File(Config.getInstance().getSettings().recordingsDir, rec.getPath());
                    File file = null;
                    if(Recording.isMergedRecording(rec)) {
                        file = Recording.mergedFileFromDirectory(dir);
                    } else {
                        file = new File(dir, "playlist.m3u8");
                    }
                    playerProcess = rt.exec(Config.getInstance().getSettings().mediaPlayer + " " + file, OS.getEnvironment(), dir);
                } else {
                    playerProcess = rt.exec(Config.getInstance().getSettings().mediaPlayer + " " + url);
                }

                // create threads, which read stdout and stderr of the player process. these are needed,
                // because otherwise the internal buffer for these streams fill up and block the process
                Thread std = new Thread(new StreamRedirectThread(playerProcess.getInputStream(), new DevNull()));
                std.setName("Player stdout pipe");
                std.setDaemon(true);
                std.start();
                Thread err = new Thread(new StreamRedirectThread(playerProcess.getErrorStream(), new DevNull()));
                err.setName("Player stderr pipe");
                err.setDaemon(true);
                err.start();

                playerProcess.waitFor();
                LOG.debug("Media player finished.");
            } catch (Exception e) {
                LOG.error("Error in player thread", e);
            }
            running = false;
        }

        public boolean isRunning() {
            return running;
        }

        public void stopThread() {
            if (playerProcess != null) {
                playerProcess.destroy();
            }
        }
    }

    private static class DevNull extends OutputStream {
        @Override
        public void write(int b) throws IOException {
        }

        @Override
        public void write(byte[] b) throws IOException {
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
        }
    }
}