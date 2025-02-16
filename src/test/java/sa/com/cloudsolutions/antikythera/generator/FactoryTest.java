package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class FactoryTest {

    private CompilationUnit cu;

    @BeforeAll
    static void beforeClass() throws IOException {
        Settings.loadConfigMap();
    }

    @BeforeEach
    void setUp() {
        cu = new CompilationUnit();
        cu.setPackageDeclaration("sa.com.cloudsolutions.antikythera.generator");
        cu.addClass("Dummy");
    }

    @Test
    void createUnitTestGeneratorReturnsNonNull() {
        TestGenerator generator = Factory.create("unit", cu);
        assertNotNull(generator);
    }

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
