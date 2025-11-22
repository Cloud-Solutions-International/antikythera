package sa.com.cloudsolutions.antikythera.parser.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MethodToSQLConverterTest {

    @Test
    void testBuildSelectAndWhereClauses_FindAll() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findAll"), sql, "users");
        assertEquals("SELECT * FROM users", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_FindAllById() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findAllById"), sql, "users");
        assertEquals("SELECT * FROM users WHERE id IN (?)", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_FindBy() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findBy", "Name"), sql, "users");
        assertEquals("SELECT * FROM users WHERE name = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_CountBy() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("countBy", "Active"), sql, "users");
        assertEquals("SELECT COUNT(*) FROM users WHERE active = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_DeleteBy() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("deleteBy", "Id"), sql, "users");
        assertEquals("DELETE FROM users WHERE id = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_ExistsBy() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("existsBy", "Email"), sql, "users");
        assertEquals("SELECT EXISTS (SELECT 1 FROM users WHERE email = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_FindFirstBy() {
        StringBuilder sql = new StringBuilder();
        boolean top = MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findFirstBy", "Name"), sql, "users");
        assertTrue(top);
        assertEquals("SELECT * FROM users WHERE name = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_FindTopBy() {
        StringBuilder sql = new StringBuilder();
        boolean top = MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findTopBy", "Age"), sql, "users");
        assertTrue(top);
        assertEquals("SELECT * FROM users WHERE age = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_FindDistinctBy() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findDistinctBy", "Name"), sql, "users");
        assertEquals("SELECT DISTINCT * FROM users WHERE name = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_Operators() {
        StringBuilder sql = new StringBuilder();
        List<String> components = List.of("findBy", "Age", "GreaterThan", "And", "Name", "In");
        MethodToSQLConverter.buildSelectAndWhereClauses(components, sql, "users");
        assertEquals("SELECT * FROM users WHERE age  > ?  AND name  IN (?) ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_Not() {
        StringBuilder sql = new StringBuilder();
        // Standalone Not -> != ?
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findBy", "Active", "Not"), sql, "users");
        assertEquals("SELECT * FROM users WHERE active != ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_NotLike() {
        StringBuilder sql = new StringBuilder();
        // Not Like -> NOT LIKE ?
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findBy", "Name", "Not", "Like"), sql, "users");
        assertEquals("SELECT * FROM users WHERE name NOT LIKE ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_StartingWithEndingWith() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(
                List.of("findBy", "Name", "StartingWith", "And", "Email", "EndingWith"), sql, "users");
        assertEquals("SELECT * FROM users WHERE name  LIKE ?  AND email  LIKE ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_BeforeAfter() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findBy", "Date", "Before", "And", "Time", "After"),
                sql, "users");
        assertEquals("SELECT * FROM users WHERE date  < ?  AND time  > ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_TrueFalse() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findBy", "Active", "True", "And", "Deleted", "False"),
                sql, "users");
        assertEquals("SELECT * FROM users WHERE active  = true  AND deleted  = false ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_IsEquals() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findBy", "Name", "Is", "And", "Age", "Equals"), sql,
                "users");
        assertEquals("SELECT * FROM users WHERE name = ?  AND age = ? ", sql.toString());
    }

    @Test
    void testBuildSelectAndWhereClauses_OrderBy() {
        StringBuilder sql = new StringBuilder();
        MethodToSQLConverter.buildSelectAndWhereClauses(List.of("findAll", "OrderBy", "Name", "Desc"), sql, "users");
        assertEquals("SELECT * FROM users ORDER BY name DESC ", sql.toString());
    }
}
