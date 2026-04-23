package sa.com.cloudsolutions.antikythera.parser;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the application-under-test's {@code pom.xml}, resolves dependency JARs
 * from the local Maven repository, and provides POM-related utilities such as
 * Java version detection and template POM generation for the output project.
 */
public class MavenHelper {
    public static final String POM_XML = "pom.xml";
    private static final Logger logger = LoggerFactory.getLogger(MavenHelper.class);
    private static final Map<String, Artifact> artifacts = new HashMap<>();
    private static boolean jarPathsBuilt = false;
    private Model pomModel;
    private Path pomPath;

    /**
     * Gets the paths to all JAR files identified as dependencies.
     * Initializes the paths if they haven't been built yet.
     *
     * @return an array of absolute file paths to JARs
     */
    public static String[] getJarPaths() {
        if (!jarPathsBuilt) {
            initializeJarPaths();
        }
        List<String> paths = new ArrayList<>();
        for (Artifact artifact : artifacts.values()) {
            if (artifact.jarFile != null) {
                paths.add(artifact.jarFile);
            }
        }
        return paths.toArray(new String[] {});
    }

    private static synchronized void initializeJarPaths() {
        if (jarPathsBuilt) return;
        try {
            MavenHelper helper = new MavenHelper();
            helper.readPomFile();
            logger.debug("Read pom from: {}", helper.pomPath);
            logger.debug("Found {} dependencies in pom", 
                helper.pomModel != null ? helper.pomModel.getDependencies().size() : 0);
            helper.buildJarPaths();
            logger.debug("Built {} jar paths", artifacts.size());
            jarPathsBuilt = true;
        } catch (Exception e) {
            logger.warn("Could not build JAR paths: {}", e.getMessage());
            jarPathsBuilt = true; // Don't retry on failure
        }
    }

    private static void addDependency(String m2, String groupIdPath, String artifactId, String version)
            throws IOException, XmlPullParserException {
        Path p = Paths.get(m2, groupIdPath, artifactId, version,
                artifactId + "-" + version + ".jar");
        if (Files.exists(p)) {
            String key = groupIdPath.replace('/', '.') + ":" + artifactId;
            Artifact artifact = artifacts.get(key);
            if (artifact == null) {
                artifacts.put(key, new Artifact(artifactId, version, p.toString()));

                Path pom = Paths.get(m2, groupIdPath, artifactId, version,
                        artifactId + "-" + version + ".pom");
                if (Files.exists(pom)) {
                    MavenHelper pomHelper = new MavenHelper();
                    pomHelper.readPomFile(pom);
                    pomHelper.buildJarPaths();
                }
            }
        } else {
            logger.debug("Jar not found: {}", p);
        }
    }

    /**
     * Reads the pom.xml file located in the configured base path or its parent directories.
     *
     * @return the parsed Maven Model of the pom.xml
     * @throws IOException            if the file cannot be read
     * @throws XmlPullParserException if the file cannot be parsed
     */
    public Model readPomFile() throws IOException, XmlPullParserException {
        String basePath = Settings.getBasePath();
        Path p = null;
        if (basePath.contains("src/main/java")) {
            p = Paths.get(basePath.replace("/src/main/java", ""), POM_XML);
        } else if (basePath.contains("src/test/java")) {
            p = Paths.get(basePath.replace("/src/test/java", ""), POM_XML);
        } else {
            p = Paths.get(basePath, POM_XML);
        }

        // Parent fallback logic from PomUtils
        if (!p.toFile().exists()) {
            Path parent = p.getParent();
            if (parent != null) {
                p = parent.getParent().resolve(POM_XML);
            }
        }

        pomPath = p;
        readPomFile(p);
        return pomModel;
    }

    /**
     * Reads a POM file from the specified path.
     *
     * @param p the path to the pom.xml file
     * @throws IOException if the file cannot be read
     * @throws XmlPullParserException if the file cannot be parsed
     */
    public void readPomFile(Path p) throws IOException, XmlPullParserException {
        pomPath = p.toAbsolutePath();
        MavenXpp3Reader reader = new MavenXpp3Reader();

        // Read file content as bytes to handle BOM and encoding issues
        // Some POM files have UTF-8 BOM but declare ISO-8859-1 encoding, which causes parser errors
        byte[] fileBytes = Files.readAllBytes(p);

        // Check for UTF-8 BOM (EF BB BF) and remove it if present
        int startOffset = 0;
        if (fileBytes.length >= 3 &&
            fileBytes[0] == (byte)0xEF &&
            fileBytes[1] == (byte)0xBB &&
            fileBytes[2] == (byte)0xBF) {
            startOffset = 3; // Skip BOM
        }

        // Read content as UTF-8 string (ignoring any incorrect encoding declaration)
        String content = new String(fileBytes, startOffset, fileBytes.length - startOffset, StandardCharsets.UTF_8);

        // Fix encoding declaration if it's incorrect (e.g., ISO-8859-1 with UTF-8 BOM)
        // Replace any encoding declaration with UTF-8 to match the actual file encoding
        content = content.replaceFirst(
            "(<\\?xml[^>]*encoding\\s*=\\s*[\"'])[^\"']+([\"'])",
            "$1UTF-8$2"
        );

        // Parse the corrected content
        try (StringReader sr = new StringReader(content)) {
            pomModel = reader.read(sr);
        }
    }

    /**
     * Gets the path to the currently loaded pom.xml file.
     *
     * @return the Path of the pom.xml, or {@code null} if no POM has been loaded yet
     */
    public Path getPomPath() {
        return pomPath;
    }

    /**
     * Write POM model to the tracked path.
     *
     * @param model the Maven model to write
     * @throws IOException if writing fails
     */
    public void writePomModel(Model model) throws IOException {
        if (pomPath == null) {
            throw new IllegalStateException("POM path not set. Call getPomModel() or setPomPath() first.");
        }
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileWriter fileWriter = new FileWriter(pomPath.toFile())) {
            writer.write(fileWriter, model);
        }
        this.pomModel = model;
    }

    /**
     * Write POM model to a specific path.
     *
     * @param path  the path to write to
     * @param model the Maven model to write
     * @throws IOException if writing fails
     */
    public void writePomModel(Path path, Model model) throws IOException {
        MavenXpp3Writer writer = new MavenXpp3Writer();
        try (FileWriter fileWriter = new FileWriter(path.toFile())) {
            writer.write(fileWriter, model);
        }
        this.pomPath = path;
        this.pomModel = model;
    }

    /**
     * Copy the pom.xml template to the given destination directory, injecting any configured
     * dependencies from the application-under-test's POM.
     *
     * <p>The destination directory is explicit so callers can place the generated pom.xml at
     * the correct project root rather than inside a java source tree.</p>
     *
     * @param destinationDir  the directory into which {@code pom.xml} will be written
     * @throws IOException            if the POM file cannot be copied
     * @throws XmlPullParserException if the POM file cannot be converted to an XML Tree
     */
    public void copyPom(Path destinationDir) throws IOException, XmlPullParserException {
        Files.createDirectories(destinationDir);
        String[] dependencies = Settings.getArtifacts();
        if (dependencies.length == 0) {
            sa.com.cloudsolutions.antikythera.generator.CopyUtils.copyTemplate(
                    POM_XML, destinationDir.toString());
        } else {
            Path destinationPath = destinationDir.resolve(POM_XML);

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

    /**
     * Copy the pom.xml template to {@code Settings.getOutputPath()}.
     *
     * @throws IOException            if the POM file cannot be copied
     * @throws XmlPullParserException if the POM file cannot be converted to an XML Tree
     * @deprecated Use {@link #copyPom(Path)} with an explicit destination directory so that
     *             the generated pom.xml lands at the intended project root rather than
     *             implicitly inside {@code output_path}. This method will be removed in a
     *             future release.
     * @since 1.0
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public void copyPom() throws IOException, XmlPullParserException {
        copyPom(Path.of(Settings.getOutputPath()));
    }

    /**
     * Copy a template file to the specified path.
     *
     * @param filename the name of the template file to copy
     * @param subPath  the path components
     * @throws IOException thrown if the copy operation failed
     * @deprecated Use {@link sa.com.cloudsolutions.antikythera.generator.CopyUtils#copyTemplate(String, String, String...)}
     *             instead. Template copying is not a Maven concern. This method will be removed in a
     *             future release.
     * @since 1.0
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public String copyTemplate(String filename, String... subPath) throws IOException {
        return sa.com.cloudsolutions.antikythera.generator.CopyUtils.copyTemplate(
                filename, Settings.getOutputPath(), subPath);
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

    /**
     * Resolves dependencies from the POM model and builds the list of JAR paths.
     * Looks for JARs in the local Maven repository.
     */
    public void buildJarPaths() {

        if (pomModel != null) {
            List<Dependency> dependencies = pomModel.getDependencies();

            // Get m2 folder from settings, or fall back to default ~/.m2/repository
            String m2 = Settings.getProperty("variables.m2_folder", String.class)
                    .orElseGet(() -> {
                        String home = System.getProperty("user.home");
                        return home != null ? home + "/.m2/repository" : null;
                    });
            
            if (m2 != null) {
                for (Dependency dependency : dependencies) {
                    try {
                        addJarPath(dependency, m2);
                    } catch (XmlPullParserException | IOException e) {
                        throw new AntikytheraException(e);
                    }
                }
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
                    if (!(version.startsWith("${") || version.equals("unknown")) && Files.exists(p)) {
                        versions.add(version);
                    }
                }
            }

            return versions.stream().max(MavenHelper::compareVersions).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Compares two version strings numerically.
     * Handles standard dot-separated version numbers (e.g., 1.2.3 vs 1.2.4).
     *
     * @param v1 the first version string
     * @param v2 the second version string
     * @return a negative integer, zero, or a positive integer as the first argument
     *         is less than, equal to, or greater than the second
     */
    public static int compareVersions(String v1, String v2) {
        try {
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
        } catch (NumberFormatException e) {
            // ignore invalid version formats
        }
        return 0;
    }

    private void addJarPath(Dependency dependency, String m2) throws XmlPullParserException, IOException {
        Properties properties = pomModel.getProperties();

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
            addDependency(m2, groupIdPath, artifactId, version);
        }
    }

    /**
     * Gets the current POM model, reading it from disk if not yet loaded.
     *
     * @return the Maven Model
     * @throws XmlPullParserException if parsing fails
     * @throws IOException            if reading fails
     */
    public Model getPomModel() throws XmlPullParserException, IOException {
        if (pomModel == null) {
            return readPomFile();
        }
        return pomModel;
    }

    /**
     * Extracts the Java source version from the POM model.
     * Checks the following properties in order:
     * <ol>
     *   <li>{@code maven.compiler.source}</li>
     *   <li>{@code maven.compiler.target}</li>
     *   <li>{@code java.version}</li>
     * </ol>
     * Property references (e.g., {@code ${java.version}}) are resolved automatically.
     * The legacy {@code 1.x} format (e.g., {@code 1.8}) is normalized to just the minor
     * version number (e.g., {@code 8}).
     *
     * @return the Java version as an integer (e.g., 8, 11, 17, 21), or 21 if not found
     */
    public int getJavaVersion() {
        if (pomModel == null) {
            return 21;
        }

        Properties properties = pomModel.getProperties();
        if (properties == null) {
            return 21;
        }

        String[] propertyNames = {
            "maven.compiler.source",
            "maven.compiler.target",
            "java.version"
        };

        for (String propertyName : propertyNames) {
            String value = properties.getProperty(propertyName);
            if (value != null && !value.isEmpty()) {
                value = resolveProperty(value, properties);
                if (value != null) {
                    int version = parseJavaVersion(value);
                    if (version > 0) {
                        return version;
                    }
                }
            }
        }

        return 21;
    }

    /**
     * Resolves a property value that may be a reference to another property.
     * For example, {@code ${java.version}} will be resolved to its actual value.
     */
    private static String resolveProperty(String value, Properties properties) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            String propertyName = value.substring(2, value.length() - 1);
            String resolved = properties.getProperty(propertyName);
            if (resolved != null && !resolved.isEmpty()) {
                return resolveProperty(resolved, properties);
            }
            return null;
        }
        return value;
    }

    /**
     * Parses a Java version string into an integer.
     * Handles both modern format (e.g., "17") and legacy format (e.g., "1.8").
     *
     * @param version the version string
     * @return the version as an integer, or -1 if parsing fails
     */
    static int parseJavaVersion(String version) {
        if (version == null || version.isEmpty()) {
            return -1;
        }
        try {
            if (version.startsWith("1.")) {
                return Integer.parseInt(version.substring(2));
            }
            return Integer.parseInt(version);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static class Artifact {
        String name;
        String version;
        String jarFile;

        Artifact(String name, String version, String jarFile) {
            this.name = name;
            this.version = version;
            this.jarFile = jarFile;
        }
    }
}
