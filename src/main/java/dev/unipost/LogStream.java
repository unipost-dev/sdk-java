package dev.unipost;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class LogStream implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final InputStream body;
    private final BufferedReader reader;
    private JsonNode event;
    private String eventName;
    private String id;

    LogStream(InputStream body) {
        this.body = body;
        this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
    }

    public boolean next() {
        String currentEvent = null;
        String currentId = null;
        StringBuilder data = new StringBuilder();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    if (data.length() == 0) {
                        continue;
                    }
                    if (currentEvent != null && !currentEvent.equals("log.created")) {
                        data.setLength(0);
                        currentEvent = null;
                        currentId = null;
                        continue;
                    }
                    event = MAPPER.readTree(data.toString());
                    eventName = currentEvent;
                    id = currentId;
                    return true;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                int separator = line.indexOf(':');
                String field = separator == -1 ? line : line.substring(0, separator);
                String value = separator == -1 ? "" : line.substring(separator + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (field.equals("event")) {
                    currentEvent = value;
                } else if (field.equals("id")) {
                    currentId = value;
                } else if (field.equals("data")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(value);
                }
            }
            if (data.length() > 0 && (currentEvent == null || currentEvent.equals("log.created"))) {
                event = MAPPER.readTree(data.toString());
                eventName = currentEvent;
                id = currentId;
                return true;
            }
            return false;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to decode log stream event", e);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read log stream", e);
        }
    }

    public JsonNode event() {
        return event;
    }

    public String eventName() {
        return eventName;
    }

    public String id() {
        return id;
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
