package sa.com.cloudsolutions.antikythera.generator;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.RestControllerParser;
import sa.com.cloudsolutions.antikythera.parser.ServicesParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Antikythera {
    public static final String POM_XML = "pom.xml";
    public static final String SRC = "src";
    private static final Logger logger = LoggerFactory.getLogger(Antikythera.class);
    private static final String PACKAGE_PATH = "src/main/java/sa/com/cloudsolutions/antikythera";
    private static Antikythera instance;
    private final Collection<String> controllers;
    private final Collection<String> services;
    private Model pomModel;

    private Antikythera() {
        controllers = Settings.getPropertyList(Settings.CONTROLLERS, String.class);
        services = Settings.getPropertyList(Settings.SERVICES, String.class);
    }

    public static Antikythera getInstance() {
        if (instance == null) {
            try {
                Settings.loadConfigMap();
                instance = new Antikythera();
                instance.readPomFile();
            } catch (IOException e) {
                throw new AntikytheraException("Failed to initialize Antikythera", e);
            } catch (XmlPullParserException xe) {
                logger.error("Could not parse the POM file", xe);
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
        RestControllerParser.Stats stats = RestControllerParser.getStats();

        logger.info("Processed {} controllers", stats.getControllers());
        logger.info("Processed {} methods", stats.getMethods());
        logger.info("Generated {} tests", stats.getTests());

        antk.generateUnitTests();
    }

    /**
     * Copy a template file to the specified path
     *
     * @param filename the name of the template file to copy
     * @param subPath  the path components
     * @throws IOException thrown if the copy operation failed
     */
    private void copyTemplate(String filename, String... subPath) throws IOException {
        Path destinationPath = Path.of(Settings.getOutputPath(), subPath);     // Path where template file should be copied into
        Files.createDirectories(destinationPath);
        try (InputStream sourceStream = getClass().getClassLoader().getResourceAsStream("templates/" + filename);
             FileOutputStream destStream = new FileOutputStream(destinationPath + File.separator + filename);
             FileChannel destChannel = destStream.getChannel()) {
            if (sourceStream == null) {
                throw new IOException("Template file not found");
            }
            destChannel.transferFrom(Channels.newChannel(sourceStream), 0, Long.MAX_VALUE);
        }
    }

    /**
     * Copy the pom.xml file to the specified path, injecting the dependencies as needed.
     * <p>
     * The configuration file needs to have a dependencies section that provides the list of
     * `artifactids` that need to be included in the output pom file. If these dependencies
     * require any variables, those are copied as well.
     * <p>
     * The primary source file is the file from the template folder. The dependencies are
     * supposed to be listed in the pom file of the application under test.
     *
     * @throws IOException            if the POM file cannot be copied
     * @throws XmlPullParserException if the POM file cannot be converted to an XML Tree
     */
    public void copyPom() throws IOException, XmlPullParserException {
        String[] dependencies = Settings.getArtifacts();
        if (dependencies.length == 0) {
            copyTemplate(POM_XML);
        } else {
            Path destinationPath = Path.of(Settings.getOutputPath(), POM_XML);

            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model templateModel = reader.read(getClass().getClassLoader().getResourceAsStream("templates/pom.xml"));

            List<Dependency> srcDependencies = pomModel.getDependencies();
            for (String dep : dependencies) {
                for (Dependency dependency : srcDependencies) {
                    if (dependency.getArtifactId().equals(dep)) {
                        templateModel.addDependency(dependency);
                        copyDependencyProperties(pomModel, templateModel, dependency);
                    }
                }
            }

            MavenXpp3Writer writer = new MavenXpp3Writer();
            writer.write(new FileWriter(destinationPath.toFile()), templateModel);
        }
    }

    private void readPomFile() throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String basePath = Settings.getBasePath();
        Path p = null;
        if (basePath.contains("src/main/java")) {
            p = Paths.get(basePath.replace("/src/main/java", ""), POM_XML);
        } else if (basePath.contains("src/test/java")) {
            p = Paths.get(basePath.replace("/src/test/java", ""), POM_XML);
        } else {
            p = Paths.get(basePath, POM_XML);
        }

        pomModel = reader.read(new FileReader(p.toFile()));
    }

    /**
     * Copy the properties of the dependency from the source model to the template model
     *
     * @param srcModel      the pom file model from the application under test
     * @param templateModel from the template folder
     * @param dependency    the dependency that may or may not have a property
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

    private void copyBaseFiles(String outputPath) throws IOException, XmlPullParserException {
        String testPath = PACKAGE_PATH.replace("main", "test");
        copyPom();
        copyTemplate("TestHelper.java", testPath, "base");
        copyTemplate("Configurations.java", testPath, "configurations");

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

            String controllersCleaned = controller.replace(".java", "").split("#")[0];
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

    public String[] getJarPaths() {
        return getAllJarPathsWithTransitives();
    }

    public String[] getAllJarPathsWithTransitives() {
        List<String> jarPaths = new ArrayList<>();

        Optional<String> m2Optional = Settings.getProperty("variables.m2_folder", String.class);
        if (m2Optional.isEmpty()) return new String[0];

        String m2 = m2Optional.get();

        try {
            org.apache.maven.model.Repository localRepo = new org.apache.maven.model.Repository();
            localRepo.setId("local");
            localRepo.setUrl("file://" + m2);

            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
            locator.addService(TransporterFactory.class, FileTransporterFactory.class);
            locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

            RepositorySystem system = locator.getService(RepositorySystem.class);

            DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
            LocalRepository local = new LocalRepository(m2);
            session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, local));

            RemoteRepository central = new RemoteRepository.Builder("central", "default", "https://repo.maven.apache.org/maven2").build();

            List<Dependency> dependencies = pomModel.getDependencies();
            for (Dependency dep : dependencies) {
                Artifact artifact = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), "jar", dep.getVersion());
                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot(new org.eclipse.aether.graph.Dependency(artifact, "compile"));
                collectRequest.addRepository(central);

                DependencyRequest dependencyRequest = new DependencyRequest();
                dependencyRequest.setCollectRequest(collectRequest);

                List<ArtifactResult> results = system.resolveDependencies(session, dependencyRequest).getArtifactResults();

                for (ArtifactResult result : results) {
                    File file = result.getArtifact().getFile();
                    if (file != null && file.exists()) {
                        jarPaths.add(file.getAbsolutePath());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Dependency resolution failed", e);
            return new String[0];
        }

        return jarPaths.toArray(new String[0]);
    }

    private void addJarPath(Dependency dependency, Properties properties, String m2, List<String> jarPaths) {
        String groupIdPath = dependency.getGroupId().replace('.', '/');
        String artifactId = dependency.getArtifactId();
        String version = dependency.getVersion();

        // Handle property variables in version
        if (version != null && version.startsWith("${") && version.endsWith("}")) {
            String propertyName = version.substring(2, version.length() - 1);
            version = properties.getProperty(propertyName);
        }

        if (version == null || version.isEmpty()) {
            version = findLatestVersion(groupIdPath, artifactId, m2);
        }

        if (version != null) {
            Path p = Paths.get(m2, groupIdPath, artifactId, version,
                    artifactId + "-" + version + ".jar");
            if (Files.exists(p)) {
                jarPaths.add(p.toString());
                System.out.println(p);
            } else {
                logger.warn("Jar not found: {}", p);
            }
        }
    }

    private String findLatestVersion(String groupIdPath, String artifactId, String m2) {
        Path artifactPath = Paths.get(m2, groupIdPath, artifactId);

        if (!Files.exists(artifactPath) || !Files.isDirectory(artifactPath)) {
            return null;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifactPath)) {
            List<String> versions = new ArrayList<>();
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    String version = path.getFileName().toString();
                    Path p = Paths.get(m2, groupIdPath, artifactId, version, artifactId + "-" + version + ".jar");
                    if (! (version.startsWith("${") || version.equals("unknown")) && Files.exists(p)) {
                        versions.add(version);
                    }
                }
            }

            return versions.stream().max(this::compareVersions).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }


    private int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (part1 != part2) {
                return Integer.compare(part1, part2);
            }
        }
        return 0;
    }
    public void preProcess() throws IOException, XmlPullParserException {
        CopyUtils.createMavenProjectStructure(Settings.getBasePackage(), Settings.getOutputPath());
        copyBaseFiles(Settings.getOutputPath());

        AbstractCompiler.preProcess();

    }

    private void generateUnitTests() throws IOException {
        for (String service : services) {
            String[] parts = service.split("#");
            ServicesParser processor = new ServicesParser(parts[0]);
            if (parts.length == 2) {
                processor.start(parts[1]);
            } else {
                processor.start();
            }
            processor.writeFiles();
        }
    }
}
