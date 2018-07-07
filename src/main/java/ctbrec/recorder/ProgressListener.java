package ctbrec.recorder.server;

@FunctionalInterface
public interface ProgressListener {
    public void update(int percentage);
}
