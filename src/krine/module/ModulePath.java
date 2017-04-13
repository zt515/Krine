package krine.module;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class ModulePath {
    private List<String> modulePath;
    
    public ModulePath() {
        modulePath = new LinkedList<>();
    }
    
    public void addPath(String path) {
        modulePath.add(path);
    }
    
    public void removePath(String path) {
        modulePath.remove(path);
    }
    
    public String search(String name) {
        File file = null;
        for (String path : modulePath) {
            file = new File(path, name);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (String path : modulePath) {
            builder.append(path).append(",");
        }
        if (builder.length() != 0 
            && builder.charAt(builder.length() - 1) == ',') {
            builder.deleteCharAt(builder.length() - 1);
        }
        return "[" + builder.toString() + "]";
    }
}
