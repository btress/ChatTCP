package org.example.ungdungchat.shared.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.time.Instant;

public class JsonUtils {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, new InstantAdapter())
            .create();
}
