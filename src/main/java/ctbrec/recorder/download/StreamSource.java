package ctbrec.recorder.download;

import java.text.DecimalFormat;

public class StreamSource {
    public int bandwidth;
    public int height;
    public String mediaPlaylistUrl;

    public int getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(int bandwidth) {
        this.bandwidth = bandwidth;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getMediaPlaylistUrl() {
        return mediaPlaylistUrl;
    }

    public void setMediaPlaylistUrl(String mediaPlaylistUrl) {
        this.mediaPlaylistUrl = mediaPlaylistUrl;
    }

    @Override
    public String toString() {
        DecimalFormat df = new DecimalFormat("0.00");
        float mbit = bandwidth / 1024.0f / 1024.0f;
        return height + "p (" + df.format(mbit) + " Mbit/s)";
    }
}
