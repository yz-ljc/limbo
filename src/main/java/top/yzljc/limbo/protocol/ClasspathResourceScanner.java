package top.yzljc.limbo.protocol;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Scans classpath resources matching a regex pattern.
 * Adapted from LOOHP/Limbo's ClasspathResourcesUtils.
 */
final class ClasspathResourceScanner {

    private ClasspathResourceScanner() {
    }

    static List<String> getResources(Pattern pattern) {
        List<String> results = new ArrayList<>();
        String classPath = System.getProperty("java.class.path", ".");
        String[] elements = classPath.split(File.pathSeparator);
        for (String element : elements) {
            File file = new File(element);
            if (file.isDirectory()) {
                results.addAll(scanDirectory(file, file.getAbsolutePath(), pattern));
            } else {
                results.addAll(scanJarFile(file, pattern));
            }
        }
        return results;
    }

    private static List<String> scanDirectory(File root, String rootPath, Pattern pattern) {
        List<String> results = new ArrayList<>();
        scanDirRecursive(root, rootPath, pattern, results);
        return results;
    }

    private static void scanDirRecursive(File dir, String rootPath, Pattern pattern, List<String> results) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirRecursive(file, rootPath, pattern, results);
            } else {
                String absPath = file.getAbsolutePath();
                // Convert to classpath-relative path using forward slashes
                String relative = absPath.substring(rootPath.length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                relative = relative.replace(File.separatorChar, '/');
                if (pattern.matcher(relative).matches()) {
                    results.add(relative);
                }
            }
        }
    }

    private static List<String> scanJarFile(File file, Pattern pattern) {
        List<String> results = new ArrayList<>();
        try (ZipFile zf = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (pattern.matcher(name).matches()) {
                    results.add(name);
                }
            }
        } catch (IOException e) {
            // Skip unreadable JARs
        }
        return results;
    }
}
