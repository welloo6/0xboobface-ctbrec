package ctbrec;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class Recording {
    private String modelName;
    private Instant startDate;
    private String path;
    private boolean hasPlaylist;
    private STATUS status;
    private int generatingPlaylistProgress = -1;
    private long sizeInByte;

    public static enum STATUS {
        RECORDING,
        GENERATING_PLAYLIST,
        FINISHED,
        DOWNLOADING,
        MERGING
    }

    public Recording() {}

    public Recording(String path) throws ParseException {
        this.path = path;
        this.modelName = path.substring(0, path.indexOf("/"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
        Date date = sdf.parse(path.substring(path.indexOf('/')+1));
        startDate = Instant.ofEpochMilli(date.getTime());
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Instant getStartDate() {
        return startDate;
    }

    public void setStartDate(Instant startDate) {
        this.startDate = startDate;
    }

    public STATUS getStatus() {
        return status;
    }

    public void setStatus(STATUS status) {
        this.status = status;
    }

    public int getProgress() {
        return this.generatingPlaylistProgress;
    }

    public void setProgress(int progress) {
        this.generatingPlaylistProgress = progress;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean hasPlaylist() {
        return hasPlaylist;
    }

    public void setHasPlaylist(boolean hasPlaylist) {
        this.hasPlaylist = hasPlaylist;
    }

    public long getSizeInByte() {
        return sizeInByte;
    }

    public void setSizeInByte(long sizeInByte) {
        this.sizeInByte = sizeInByte;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((modelName == null) ? 0 : modelName.hashCode());
        result = prime * result + ((startDate == null) ? 0 : startDate.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        Recording other = (Recording) obj;
        if (getModelName() == null) {
            if (other.getModelName() != null)
                return false;
        } else if (!getModelName().equals(other.getModelName()))
            return false;
        if (getStartDate() == null) {
            if (other.getStartDate() != null)
                return false;
        } else if (!getStartDate().equals(other.getStartDate()))
            return false;
        return true;
    }

    public static File mergedFileFromDirectory(File recDir) {
        String date = recDir.getName();
        String model = recDir.getParentFile().getName();
        String filename = model + "-" + date + ".ts";
        File mergedFile = new File(recDir, filename);
        return mergedFile;
    }

    public static boolean isMergedRecording(File recDir) {
        File mergedFile = mergedFileFromDirectory(recDir);
        return mergedFile.exists();
    }

    public static boolean isMergedRecording(Recording recording) {
        String recordingsDir = Config.getInstance().getSettings().recordingsDir;
        File recDir = new File(recordingsDir, recording.getPath());
        return isMergedRecording(recDir);
    }
}
