package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for DatabaseDialect functionality.
 */
public class DatabaseDialectTest {

    @Test
    public void testOracleBooleanTransformation() {
        assertEquals("1", DatabaseDialect.ORACLE.transformBooleanValue("true"));
        assertEquals("0", DatabaseDialect.ORACLE.transformBooleanValue("false"));
        assertEquals("someValue", DatabaseDialect.ORACLE.transformBooleanValue("someValue"));
    }

    @Test
    public void testPostgreSQLBooleanTransformation() {
        assertEquals("true", DatabaseDialect.POSTGRESQL.transformBooleanValue("true"));
        assertEquals("false", DatabaseDialect.POSTGRESQL.transformBooleanValue("false"));
        assertEquals("someValue", DatabaseDialect.POSTGRESQL.transformBooleanValue("someValue"));
    }

    @Test
    public void testOracleLimitClause() {
        String sql = "SELECT * FROM users WHERE active = 1";
        assertEquals(sql + " AND ROWNUM = 1", DatabaseDialect.ORACLE.applyLimitClause(sql, 1));
        assertEquals(sql + " AND ROWNUM <= 5", DatabaseDialect.ORACLE.applyLimitClause(sql, 5));
    }

    @Test
    public void testPostgreSQLLimitClause() {
        String sql = "SELECT * FROM users WHERE active = true";
        assertEquals(sql + " LIMIT 1", DatabaseDialect.POSTGRESQL.applyLimitClause(sql, 1));
        assertEquals(sql + " LIMIT 5", DatabaseDialect.POSTGRESQL.applyLimitClause(sql, 5));
    }

    @Test
    public void testDialectDetectionFromJdbcUrl() {
        assertEquals(DatabaseDialect.ORACLE, DatabaseDialect.fromJdbcUrl("jdbc:oracle:thin:@localhost:1521:xe"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromJdbcUrl("jdbc:postgresql://localhost:5432/testdb"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromJdbcUrl("jdbc:postgres://localhost:5432/testdb"));
        assertNull(DatabaseDialect.fromJdbcUrl("jdbc:mysql://localhost:3306/testdb"));
        assertNull(DatabaseDialect.fromJdbcUrl(null));
    }

    @Test
    public void testDialectDetectionFromString() {
        assertEquals(DatabaseDialect.ORACLE, DatabaseDialect.fromString("oracle"));
        assertEquals(DatabaseDialect.ORACLE, DatabaseDialect.fromString("ORACLE"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromString("postgresql"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromString("pg"));
        assertEquals(DatabaseDialect.POSTGRESQL, DatabaseDialect.fromString("PG"));
        assertNull(DatabaseDialect.fromString("mysql"));
        assertNull(DatabaseDialect.fromString(null));
    }

    @Test
    public void testSqlTransformation() {
        String sqlWithBooleans = "SELECT * FROM users WHERE active = true AND deleted = false";
        
        // Oracle should transform booleans
        String oracleResult = DatabaseDialect.ORACLE.transformSql(sqlWithBooleans);
        assertTrue(oracleResult.contains("= 1"));
        assertTrue(oracleResult.contains("= 0"));
        
        // PostgreSQL should keep booleans as-is
        String postgresResult = DatabaseDialect.POSTGRESQL.transformSql(sqlWithBooleans);
        assertTrue(postgresResult.contains("= true"));
        assertTrue(postgresResult.contains("= false"));
    }

    @Test
    public void testBooleanSupport() {
        assertFalse(DatabaseDialect.ORACLE.supportsBoolean());
        assertTrue(DatabaseDialect.POSTGRESQL.supportsBoolean());
    }

    @Test
    public void testSequenceSyntax() {
        assertEquals("user_seq.NEXTVAL", DatabaseDialect.ORACLE.getSequenceNextValueSyntax("user_seq"));
        assertEquals("NEXTVAL('user_seq')", DatabaseDialect.POSTGRESQL.getSequenceNextValueSyntax("user_seq"));
    }

    @Test
    public void testConcatenationOperator() {
        assertEquals("||", DatabaseDialect.ORACLE.getConcatenationOperator());
        assertEquals("||", DatabaseDialect.POSTGRESQL.getConcatenationOperator());
    }
}
