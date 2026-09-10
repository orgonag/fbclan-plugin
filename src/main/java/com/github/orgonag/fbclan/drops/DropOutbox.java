package com.github.orgonag.fbclan.drops;

import com.github.orgonag.fbclan.core.Session;
import com.github.orgonag.fbclan.core.Supabase;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Disk I/O only on workers. One atomic file per event; no credentials stored. */
public final class DropOutbox
{
    private final Path directory;
    public DropOutbox(Path directory) { this.directory = directory; }
    public synchronized void add(Session session, JsonObject row) throws IOException
    {
        Files.createDirectories(directory);
        try (java.util.stream.Stream<Path> files = Files.list(directory))
        {
            if (files.filter(p -> p.toString().endsWith(".json")).count() >= 1000)
                throw new IOException("Drop outbox is full (1000 events)");
        }
        JsonObject record = new JsonObject();
        record.addProperty("profile", session.getProfile());
        record.addProperty("endpoint", Supabase.projectUrl());
        record.add("row", row);
        Path target = directory.resolve(Supabase.str(row, "event_id") + ".json");
        Path temporary = Files.createTempFile(directory, "event-", ".tmp");
        try
        {
            Files.write(temporary, record.toString().getBytes(StandardCharsets.UTF_8));
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target); }
        }
        finally { Files.deleteIfExists(temporary); }
    }
    public synchronized List<JsonObject> pending(Session session) throws IOException
    {
        List<JsonObject> out = new ArrayList<>();
        if (!Files.isDirectory(directory)) return out;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.json"))
        {
            for (Path file : files)
            {
                try
                {
                    JsonObject record = new JsonParser().parse(Files.readString(file)).getAsJsonObject();
                    JsonObject row = record.getAsJsonObject("row");
                    if (session.getProfile().equals(Supabase.str(record, "profile"))
                        && Supabase.projectUrl().equals(Supabase.str(record, "endpoint"))
                        && com.github.orgonag.fbclan.core.Names.same(session.getRsn(), Supabase.str(row, "rsn"))) out.add(row);
                }
                catch (RuntimeException e) { Files.move(file, file.resolveSibling(file.getFileName() + ".invalid")); }
            }
        }
        return out;
    }
    public synchronized void reject(String id) throws IOException
    {
        Path file = directory.resolve(java.util.UUID.fromString(id) + ".json");
        Files.move(file, file.resolveSibling(file.getFileName() + ".rejected"), StandardCopyOption.REPLACE_EXISTING);
    }
    public synchronized void remove(String id) throws IOException
    {
        Files.deleteIfExists(directory.resolve(java.util.UUID.fromString(id) + ".json"));
    }
}
