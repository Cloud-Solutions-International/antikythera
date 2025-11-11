package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// ... other imports

public class HQLParseAdapterTest extends TestHelper {
    private static final String USER_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.User";
    private static final String VEHICAL_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.Vehicle";

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
    }

    @ParameterizedTest
    @ValueSource(strings = {USER_MODEL, VEHICAL_MODEL})
    void testGetEntiyNameForEntity(String model) {
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(model).orElseThrow();
        CompilationUnit cu = type.findCompilationUnit().orElseThrow();
        HQLParserAdapter adapter = new HQLParserAdapter(cu, new TypeWrapper(type));
        assertEquals(USER_MODEL, adapter.getEntiyNameForEntity(USER_MODEL));
        assertEquals(USER_MODEL, adapter.getEntiyNameForEntity("User"));
        assertEquals(VEHICAL_MODEL, adapter.getEntiyNameForEntity(VEHICAL_MODEL));
        assertNull(adapter.getEntiyNameForEntity("xx"));
    }
}