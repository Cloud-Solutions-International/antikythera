package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ITRepositoryParser {
    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
        AbstractCompiler.preProcess();
    }

    MCEWrapper toWrapper(MethodCallExpr mce) {
        MCEWrapper wrapper = new MCEWrapper(mce);
        wrapper.setArgumentTypes(new NodeList<>());
        for (Expression argument : mce.getArguments()) {
            if (argument.isLiteralExpr()) {
                wrapper.getArgumentTypes().add(AbstractCompiler.convertLiteralToType(argument.asLiteralExpr()));
            }
        }
        return wrapper;
    }

    @Test
    void testDepartmentRepositoryParser() throws IOException {
        final RepositoryParser tp = new RepositoryParser();
        RepositoryParser.preProcess();
        tp.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.repository.DepartmentRepository"));
        tp.processTypes();
        tp.buildQueries();

        final CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.Service");
        assertNotNull(cu);
        TypeDeclaration<?> repository = tp.getCompilationUnit().getType(0);

        cu.findFirst(MethodDeclaration.class,
            md1 -> md1.getNameAsString().equals("queries2")).ifPresent(md -> md.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    Optional<Callable> cd = AbstractCompiler.findCallableDeclaration(toWrapper(n), repository);
                    assertTrue(cd.isPresent());
                    MethodDeclaration md = cd.get().asMethodDeclaration();
                    assertNotNull(md);
                    RepositoryQuery rql = tp.get(cd.get());
                    assertNotNull(rql);

                    String sql = rql.getOriginalQuery();
                    assertTrue(sql.contains("SELECT new sa.com.cloudsolutions.dto.EmployeeDepartmentDTO(p.name, d.departmentName) "));
                    assertTrue(rql.getQuery().contains("SELECT * FROM person p"));

                    try {
                        Select stmt = (Select) CCJSqlParserUtil.parse(rql.getQuery());
                        assertNotNull(stmt);
                        assertEquals(
                                "SELECT * FROM person p JOIN department d ON p.id = d.id WHERE d.id = :departmentId",
                                stmt.toString()
                        );
                        rql.buildSimplifiedQuery();
                        Statement ex = rql.getSimplifiedStatement();
                        assertEquals("SELECT * FROM person p JOIN department d ON p.id = d.id WHERE '1' = '1'",
                                ex.toString());
                    } catch (JSQLParserException e) {
                        throw new RuntimeException(e);
                    } catch (AntikytheraException e) {
                        throw new RuntimeException(e);
                    }

                }
            }, null));
    }

    @Test
    void testPersonRepositoryParser() throws IOException {
        RepositoryParser.preProcess();

        final RepositoryParser tp = new RepositoryParser();
        tp.compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.repository.PersonRepository"));
        tp.processTypes();
        tp.buildQueries();

        final CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.Service");
        assertNotNull(cu);

        TypeDeclaration<?> repository = tp.getCompilationUnit().getType(0);

        cu.findFirst(MethodDeclaration.class).ifPresent(md -> md.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                Optional<Callable> cd = AbstractCompiler.findCallableDeclaration(toWrapper(n), repository);
                assertTrue(cd.isPresent());
                MethodDeclaration md = cd.get().asMethodDeclaration();
                if(md == null) {
                    return;
                }

                RepositoryQuery rql = tp.get(cd.get());
                assertNotNull(rql);

                if(n.getNameAsString().equals("findById")) {
                    assertEquals("SELECT * FROM person WHERE id = ?1", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeBetween")) {
                    assertEquals("SELECT * FROM person WHERE age BETWEEN ?1 AND ?2", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAge")) {
                    assertEquals("SELECT * FROM person WHERE age = ?1", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeGreaterThan")) {
                    assertEquals("SELECT * FROM person WHERE age > ?1", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeLessThan")) {
                    assertEquals("SELECT * FROM person WHERE age < ?1", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeLessThanEqual")) {
                    assertEquals("SELECT * FROM person WHERE age <= ?1", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeGreaterThanEqual")) {
                    assertEquals("SELECT * FROM person WHERE age >= ?1", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeIn")) {
                    assertEquals("SELECT * FROM person WHERE age IN (?1)", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeNotIn")) {
                    assertEquals("SELECT * FROM person WHERE age NOT IN (?1)", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeIsNull")) {
                    assertEquals("SELECT * FROM person WHERE age IS NULL", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeIsNotNull")) {
                    assertEquals("SELECT * FROM person WHERE age IS NOT NULL", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByNameLike")) {
                    assertEquals("SELECT * FROM person WHERE name LIKE ?1", rql.getQuery());
                }
            }
        }, null));

    }
}
