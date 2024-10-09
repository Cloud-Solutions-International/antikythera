package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRepositoryParser {
    private RepositoryParser parser;
    private CompilationUnit cu;

    @BeforeAll
    static void setUpAll() throws IOException {
        Settings.loadConfigMap();
    }

    @BeforeEach
    void setUp() throws IOException {
        parser = new RepositoryParser();
        cu = StaticJavaParser.parse("""
                @Table(name = "table_name")
                public class AdmissionClearance implements Serializable {}
                """);
    }

    @Test
    void testFindTableName() {

        assertEquals("table_name", RepositoryParser.findTableName(cu));

        cu = StaticJavaParser.parse("""
                public class AdmissionClearanceTable implements Serializable {}
                """);
        assertEquals("admission_clearance_table", RepositoryParser.findTableName(cu));
    }

    @Test
    void testParseNonAnnotatedMethod() throws ReflectiveOperationException {
        Field entityCuField = RepositoryParser.class.getDeclaredField("entityCu");
        entityCuField.setAccessible(true);
        entityCuField.set(parser, cu);

        parser.parseNonAnnotatedMethod("findAll");
        Map<String, RepositoryQuery> queries = parser.getQueries();
        assertTrue(queries.containsKey("findAll"));
        assertEquals("SELECT * FROM table_name", queries.get("findAll").getQuery());

        // Test findById method
        parser.parseNonAnnotatedMethod("findById");
        queries = parser.getQueries();
        assertTrue(queries.containsKey("findById"));
        assertEquals("SELECT * FROM table_name WHERE id = ?", queries.get("findById").getQuery().strip());

        // Test findAllById method
        parser.parseNonAnnotatedMethod("findAllById");
        queries = parser.getQueries();
        assertTrue(queries.containsKey("findAllById"));
        assertEquals("SELECT * FROM table_name WHERE id = ?", queries.get("findAllById").getQuery());
    }
}
