package com.gemma3n.smartlearning;

import java.io.File;

public class FileItem {
    private File file;

    public FileItem(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return file.getName();
    }
}