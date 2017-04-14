package com.krine.kar;

import java.io.UnsupportedEncodingException;

/**
 * @author kiva
 * @date 2017/4/14
 */
public class KarEntry {
    private String entryName;
    private byte[] content;

    public KarEntry(String entryName, byte[] content) {
        this.entryName = entryName;
        this.content = content;
    }

    public String getEntryName() {
        return entryName;
    }

    public byte[] getContent() {
        return content;
    }

    public String getStringContent() {
        if (entryName.endsWith(".k")) {
            try {
                return new String(getContent(), "utf-8");
            } catch (UnsupportedEncodingException ignore) {
            }
        }

        return null;
    }
}
