package sa.com.cloudsolutions.antikythera.generator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class CopyUtils {
    public static final String SRC = "src";

    public static void createMavenProjectStructure(String basePackage, String path) throws IOException {
        String basePackagePath = basePackage.replace(".", File.separator);
        String[] directories = {
                path + File.separator + SRC + File.separator + "main" + File.separator + "java" + File.separator + basePackagePath,
                path + File.separator + SRC + File.separator + "main" + File.separator + "resources",
                path + File.separator + SRC + File.separator + "test" + File.separator + "java" + File.separator + basePackagePath,
                path + File.separator + SRC + File.separator + "test" + File.separator + "resources"
        };

        for (String dir : directories) {
            Files.createDirectories(Paths.get(dir));
        }
    }

    public static void writeFile( String relativePath, String content) throws IOException {
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


}
