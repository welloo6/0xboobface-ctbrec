package ctbrec.recorder;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import ctbrec.Model;
import ctbrec.Recording;

public interface Recorder {
    public void startRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException;

    public void stopRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException;

    /**
     * Returns, if a model is in the list of models to record. This does not reflect, if there currently is a recording running. The model might be offline
     * aswell.
     */
    public boolean isRecording(Model model);

    public List<Model> getModelsRecording();

    public List<Recording> getRecordings() throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException;

    public void merge(Recording recording, boolean keepSegments) throws IOException;

    public void delete(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException;

    public void shutdown();
}
