package ctbrec.recorder;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map.Entry;

import ctbrec.Settings;

public class OS {

    public static enum TYPE {
        LINUX,
        MAC,
        WINDOWS,
        OTHER
    }

    public static TYPE getOsType() {
        if(System.getProperty("os.name").contains("Linux")) {
            return TYPE.LINUX;
        } else if(System.getProperty("os.name").contains("Windows")) {
            return TYPE.WINDOWS;
        } else if(System.getProperty("os.name").contains("Mac")) {
            return TYPE.MAC;
        } else {
            return TYPE.OTHER;
        }
    }

    public static File getConfigDir() {
        File configDir;
        switch (getOsType()) {
        case LINUX:
            String userHome = System.getProperty("user.home");
            configDir = new File(new File(userHome, ".config"), "ctbrec");
            break;
        case MAC:
            userHome = System.getProperty("user.home");
            configDir = new File(userHome, "Library/Preferences/ctbrec");
            break;
        case WINDOWS:
            String appData = System.getenv("APPDATA");
            configDir = new File(appData, "ctbrec");
            break;
        default:
            throw new RuntimeException("Unsupported operating system " + System.getProperty("os.name"));
        }
        return configDir;
    }

    public static Settings getDefaultSettings() {
        Settings settings = new Settings();
        if(getOsType() == TYPE.WINDOWS) {
            String userHome = System.getProperty("user.home");
            Path path = Paths.get(userHome, "Videos", "ctbrec");
            settings.recordingsDir = path.toString();
            String programFiles = System.getenv("ProgramFiles");
            programFiles = programFiles != null ? programFiles : "C:\\Program Files";
            settings.mediaPlayer = Paths.get(programFiles, "VideoLAN", "VLC", "vlc.exe").toString();
        } else if(getOsType() == TYPE.MAC) {
            String userHome = System.getProperty("user.home");
            settings.recordingsDir = Paths.get(userHome, "Movies", "ctbrec").toString();
            settings.mediaPlayer = "/Applications/VLC.app/Contents/MacOS/VLC";
        }
        return settings;
    }

    public static String[] getEnvironment() {
        String[] env = new String[System.getenv().size()];
        int index = 0;
        for (Entry<String, String> entry : System.getenv().entrySet()) {
            env[index++] = entry.getKey() + "=" + entry.getValue();
        }
        return env;
    }
}
