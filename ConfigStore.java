package com.solimes.client.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

/** Reads/writes options as a .properties file. Unknown keys written by other tools (e.g. the launcher) are preserved. */
public final class ConfigStore {
    private final OptionRegistry registry;
    private final Path file;
    private final Properties extra = new Properties();

    public ConfigStore(OptionRegistry registry, Path file) {
        this.registry = registry;
        this.file = file;
    }

    public void load() throws IOException {
        extra.clear();
        if (!Files.exists(file)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { p.load(r); }
        for (String name : p.stringPropertyNames()) {
            Option<?> o = registry.find(name);
            if (o != null) o.deserialize(p.getProperty(name));
            else extra.setProperty(name, p.getProperty(name));
        }
    }

    public synchronized void save() throws IOException {
        Properties p = new Properties();
        p.putAll(extra);
        for (Option<?> o : registry.all()) p.setProperty(o.key(), o.serialize());
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) { p.store(w, "Solimes Client"); }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
}
