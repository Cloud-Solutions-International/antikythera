package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class IntegrationTestRepositoryParser {
    @BeforeAll
    public static void setup() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator.yml"));
    }

    @Test
    void testRepositoryParser() throws IOException {
        final TestRepository tp = new TestRepository();
        final CompilationUnit cu = AntikytheraRunTime.getCompilationUnit("sa.com.cloudsolutions.service.Service");
        assertNotNull(cu);

        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).get();
        md.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                super.visit(n, arg);
                MethodDeclaration md = tp.findMethodDeclaration(n);
                assertNotNull(md);

                if(n.getNameAsString().equals("findById")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE id = ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeBetween")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age BETWEEN ? AND ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAge")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age = ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeGreaterThan")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age > ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeLessThan")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age < ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeLessThanEqual")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age <= ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeGreaterThanEqual")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age >= ? ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeIn")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age In  (?) ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeNotIn")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age NOT In (?) ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeIsNull")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age IS NULL ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeIsNotNull")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age IS NOT NULL ", rql.getQuery());
                }
                else if(n.getNameAsString().equals("findByAgeLike")) {
                    RepositoryQuery rql = tp.get(md);
                    assertNotNull(rql);
                    assertEquals("SELECT * FROM person WHERE age LIKE ?it  ", rql.getQuery());
                }
            }
        }, null);
    }

    class TestRepository extends RepositoryParser {
        protected TestRepository() throws IOException {
            preProcess();
            compile(AbstractCompiler.classToPath("sa.com.cloudsolutions.repository.PersonRepository"));
            process();
        }
    }
}
