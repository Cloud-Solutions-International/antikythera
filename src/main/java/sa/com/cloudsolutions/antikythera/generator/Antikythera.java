package sa.com.cloudsolutions.antikythera.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.RestControllerParser;
import sa.com.cloudsolutions.antikythera.parser.ServicesParser;

import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Antikythera {
    private static final Logger logger = LoggerFactory.getLogger(Antikythera.class);

    public static final String POM_XML = "pom.xml";
    public static final String SRC = "src";
    private static final String PACKAGE_PATH = "src/main/java/sa/com/cloudsolutions/antikythera";
    private static final String SUFFIX = ".java";

    private final String basePackage;
    private final String basePath;
    private final Collection<String> controllers;
    private final Collection<String> services;
    private final String outputPath;

    private static Antikythera instance;

    private Antikythera() {
        basePath = Settings.getProperty(Constants.BASE_PATH, String.class).orElse(null);
        basePackage = Settings.getProperty(Constants.BASE_PACKAGE, String.class).orElse(null);
        outputPath = Settings.getProperty(Constants.OUTPUT_PATH, String.class).orElse(null);
        controllers = Settings.getPropertyList(Constants.CONTROLLERS, String.class);
        services = Settings.getPropertyList(Constants.SERVICES, String.class);
    }

    public static Antikythera getInstance() throws IOException {
        if (instance == null) {
            Settings.loadConfigMap();

            instance = new Antikythera();
        }
        return instance;
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
        String testPath = PACKAGE_PATH.replace("main","test");
        copyPom();
        copyTemplate("TestHelper.java", testPath, "base");
        copyTemplate("Configurations.java", testPath,  "configurations");

        Path pathToCopy = Paths.get(outputPath, SRC, "test", "resources");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get(SRC,"test", "resources"), pathToCopy);

        pathToCopy = Paths.get(outputPath, PACKAGE_PATH, "constants");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get(PACKAGE_PATH, "constants"), pathToCopy);

        pathToCopy = Paths.get(outputPath, PACKAGE_PATH, "configurations");
        Files.createDirectories(pathToCopy);
    }

    /**
     * Generate tests for the controllers
     * @throws IOException if any of the files associated with the application under test cannot be read, or
     *      if the output folder cannot be written to
     * @throws XmlPullParserException if attempts to convert the POM file to an xml tree fail
     * @throws EvaluatorException if evaluating java expressions in the AUT code fails.
     */
    public void generateApiTests() throws IOException, XmlPullParserException, EvaluatorException {
        for (String controller : controllers) {

            String controllersCleaned = controller.replace(".java","").split("#")[0];
            RestControllerParser processor = new RestControllerParser(controllersCleaned);
            processor.start();
        }
    }

    public void writeFilesToTest(String belongingPackage, String filename, String content) throws IOException {
        String filePath = outputPath + File.separator + SRC + File.separator + "test" + File.separator + "java"
                + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename;

        writeFile(filePath, content);
    }

    public void writeFile(String filePath, String content) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    public static void main(String[] args) throws IOException, XmlPullParserException, EvaluatorException {
        Antikythera antk = Antikythera.getInstance();
        antk.preProcess();
        antk.generateApiTests();
        RestControllerParser.Stats stats = RestControllerParser.getStats();

        logger.info("Processed {} controllers", stats.getControllers());
        logger.info("Processed {} methods", stats.getMethods());
        logger.info("Generated {} tests", stats.getTests());

        antk.generateUnitTests();
    }

    public void preProcess() throws IOException, XmlPullParserException {
        CopyUtils.createMavenProjectStructure(basePackage, outputPath);
        copyBaseFiles(outputPath);

        AbstractCompiler.preProcess();

    }

    private void generateUnitTests() throws IOException {
        for (String service : services) {
            String[] parts = service.split("#");
            String servicesCleaned = parts[0];
            if (parts.length == 2) {
                ServicesParser processor = new ServicesParser(parts[0]);
                processor.start(parts[1]);
                processor.writeFiles();
            } else {
                ServicesParser processor = new ServicesParser(servicesCleaned);
                processor.start();
                processor.writeFiles();
            }
        }
    }
}
