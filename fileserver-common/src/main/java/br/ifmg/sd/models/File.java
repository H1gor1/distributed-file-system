package br.ifmg.sd.models;

import java.io.Serializable;

public class File implements Serializable {

    private String name;
    private String type;
    private String path;
    private long size;

    public File(String name, String type, String path, long size) {
        this.name = name;
        this.type = type;
        this.path = path;
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }
}
