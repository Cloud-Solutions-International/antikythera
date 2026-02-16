package sa.com.cloudsolutions.antikythera.parser;

import org.apache.maven.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class MavenHelperTest {

    @ParameterizedTest
    @CsvSource({
        "21, 21",
        "17, 17",
        "11, 11",
        "1.8, 8",
        "1.7, 7",
        "8, 8"
    })
    void testParseJavaVersion(String input, int expected) {
        assertEquals(expected, MavenHelper.parseJavaVersion(input));
    }

    @Test
    void testParseJavaVersionInvalid() {
        assertEquals(-1, MavenHelper.parseJavaVersion(null));
        assertEquals(-1, MavenHelper.parseJavaVersion(""));
        assertEquals(-1, MavenHelper.parseJavaVersion("abc"));
    }

    @Test
    void testGetJavaVersionFromMavenCompilerSource(@TempDir Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        try (FileWriter fw = new FileWriter(pomFile.toFile())) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
            fw.write("  <modelVersion>4.0.0</modelVersion>\n");
            fw.write("  <groupId>com.example</groupId>\n");
            fw.write("  <artifactId>test</artifactId>\n");
            fw.write("  <version>1.0</version>\n");
            fw.write("  <properties>\n");
            fw.write("    <maven.compiler.source>17</maven.compiler.source>\n");
            fw.write("    <maven.compiler.target>17</maven.compiler.target>\n");
            fw.write("  </properties>\n");
            fw.write("</project>\n");
        }

        MavenHelper helper = new MavenHelper();
        helper.readPomFile(pomFile);
        assertEquals(17, helper.getJavaVersion());
    }

    @Test
    void testGetJavaVersionFromJavaVersionProperty(@TempDir Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        try (FileWriter fw = new FileWriter(pomFile.toFile())) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
            fw.write("  <modelVersion>4.0.0</modelVersion>\n");
            fw.write("  <groupId>com.example</groupId>\n");
            fw.write("  <artifactId>test</artifactId>\n");
            fw.write("  <version>1.0</version>\n");
            fw.write("  <properties>\n");
            fw.write("    <java.version>11</java.version>\n");
            fw.write("  </properties>\n");
            fw.write("</project>\n");
        }

        MavenHelper helper = new MavenHelper();
        helper.readPomFile(pomFile);
        assertEquals(11, helper.getJavaVersion());
    }

    @Test
    void testGetJavaVersionWithPropertyReference(@TempDir Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        try (FileWriter fw = new FileWriter(pomFile.toFile())) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
            fw.write("  <modelVersion>4.0.0</modelVersion>\n");
            fw.write("  <groupId>com.example</groupId>\n");
            fw.write("  <artifactId>test</artifactId>\n");
            fw.write("  <version>1.0</version>\n");
            fw.write("  <properties>\n");
            fw.write("    <java.version>11</java.version>\n");
            fw.write("    <maven.compiler.source>${java.version}</maven.compiler.source>\n");
            fw.write("    <maven.compiler.target>${java.version}</maven.compiler.target>\n");
            fw.write("  </properties>\n");
            fw.write("</project>\n");
        }

        MavenHelper helper = new MavenHelper();
        helper.readPomFile(pomFile);
        assertEquals(11, helper.getJavaVersion());
    }

    @Test
    void testGetJavaVersionLegacyFormat(@TempDir Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        try (FileWriter fw = new FileWriter(pomFile.toFile())) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
            fw.write("  <modelVersion>4.0.0</modelVersion>\n");
            fw.write("  <groupId>com.example</groupId>\n");
            fw.write("  <artifactId>test</artifactId>\n");
            fw.write("  <version>1.0</version>\n");
            fw.write("  <properties>\n");
            fw.write("    <maven.compiler.source>1.8</maven.compiler.source>\n");
            fw.write("    <maven.compiler.target>1.8</maven.compiler.target>\n");
            fw.write("  </properties>\n");
            fw.write("</project>\n");
        }

        MavenHelper helper = new MavenHelper();
        helper.readPomFile(pomFile);
        assertEquals(8, helper.getJavaVersion());
    }

    @Test
    void testGetJavaVersionDefaultsTo21WhenNoProperties(@TempDir Path tempDir) throws Exception {
        Path pomFile = tempDir.resolve("pom.xml");
        try (FileWriter fw = new FileWriter(pomFile.toFile())) {
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
            fw.write("  <modelVersion>4.0.0</modelVersion>\n");
            fw.write("  <groupId>com.example</groupId>\n");
            fw.write("  <artifactId>test</artifactId>\n");
            fw.write("  <version>1.0</version>\n");
            fw.write("</project>\n");
        }

        MavenHelper helper = new MavenHelper();
        helper.readPomFile(pomFile);
        assertEquals(21, helper.getJavaVersion());
    }
}
