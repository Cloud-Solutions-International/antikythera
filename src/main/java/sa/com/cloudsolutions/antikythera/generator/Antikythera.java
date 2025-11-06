package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.expr.Expression;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockConfigReader;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.MavenHelper;
import sa.com.cloudsolutions.antikythera.parser.RestControllerParser;
import sa.com.cloudsolutions.antikythera.parser.ServicesParser;
import sa.com.cloudsolutions.antikythera.parser.Stats;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@SuppressWarnings("java:S6548")
public class Antikythera {

    public static final String SRC = "src";
    private static final Logger logger = LoggerFactory.getLogger(Antikythera.class);
    private static final String PACKAGE_PATH = "src/main/java/sa/com/cloudsolutions/antikythera";
    public static final String JAVA = ".java";
    private static Antikythera instance;
    private final Collection<String> controllers;
    private final Collection<String> services;
    private static MavenHelper mavenHelper;


    private Antikythera() {
        controllers = Settings.getPropertyList(Settings.CONTROLLERS, String.class);
        services = Settings.getPropertyList(Settings.SERVICES, String.class);
    }

    public static Antikythera getInstance() {
        if (instance == null) {
            try {
                Settings.loadConfigMap();
                instance = new Antikythera();
                mavenHelper = new MavenHelper();
                mavenHelper.readPomFile();
                mavenHelper.buildJarPaths();
            } catch (IOException e) {
                throw new AntikytheraException("Failed to initialize Antikythera", e);
            } catch (XmlPullParserException xe) {
                logger.error("Could not parse the POM file", xe);
            }
            try {
                Map<String, List<Expression>> customMocks = MockConfigReader.readDefaultMockExpressions();
                if (!customMocks.isEmpty()) {
                    MockingRegistry.setCustomMockExpressions(customMocks);
                }
            } catch (IllegalArgumentException e) {
                // can safely ignore this exception, as it is thrown when no mock configuration is found
            }
        }
        return instance;
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

    public static void main(String[] args) throws IOException, XmlPullParserException, EvaluatorException {
        Antikythera antk = Antikythera.getInstance();
        antk.preProcess();
        antk.generateApiTests();
        Stats stats = RestControllerParser.getStats();

        logger.info("Processed {} controllers", stats.getControllers());
        logger.info("Processed {} methods", stats.getMethods());
        logger.info("Generated {} tests", stats.getTests());

        antk.generateUnitTests();
    }

    private void copyBaseFiles(String outputPath) throws IOException, XmlPullParserException {
        String testPath = PACKAGE_PATH.replace("main", "test");
        mavenHelper.copyPom();
        String name = mavenHelper.copyTemplate("TestHelper.txt", testPath, "base");
        String java = name.replace(".txt", JAVA);
        File f = new File(name);
        f.renameTo(new File(java));

        mavenHelper.copyTemplate("Configurations.java", testPath, "configurations");

        Path pathToCopy = Paths.get(outputPath, SRC, "test", "resources");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get(SRC, "test", "resources"), pathToCopy);

        pathToCopy = Paths.get(outputPath, PACKAGE_PATH, "constants");
        Files.createDirectories(pathToCopy);
        copyFolder(Paths.get(PACKAGE_PATH, "constants"), pathToCopy);

        pathToCopy = Paths.get(outputPath, PACKAGE_PATH, "configurations");
        Files.createDirectories(pathToCopy);
    }

    /**
     * Generate tests for the controllers
     *
     * @throws IOException            if any of the files associated with the application under test cannot be read, or
     *                                if the output folder cannot be written to
     * @throws XmlPullParserException if attempts to convert the POM file to an xml tree fail
     * @throws EvaluatorException     if evaluating java expressions in the AUT code fails.
     */
    public void generateApiTests() throws IOException, XmlPullParserException, EvaluatorException {
        for (String controller : controllers) {

            String controllersCleaned = controller.replace(JAVA, "").split("#")[0];
            RestControllerParser processor = new RestControllerParser(controllersCleaned);
            processor.start();
        }
    }

    public void writeFilesToTest(String belongingPackage, String filename, String content) throws IOException {
        String filePath = Settings.getOutputPath() + File.separator + SRC + File.separator + "test" + File.separator + "java"
                + File.separator + belongingPackage.replace(".", File.separator) + File.separator + filename;

        writeFile(filePath, content);
    }

    public void writeFile(String filePath, String content) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        Files.createDirectories(parentDir.toPath());
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
            writer.flush();
        }
    }

    public void preProcess() throws IOException, XmlPullParserException {
        CopyUtils.createMavenProjectStructure(Settings.getBasePackage(), Settings.getOutputPath());
        copyBaseFiles(Settings.getOutputPath());

        AbstractCompiler.preProcess();

    }

    private void generateUnitTests() throws IOException {
        for (String service : services) {
            String[] parts = service.split("#");
            String path = parts[0];

            // Check if it's a source file in compilation units
            if (AntikytheraRunTime.getCompilationUnit(path) != null) {
                processService(path, parts);
            } else {
                // Might be a package - check directory
                Path packagePath = Paths.get(Settings.getBasePath(),
                    path.replace('.', File.separatorChar));

                if (Files.isDirectory(packagePath)) {
                    try (var paths = Files.walk(packagePath)) {
                        paths.filter(Files::isRegularFile)
                             .filter(p -> p.toString().endsWith(JAVA))
                             .forEach(p -> {
                                 String relativePath = Paths.get(Settings.getBasePath())
                                     .relativize(p).toString()
                                     .replace(File.separatorChar, '.')
                                     .replaceAll("\\.java$", "");
                                 try {
                                     processService(relativePath, parts);
                                 } catch (IOException e) {
                                     logger.error("Failed to process service {}", relativePath, e);
                                 }
                             });
                    }
                } else {
                    logger.warn("Service path {} not found as file or package", path);
                }
            }
        }
    }

    private void processService(String servicePath, String[] parts) throws IOException {
        logger.info("******************");
        logger.info("Processing service {}", servicePath);

        ServicesParser processor = new ServicesParser(servicePath);
        if (parts.length == 2) {
            processor.start(parts[1]);
        } else {
            processor.start();
        }
        processor.writeFiles();
    }
}
