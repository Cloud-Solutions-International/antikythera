package sa.com.cloudsolutions.antikythera.parser;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class BaseRepositoryParserRegexTest {

    @org.junit.jupiter.api.BeforeAll
    static void setUpAll() throws java.io.IOException {
        sa.com.cloudsolutions.antikythera.configuration.Settings
                .loadConfigMap(new java.io.File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @Test
    void testLooksLikeNativeSQL() throws Exception {
        BaseRepositoryParser parser = new BaseRepositoryParser();
        Method method = BaseRepositoryParser.class.getDeclaredMethod("looksLikeNativeSQL", String.class);
        method.setAccessible(true);

        // Test HQL patterns (should return false)
        assertFalse((boolean) method.invoke(parser, "SELECT u FROM User u WHERE u.id = :id"));
        assertFalse((boolean) method.invoke(parser, "FROM User u WHERE u.name = 'test'"));
        assertFalse((boolean) method.invoke(parser, "SELECT u.name FROM User u"));

        // Test SQL patterns (should return true)
        assertTrue((boolean) method.invoke(parser, "SELECT COUNT(*) FROM users"));
        assertTrue((boolean) method.invoke(parser, "SELECT * FROM (SELECT * FROM users) as sub"));
        assertTrue(
                (boolean) method.invoke(parser, "SELECT * FROM user_table u JOIN address_table a ON u.id = a.user_id"));
        assertTrue((boolean) method.invoke(parser, "SELECT * FROM \"UserTable\""));

        // Test mixed/ambiguous cases
        assertFalse((boolean) method.invoke(parser, "SELECT count(u) FROM User u")); // HQL count

        // Test long strings (basic performance check, not full ReDoS proof but good
        // sanity check)
        StringBuilder longHql = new StringBuilder("FROM User u WHERE u.id IN (");
        for (int i = 0; i < 1000; i++) {
            longHql.append(i).append(",");
        }
        longHql.append("1000)");
        assertFalse((boolean) method.invoke(parser, longHql.toString()));
    }
}
