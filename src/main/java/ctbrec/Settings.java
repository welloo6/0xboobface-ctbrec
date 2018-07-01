package ctbrec;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Settings {
    public boolean localRecording = true;
    public int httpPort = 8080;
    public int httpTimeout = 30;
    public String httpServer = "localhost";
    public String recordingsDir = System.getProperty("user.home") + File.separator + "ctbrec";
    public String mediaPlayer = "/usr/bin/mpv";
    public String username = "";
    public String password = "";
    public String lastDownloadDir = "";
    public List<Model> models = new ArrayList<Model>();
}
