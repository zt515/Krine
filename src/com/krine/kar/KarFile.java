package com.krine.kar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class KarFile {
    private ZipFile zipFile;
    private Map<String, KarEntry> entryCache;

    public KarFile(String filePath) throws IOException {
        this(new File(filePath));
    }

    public KarFile(File file) throws IOException {
        zipFile = new ZipFile(file);
        entryCache = new HashMap<>();
    }

    public KarEntry getEntry(String entryName) throws IOException {
        KarEntry entry = getEntryFromCache(entryName);
        if (entry != null) {
            return entry;
        }

        ZipEntry zipEntry = zipFile.getEntry(entryName);
        if (zipEntry == null) {
            return null;
        }

        InputStream inputStream = zipFile.getInputStream(zipEntry);
        byte[] buffer = new byte[inputStream.available()];
        if (inputStream.read(buffer) != buffer.length) {
            throw new IOException("Error reading kar entry " + entryName);
        }

        entry = new KarEntry(entryName, buffer);
        cacheEntry(entry);
        return entry;
    }

    public void close() throws IOException {
        zipFile.close();
    }

    private KarEntry getEntryFromCache(String entryName) {
        return entryCache.getOrDefault(entryName, null);
    }

    private void cacheEntry(KarEntry entry) {
        entryCache.put(entry.getEntryName(), entry);
    }
}