package sa.com.cloudsolutions.antikythera.generator;

import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.RestControllerParser;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.cloud.api.constants.Constants;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProjectGenerator {
    public static final String POM_XML = "pom.xml";
    public static final String SRC = "src";
    private static final String SUFFIX = ".java";

    private final String basePackage;
    private final String basePath;
    private final String controllers;
    private final String outputPath;

    private static ProjectGenerator instance;


    private ProjectGenerator() {
        basePath = Settings.getProperty(Constants.BASE_PATH).toString();
        basePackage = Settings.getProperty(Constants.BASE_PACKAGE).toString();
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH).toString();
        controllers = Settings.getProperty(Constants.CONTROLLERS).toString();
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
                path + File.separator + SRC + File.separator + "main" + File.separator + "java" + File.separator + basePackagePath,
                path + File.separator + SRC + File.separator + "main" + File.separator + "resources",
                path + File.separator + SRC + File.separator + "test" + File.separator + "java" + File.separator + basePackagePath,
                path + File.separator + SRC + File.separator + "test" + File.separator + "resources"
        };

        for (String dir : directories) {
            Files.  createDirectories(Paths.get(dir));
        }
    }

    /**
     * Copy template file to the specified path
     * @param filename the name of the template file to copy
     * @param subPath the path components
     * @throws IOException thrown if the copy operation failed
     */
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

    /**
     * Copy the pom.xml file to the specified path injecting the dependencies as needed.
     *
     * The configuration file needs to have a dependencies section that provides the list of
     * artifactids that need to be included in the output pom file. If these dependencies
     * require any variables, those are copied as well.
     *
     * The primary source file is the file from the template folder. The dependencies are
     * supposed to be listed in the pom file of the application under test.
     * @throws IOException if the POM file cannot be copied
     * @throws XmlPullParserException if the POM file cannot be converted to an XML Tree
     */
    public void copyPom() throws IOException, XmlPullParserException {
        String[] dependencies = Settings.getArtifacts();
        if (dependencies.length == 0) {
            copyTemplate(POM_XML);
        } else {

            Path destinationPath = Path.of(outputPath, POM_XML);

            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model templateModel = reader.read(getClass().getClassLoader().getResourceAsStream("templates/pom.xml"));
            Path p = basePath.contains("src/main/java")
                    ? Paths.get(basePath.replace("/src/main/java", ""), POM_XML)
                    : Paths.get(basePath, POM_XML);

            Model srcModel = reader.read(new FileReader(p.toFile()));

            List<Dependency> srcDependencies = srcModel.getDependencies();
            for (String dep : dependencies) {
                for (Dependency dependency : srcDependencies) {
                    if (dependency.getArtifactId().equals(dep)) {
                        templateModel.addDependency(dependency);
                        copyDependencyProperties(srcModel, templateModel, dependency);
                    }
                }
            }

            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(new FileWriter(destinationPath.toFile()), templateModel);
        }
    }

    /**
     * Copy the properties of the dependency from the source model to the template model
     *
     * @param srcModel the pom file model from the application under test
     * @param templateModel from the template folder
     * @param dependency the dependency that may or may not have a property
     */
    private void copyDependencyProperties(Model srcModel, Model templateModel, Dependency dependency) {
        Properties srcProperties = srcModel.getProperties();
        Properties templateProperties = templateModel.getProperties();
        Pattern pattern = Pattern.compile("\\$\\{(.+?)\\}");

        // Check all fields of the dependency for property references
        String[] fields = {dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getScope()};
        for (String field : fields) {
            if (field != null) {
                Matcher matcher = pattern.matcher(field);
                while (matcher.find()) {
                    String propertyName = matcher.group(1);
                    if (srcProperties.containsKey(propertyName) && !templateProperties.containsKey(propertyName)) {
                        templateProperties.setProperty(propertyName, srcProperties.getProperty(propertyName));
                    }
                }
            }
        }
    }

    public static void copyFolder(Path source, Path destination) throws IOException {
        if (!Files.exists(destination)) {
            Files.createDirectories(destination);
        }

        var paths = Files.walk(source).iterator();
        while (paths.hasNext()) {
            Path sourcePath = paths.next();
            Path targetPath = destination.resolve(source.relativize(sourcePath));
            if (Files.isDirectory(sourcePath)) {
                if (!Files.exists(targetPath)) {
                    Files.createDirectories(targetPath);
                }
            } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

    }

    private void copyBaseFiles(String outputPath) throws IOException, XmlPullParserException {
        copyPom();
        copyTemplate("TestHelper.java", SRC, "test", "java", "com", "cloud", "api", "base");
        copyTemplate("Configurations.java", SRC, "main", "java", "com", "cloud", "api", "configurations");

        Path pathToCopy = Paths.get(outputPath, SRC, "test", "resources");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get(SRC,"test", "resources"), pathToCopy);

        pathToCopy = Paths.get(outputPath, SRC, "main", "java", "com", "cloud", "api", "constants");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get(SRC,"main", "java", "com", "cloud", "api", "constants"), pathToCopy);

        pathToCopy = Paths.get(outputPath, SRC, "main", "java", "com", "cloud", "api", "configurations");
        Files.createDirectories(pathToCopy);

    }

    public void generate() throws IOException, XmlPullParserException, EvaluatorException {
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
        String filePath = outputPath + File.separator + SRC + File.separator + "main" + File.separator + "java" +
                File.separator + relativePath;
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public void writeFilesToTest(String belongingPackage, String filename, String content) throws IOException {
        String filePath = outputPath + File.separator + SRC + File.separator + "test" + File.separator + "java"
                + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename;
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) throws IOException, XmlPullParserException, EvaluatorException {
        ProjectGenerator.getInstance().generate();
    }
}