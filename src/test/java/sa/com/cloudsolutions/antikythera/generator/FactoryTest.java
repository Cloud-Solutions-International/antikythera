package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;
import java.util.zip.Adler32;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Not a real test suite.
 * The purpose for existence is to facilitate others
 */
class FactoryTest {
    /**
     * Just something i picked out of the blue
     */
    @Mock
    Adler32 adler32;

    private CompilationUnit cu;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }

    @BeforeEach
    void setUp() {
        cu = new CompilationUnit();
        cu.setPackageDeclaration("sa.com.cloudsolutions.antikythera.testhelper.generator");
        cu.addClass("Dummy");
    }

    @Test
    void createUnitTestGeneratorReturnsNonNull() {
        TestGenerator generator = Factory.create("unit", cu);
        assertNotNull(generator);
    }

    /**
     * Author : Antikythera
     * This test was not really written by Antikythera, but having this annotation makes it
     * possible to use this test as an input to another unit test!
     */
    @Test
    void createIntegrationTestGeneratorReturnsNull() {
        TestGenerator generator = Factory.create("integration", cu);
        assertNull(generator);
    }

    @Test
    void createApiTestGeneratorReturnsNonNull() {
        TestGenerator generator = Factory.create("api", cu);
        assertNotNull(generator);
    }

    @Test
    void createUnitTestGeneratorCachesInstance() {
        TestGenerator generator1 = Factory.create("unit", cu);
        TestGenerator generator2 = Factory.create("unit", cu);
        assertSame(generator1, generator2);
    }

    @Test
    void createApiTestGeneratorCachesInstance() {
        TestGenerator generator1 = Factory.create("api", cu);
        TestGenerator generator2 = Factory.create("api", cu);
        assertSame(generator1, generator2);
    }
}
