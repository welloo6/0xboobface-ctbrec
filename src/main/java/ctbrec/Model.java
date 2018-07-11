package ctbrec;

import java.util.ArrayList;
import java.util.List;

public class Model {
    private String url;
    private String name;
    private String preview;
    private String description;
    private List<String> tags = new ArrayList<>();
    private boolean online = false;
    private int streamUrlIndex = -1;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getStreamUrlIndex() {
        return streamUrlIndex;
    }

    public void setStreamUrlIndex(int streamUrlIndex) {
        this.streamUrlIndex = streamUrlIndex;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getUrl() == null) ? 0 : getUrl().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Model))
            return false;
        Model other = (Model) obj;
        if (getName() == null) {
            if (other.getName() != null)
                return false;
        } else if (!getName().equals(other.getName()))
            return false;
        if (getUrl() == null) {
            if (other.getUrl() != null)
                return false;
        } else if (!getUrl().equals(other.getUrl()))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return name;
    }

    public static void main(String[] args) {
        Model model = new Model();
        model.name = "A";
        model.url = "url";
    }
}
