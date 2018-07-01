package ctbrec;

import java.io.IOException;
import java.time.Instant;

import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;

public class InstantJsonAdapter extends JsonAdapter<Instant> {
    @Override
    public Instant fromJson(JsonReader reader) throws IOException {
        long timeInEpochMillis = reader.nextLong();
        return Instant.ofEpochMilli(timeInEpochMillis);
    }

    @Override
    public void toJson(JsonWriter writer, Instant time) throws IOException {
        writer.value(time.toEpochMilli());
    }
}
