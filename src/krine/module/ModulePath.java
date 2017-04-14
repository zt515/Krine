package krine.module;

import com.krine.kar.KarEntry;
import com.krine.kar.KarFile;
import krine.core.Core;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Save paths where we find Module files
 *
 * @author kiva
 * @date 2017/4/14
 */
public class ModulePath {
    private List<String> modulePath;
    private List<KarFile> karFiles;

    ModulePath() {
        modulePath = new LinkedList<>();
        karFiles = new LinkedList<>();

        Core.doOnExit(new Runnable() {
            @Override
            public void run() {
                try {
                    for (KarFile karFile : karFiles) {
                        karFile.close();
                    }
                } catch (IOException ignore) {
                }
            }
        });
    }

    void addModuleSearchPath(File file) {
        if (!file.canRead()) {
            return;
        }

        if (file.isFile()) {
            try {
                karFiles.add(new KarFile(file));
            } catch (IOException ignore) {
            }
        } else {
            modulePath.add(file.getAbsolutePath());
        }
    }

    /**
     * Search a module file in .kar files by given module name.
     *
     * @param moduleName Module name to search
     * @return KarEntry containing module file content if found, otherwise null.
     */
    KarEntry searchPackedModule(String moduleName) {
        String searchName = generateModuleFileName(moduleName);

        try {
            for (KarFile karFile : karFiles) {
                KarEntry entry = karFile.getEntry(searchName);
                if (entry != null) {
                    return entry;
                }
            }
        } catch (IOException ignore) {
        }

        return null;
    }

    /**
     * Search a module file on disk by given module name.
     *
     * @param moduleName Module name to search
     * @return Module file path if found, otherwise null.
     */
    String searchModuleOnDisk(String moduleName) {
        String searchName = generateModuleFileName(moduleName);

        for (String path : modulePath) {
            File file = new File(path, searchName);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    /**
     * moduleName is just a name, we need suffix
     *
     * @param moduleName Module name
     * @return Module file name
     */
    private String generateModuleFileName(String moduleName) {
        return moduleName + ".k";
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
