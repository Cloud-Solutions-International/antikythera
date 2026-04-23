package sa.com.cloudsolutions.antikythera.generator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File-system utilities for creating Maven project structures, writing generated
 * source files, and copying classpath template resources into the output directory.
 */
public class CopyUtils {
    public static final String SRC = "src";

    public static void createMavenProjectStructure(String basePackage, String path) throws IOException {
        String basePackagePath = basePackage.replace(".", File.separator);
        String[] directories = {
                path + File.separator + SRC + File.separator + "main" + File.separator + "java" + File.separator
                        + basePackagePath,
                path + File.separator + SRC + File.separator + "main" + File.separator + "resources",
                path + File.separator + SRC + File.separator + "test" + File.separator + "java" + File.separator
                        + basePackagePath,
                path + File.separator + SRC + File.separator + "test" + File.separator + "resources"
        };

        for (String dir : directories) {
            Files.createDirectories(Paths.get(dir));
        }
    }

    public static void writeFile(String relativePath, String content) throws IOException {
        String filePath = Settings.getProperty(Settings.OUTPUT_PATH).toString() +
                File.separator + SRC + File.separator + "main" + File.separator + "java" +
                File.separator + relativePath;

        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    /**
     * Copy a template resource from the classpath to a destination directory.
     *
     * @param filename   the template filename (looked up under {@code templates/} on the classpath)
     * @param outputPath the root directory into which sub-path components are resolved
     * @param subPath    optional sub-path components appended to {@code outputPath}
     * @return the full path of the copied file, or {@code null} if {@code outputPath} is null
     * @throws IOException if the template cannot be read or the destination cannot be written
     */
    public static String copyTemplate(String filename, String outputPath, String... subPath) throws IOException {
        if (outputPath == null) {
            return null;
        }
        Path destinationPath = Path.of(outputPath, subPath);
        Files.createDirectories(destinationPath);
        String name = destinationPath + File.separator + filename;
        try (InputStream sourceStream = CopyUtils.class.getClassLoader()
                    .getResourceAsStream("templates/" + filename);
             FileOutputStream destStream = new FileOutputStream(name);
             FileChannel destChannel = destStream.getChannel()) {
            if (sourceStream == null) {
                throw new IOException("Template not found on classpath: templates/" + filename);
            }
            destChannel.transferFrom(Channels.newChannel(sourceStream), 0, Long.MAX_VALUE);
        }
        return name;
    }

    /**
     * Write content to an absolute file path.
     * Unlike writeFile, this does NOT prepend Settings.OUTPUT_PATH.
     *
     * @param absolutePath The full absolute path to the file
     * @param content      The content to write
     * @throws IOException if writing fails
     */
    public static void writeFileAbsolute(String absolutePath, String content) throws IOException {
        File file = new File(absolutePath);
        File parentDir = file.getParentFile();
        if (parentDir != null) {
            Files.createDirectories(parentDir.toPath());
        }
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }
}
