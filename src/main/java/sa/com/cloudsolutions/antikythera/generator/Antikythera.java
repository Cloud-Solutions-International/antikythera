package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
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
import java.util.Optional;

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
                configureStaticJavaParser();
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

    private static void configureStaticJavaParser() {
        ParserConfiguration parserConfig = new ParserConfiguration();
        parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        StaticJavaParser.setConfiguration(parserConfig);
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
        if (name == null) {
            return;
        }

        String java = name.replace(".txt", JAVA);
        File f = new File(name);
        if (f.renameTo(new File(java))) {

            mavenHelper.copyTemplate("Configurations.java", testPath, "configurations");

            Path pathToCopy = Paths.get(outputPath, SRC, "test", "resources");
            Files.createDirectories(pathToCopy);
            copyFolder(Paths.get(SRC, "test", "resources"), pathToCopy);

            pathToCopy = Paths.get(outputPath, PACKAGE_PATH, "constants");
            Files.createDirectories(pathToCopy);
            /*
             * Todo resurrect the Constants class that as in the com.sa.com.cloudsolutions.antikythera.constants package
             *  and move it to the resources
            copyFolder(Paths.get(PACKAGE_PATH, "constants"), pathToCopy);
            */
            pathToCopy = Paths.get(outputPath, PACKAGE_PATH, "configurations");
            Files.createDirectories(pathToCopy);
        }
        else {
            throw  new AntikytheraException("Could not copy resources");
        }
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

            if (AntikytheraRunTime.getCompilationUnit(path) != null) {
                processService(path, parts);
            } else {
                processPackage(path, parts);
            }
        }
    }

    private void processPackage(String packagePath, String[] parts) throws IOException {
        Path dirPath = Paths.get(Settings.getBasePath(), packagePath.replace('.', File.separatorChar));
        
        if (!Files.isDirectory(dirPath)) {
            logger.warn("Service path {} not found as file or package", packagePath);
            return;
        }

        try (var paths = Files.walk(dirPath)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(JAVA))
                 .forEach(file -> processServiceFile(file, parts));
        }
    }

    private void processServiceFile(Path file, String[] parts) {
        try {
            String className = getClassNameFromFile(file);
            if (className != null) {
                processService(className, parts);
            }
        } catch (IOException e) {
            logger.error("Failed to process service file {}", file, e);
        }
    }

    private String getClassNameFromFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file.toFile());
            String fileName = file.getFileName().toString().replace(JAVA, "");
            Optional<String> packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString);
            
            String className = packageName.map(pkg -> pkg + "." + fileName).orElse(fileName);
            
            if (AntikytheraRunTime.getCompilationUnit(className) == null) {
                logger.debug("Skipping {} - class not found in compilation unit cache. File package ({}) may not match directory structure.", 
                    className, packageName.orElse("default package"));
                return null;
            }
            
            return className;
        } catch (Exception e) {
            logger.debug("Could not parse file {} to determine class name", file, e);
            return null;
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
