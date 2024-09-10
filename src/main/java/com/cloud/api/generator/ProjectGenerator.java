package com.cloud.api.generator;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class ProjectGenerator {
    private Properties props = new Properties();
    public String basePackage;
    private String basePath;
    private String controllers;
    public String outputPath;
    private static final String SUFFIX = ".java";

    private static ProjectGenerator instance;

    private ProjectGenerator() throws IOException {
        loadConfigMap();
    }

    public static ProjectGenerator getInstance() throws IOException {
        if (instance == null) {
            instance = new ProjectGenerator();
        }
        return instance;
    }

    private void loadConfigMap() throws IOException {
        try (FileInputStream fis = new FileInputStream("src/main/resources/generator.cfg")) {
            props.load(fis);
            this.basePath = props.getProperty("BASE_PATH");
            this.basePackage = props.getProperty("BASE_PACKAGE");
            this.controllers = props.getProperty("CONTROLLERS");
            this.outputPath = props.getProperty("OUTPUT_PATH");
        }
    }


    private void createMavenProjectStructure(String basePackage, String path) throws IOException {
        String basePackagePath = basePackage.replace(".", File.separator);
        String[] directories = {
                path + File.separator + "src" + File.separator + "main" + File.separator + "java" + File.separator + basePackagePath,
                path + File.separator + "src" + File.separator + "main" + File.separator + "resources",
                path + File.separator + "src" + File.separator + "test" + File.separator + "java" + File.separator + basePackagePath,
                path + File.separator + "src" + File.separator + "test" + File.separator + "resources"
        };

        for (String dir : directories) {
            Files.  createDirectories(Paths.get(dir));
        }
    }

    private void copyTemplate(String filename, String... subPath) throws IOException {
        Path destinationPath = Path.of(outputPath, subPath);     // Path where template file should be copied into
        Files.createDirectories(destinationPath);
        try (InputStream sourceStream = getClass().getClassLoader().getResourceAsStream("templates/"+filename);
            FileOutputStream destStream = new FileOutputStream(new File(destinationPath + File.separator + filename));
            FileChannel destChannel = destStream.getChannel()) {
            if (sourceStream == null) {
                throw new IOException("Template file not found");
            }
            destChannel.transferFrom(Channels.newChannel(sourceStream), 0, Long.MAX_VALUE);
        }
    }

    public static void copyFolder(Path source, Path destination) throws IOException {
        if (!Files.exists(destination)) {
            Files.createDirectories(destination);
        }

        Files.walk(source).forEach(sourcePath -> {
            Path targetPath = destination.resolve(source.relativize(sourcePath));
            try {
                if (Files.isDirectory(sourcePath)) {
                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath);
                    }
                } else {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void copyBaseFiles(String outputPath) throws IOException {
        copyTemplate("pom.xml");
        copyTemplate("TestHelper.java", "src", "test", "java", "com", "cloud", "api", "base");

        Path pathToCopy = Paths.get(outputPath, "src", "test", "resources");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get("src","test", "resources"), pathToCopy);

        pathToCopy = Paths.get(outputPath, "src", "main", "java", "com", "cloud", "api", "constants");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get("src","main", "java", "com", "cloud", "api", "constants"), pathToCopy);

        pathToCopy = Paths.get(outputPath, "src", "main", "java", "com", "cloud", "api", "configurations");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get("src","main", "java", "com", "cloud", "api", "configurations"), pathToCopy);
    }

    public void generate() throws IOException {
        createMavenProjectStructure(basePackage, outputPath);
        copyBaseFiles(outputPath);
        if (controllers.endsWith(SUFFIX)) {
            Path path = Paths.get(basePath, controllers.replace(".", "/").replace("/java", SUFFIX));
            RestControllerParser processor = new RestControllerParser(path.toFile());
            processor.start();
        } else {
            Path path = Paths.get(basePath, controllers.replace(".", "/"));
            RestControllerParser processor = new RestControllerParser(path.toFile());
            processor.start();
        }
    }

    public void writeFile(String relativePath, String content) throws IOException {
        String filePath = outputPath + File.separator + "src" + File.separator + "main" + File.separator + "java" +
                File.separator + relativePath;
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public void writeFilesToTest(String belongingPackage, String filename, String content) throws IOException {
        String filePath = outputPath + File.separator + "src" + File.separator + "test" + File.separator + "java"
                + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename;
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) throws IOException {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate();
    }

}
