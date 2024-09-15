package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ProjectGenerator {
    private final String basePackage;
    private final String basePath;
    private final String controllers;
    private final String outputPath;
    private static final String SUFFIX = ".java";

    private static ProjectGenerator instance;

    @JacksonXmlRootElement(localName = "project", namespace = "http://maven.apache.org/POM/4.0.0")
    public static class ProjectNode {}

    private ProjectGenerator() {
        basePath = Settings.getProperty("BASE_PATH");
        basePackage = Settings.getProperty("BASE_PACKAGE");
        outputPath = Settings.getProperty("OUTPUT_PATH");
        controllers = Settings.getProperty("CONTROLLERS");
    }

    public static ProjectGenerator getInstance() throws IOException {
        if (instance == null) {
            Settings.loadConfigMap();

            instance = new ProjectGenerator();
        }
        return instance;
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

    public void copyPom() throws IOException {
        if (Settings.getProperty("DEPENDENCIES") == null) {
            copyTemplate("pom.xml");
        } else {
            String[] dependencies = Settings.getProperty("DEPENDENCIES").split(",");
            Path destinationPath = Path.of(outputPath, "pom.xml");     // Path where template file should be copied into
            if(!Files.exists(destinationPath.getParent())) {
                Files.createDirectories(destinationPath.getParent());
            }

            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonNode template = xmlMapper.readTree(getClass().getClassLoader().getResourceAsStream("templates/pom.xml"));
            Path p = basePath.contains("src/main/java")
                    ? Paths.get(basePath.replace("/src/main/java", ""), "pom.xml")
                    : Paths.get(basePath, "pom.xml");

            JsonNode src = xmlMapper.readTree(p.toFile());

            for (String dep : dependencies) {
                src.path("dependencies").path("dependency").forEach(dependency -> {
                    if (dependency.path("artifactId").asText().equals(dep)) {
                        ((com.fasterxml.jackson.databind.node.ArrayNode) template.path("dependencies").path("dependency")).add(dependency);
                    }
                });
            }
            xmlMapper.addMixIn(JsonNode.class, ProjectNode.class);
            xmlMapper.writerWithDefaultPrettyPrinter().writeValue(new File(destinationPath.toString()), template);
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
        copyPom();
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
        ProjectGenerator.getInstance().generate();
    }
}
