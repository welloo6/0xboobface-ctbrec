package ctbrec.recorder;

@FunctionalInterface
public interface ProgressListener {
    public void update(int percentage);
}
