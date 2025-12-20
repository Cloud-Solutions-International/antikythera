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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MavenHelper {
    public static final String POM_XML = "pom.xml";
    private static final Logger logger = LoggerFactory.getLogger(MavenHelper.class);
    private static final Map<String, Artifact> artifacts = new HashMap<>();
    private Model pomModel;
    private Path pomPath;

    public static String[] getJarPaths() {
        List<String> paths = new ArrayList<>();
        for (Artifact artifact : artifacts.values()) {
            if (artifact.jarFile != null) {
                paths.add(artifact.jarFile);
            }
        }

        return paths.toArray(new String[] {});
    }

    private static void addDependency(String m2, String groupIdPath, String artifactId, String version)
            throws IOException, XmlPullParserException {
        Path p = Paths.get(m2, groupIdPath, artifactId, version,
                artifactId + "-" + version + ".jar");
        if (Files.exists(p)) {
            Artifact artifact = artifacts.get(artifactId);
            if (artifact != null) {
                artifact.jarFile = p.toString();
                artifact.version = version;
            } else {
                artifacts.put(artifactId, new Artifact(artifactId, version, p.toString()));

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

    public void readPomFile() throws IOException, XmlPullParserException {
        String basePath = Settings.getBasePath();
        Path p;
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
    }

    private void readPomFile(Path p) throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        pomModel = reader.read(new FileReader(p.toFile()));
    }

    /**
     * Get POM model, reading it if not already loaded.
     * 
     * @return the Maven POM model
     * @throws IOException            if reading the POM file fails
     * @throws XmlPullParserException if parsing the POM file fails
     */
    public Model getPomModel() throws IOException, XmlPullParserException {
        if (pomModel == null) {
            readPomFile();
        }
        return pomModel;
    }

    /**
     * Set a custom POM path and invalidate cached model.
     * 
     * @param path the path to the POM file
     */
    public void setPomPath(Path path) {
        this.pomPath = path;
        this.pomModel = null;
    }

    /**
     * Get the resolved POM path.
     * 
     * @return the path to the POM file
     * @throws IOException            if resolving the POM path fails
     * @throws XmlPullParserException if reading the POM file fails
     */
    public Path getPomPath() throws IOException, XmlPullParserException {
        if (pomPath == null) {
            readPomFile();
        }
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
     * Copy the pom.xml file to the specified path, injecting the dependencies as
     * needed.
     * <p>
     * The configuration file needs to have a dependencies section that provides the
     * list of
     * `artifactids` that need to be included in the output pom file. If these
     * dependencies
     * require any variables, those are copied as well.
     * <p>
     * The primary source file is the file from the template folder. The
     * dependencies are
     * supposed to be listed in the pom file of the application under test.
     *
     * @throws IOException            if the POM file cannot be copied
     * @throws XmlPullParserException if the POM file cannot be converted to an XML
     *                                Tree
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

    /**
     * Copy a template file to the specified path
     *
     * @param filename the name of the template file to copy
     * @param subPath  the path components
     * @throws IOException thrown if the copy operation failed
     */
    public String copyTemplate(String filename, String... subPath) throws IOException {
        String outputPath = Settings.getOutputPath();
        if (outputPath != null) {
            Path destinationPath = Path.of(outputPath, subPath); // Path where template file should be copied into
            Files.createDirectories(destinationPath);
            String name = destinationPath + File.separator + filename;
            try (InputStream sourceStream = getClass().getClassLoader().getResourceAsStream("templates/" + filename);
                    FileOutputStream destStream = new FileOutputStream(name);
                    FileChannel destChannel = destStream.getChannel()) {
                if (sourceStream == null) {
                    throw new IOException("Template file not found");
                }
                destChannel.transferFrom(Channels.newChannel(sourceStream), 0, Long.MAX_VALUE);
            }
            return name;
        }
        return null;
    }

    /**
     * Copy the properties of the dependency from the source model to the template
     * model
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
        String[] fields = { dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(),
                dependency.getScope() };
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

    public void buildJarPaths() {

        if (pomModel != null) {
            List<Dependency> dependencies = pomModel.getDependencies();

            Settings.getProperty("variables.m2_folder", String.class)
                    .ifPresent(m2 -> {
                        for (Dependency dependency : dependencies) {
                            try {
                                addJarPath(dependency, m2);
                            } catch (XmlPullParserException | IOException e) {
                                throw new AntikytheraException(e);
                            }
                        }
                    });
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
