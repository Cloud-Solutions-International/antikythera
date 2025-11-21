package sa.com.cloudsolutions.antikythera.parser.converter;

import com.github.javaparser.ast.body.TypeDeclaration;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.ParenthesedSelect;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.TestHelper;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BasicConverter focusing on the processJoins logic.
 * We simulate HQL style joins (entity.property alias) by supplying a custom FromItem whose toString()
 * matches the expected pattern that BasicConverter.split(".").
 */
class BasicConverterTest extends TestHelper {
    private static final String USER_MODEL = "sa.com.cloudsolutions.antikythera.testhelper.model.User";

    // Helper subclass to force the toString() format "u.vehicles v" for join processing
    static class PathTable extends Table {
        private final String path;
        private final Alias alias;
        PathTable(String path, String alias) {
            super(path);
            this.path = path;
            this.alias = new Alias(alias);
        }
        @Override
        public Alias getAlias() { return alias; }
        @Override
        public String toString() { return path + " " + alias.getName(); }
    }

    @BeforeAll
    static void setUp() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.reset();
        AbstractCompiler.preProcess();
        EntityMappingResolver.reset();
        EntityMappingResolver.build();
    }

    @Test
    void testImplicitJoinThrowsAndStarProjection() throws Exception {
        // Rename test to reflect success
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        TypeWrapper wrapper = new TypeWrapper(type);
        Statement stmt = CCJSqlParserUtil.parse("SELECT u FROM user u");
        Select sel = (Select) stmt;
        PlainSelect ps = sel.getPlainSelect();
        Join j1 = new Join();
        j1.setFromItem(ps.getFromItem());
        j1.setRightItem(new PathTable("u.vehicles", "j"));
        ps.setJoins(List.of(j1));
        BasicConverter.convertFieldsToSnakeCase(stmt, wrapper);
        assertInstanceOf(AllColumns.class, ps.getSelectItems().getFirst().getExpression());
        assertEquals(1, j1.getOnExpressions().size());
        // Right item should now be a concrete table name, not path
        assertInstanceOf(Table.class, j1.getRightItem());
        assertFalse(j1.getRightItem().toString().contains("u.vehicles"));
    }

    @Test
    void testParenthesedSelectJoinBranchOnly() throws Exception {
        // Add implicit join plus parenthesed select to validate both paths
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        TypeWrapper wrapper = new TypeWrapper(type);
        Statement outerStmt = CCJSqlParserUtil.parse("SELECT u FROM user u");
        Select outerSel = (Select) outerStmt;
        PlainSelect outerPs = outerSel.getPlainSelect();
        // implicit join
        Join j1 = new Join();
        j1.setFromItem(outerPs.getFromItem());
        j1.setRightItem(new PathTable("u.vehicles", "j"));
        // Parenthesed select join
        Statement innerStmt = CCJSqlParserUtil.parse("SELECT veh FROM vehicle veh");
        Select innerSel = (Select) innerStmt;
        ParenthesedSelect parenthesedSelect = new ParenthesedSelect();
        parenthesedSelect.setSelect(innerSel);
        Join j2 = new Join();
        j2.setFromItem(outerPs.getFromItem());
        j2.setRightItem(parenthesedSelect);
        outerPs.setJoins(List.of(j1, j2));
        BasicConverter.convertFieldsToSnakeCase(outerStmt, wrapper);
        assertInstanceOf(AllColumns.class, outerPs.getSelectItems().getFirst().getExpression());
        assertEquals(1, j1.getOnExpressions().size());
        assertInstanceOf(Table.class, j1.getRightItem());
        PlainSelect innerPs = innerSel.getPlainSelect();
        assertInstanceOf(AllColumns.class, innerPs.getSelectItems().getFirst().getExpression());
        assertTrue(j2.getOnExpressions().isEmpty());
    }

    @Test
    void testOriginalImplicitJoinSuccess() throws Exception {
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        TypeWrapper wrapper = new TypeWrapper(type);
        Statement stmt = CCJSqlParserUtil.parse("SELECT u FROM user u");
        Select sel = (Select) stmt;
        PlainSelect ps = sel.getPlainSelect();
        Join j1 = new Join();
        j1.setFromItem(ps.getFromItem());
        j1.setRightItem(new PathTable("u.vehicles", "v"));
        ps.setJoins(List.of(j1));
        BasicConverter.convertFieldsToSnakeCase(stmt, wrapper);
        assertInstanceOf(AllColumns.class, ps.getSelectItems().getFirst().getExpression());
        assertEquals(1, j1.getOnExpressions().size());
    }

    @Test
    void testOriginalParenthesedSelectBranch() throws Exception {
        TypeDeclaration<?> type = AntikytheraRunTime.getTypeDeclaration(USER_MODEL).orElseThrow();
        TypeWrapper wrapper = new TypeWrapper(type);
        Statement outerStmt = CCJSqlParserUtil.parse("SELECT u FROM user u");
        Select outerSel = (Select) outerStmt;
        PlainSelect outerPs = outerSel.getPlainSelect();
        Join j1 = new Join();
        j1.setFromItem(outerPs.getFromItem());
        j1.setRightItem(new PathTable("u.vehicles", "v"));
        Statement innerStmt = CCJSqlParserUtil.parse("SELECT veh FROM vehicle veh");
        Select innerSel = (Select) innerStmt;
        ParenthesedSelect parenthesedSelect = new ParenthesedSelect();
        parenthesedSelect.setSelect(innerSel);
        Join j2 = new Join();
        j2.setFromItem(outerPs.getFromItem());
        j2.setRightItem(parenthesedSelect);
        outerPs.setJoins(List.of(j1, j2));
        BasicConverter.convertFieldsToSnakeCase(outerStmt, wrapper);
        assertInstanceOf(AllColumns.class, outerPs.getSelectItems().getFirst().getExpression());
        assertEquals(1, j1.getOnExpressions().size());
        PlainSelect innerPs = innerSel.getPlainSelect();
        assertInstanceOf(AllColumns.class, innerPs.getSelectItems().getFirst().getExpression());
    }
}
