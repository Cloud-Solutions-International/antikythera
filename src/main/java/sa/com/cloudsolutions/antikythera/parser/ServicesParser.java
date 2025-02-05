package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;

import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.generator.Antikythera;
import sa.com.cloudsolutions.antikythera.generator.UnitTestGenerator;

import java.io.IOException;
import java.util.Optional;


public class ServicesParser {
    private static final Logger logger = LoggerFactory.getLogger(ServicesParser.class);

    CompilationUnit cu;
    SpringEvaluator evaluator;
    UnitTestGenerator generator;

    public ServicesParser(String cls) {
        this.cu = AntikytheraRunTime.getCompilationUnit(cls);
        if (this.cu == null) {
            throw new AntikytheraException("Class not found: " + cls);
        }
        evaluator = new SpringEvaluator(cls);
        generator = new UnitTestGenerator();
        evaluator.addGenerator(generator);

        CompilationUnit testClass = generator.getCompilationUnit();
        testClass.setPackageDeclaration(cu.getPackageDeclaration().orElse(null));
        testClass.addClass(AbstractCompiler.getPublicType(cu).getNameAsString() + "Test");
    }

    public void start() throws IOException {
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate()) {
                    Graph.createGraphNode(md);

                }
            });
            solver.dfs();
        }
        generator.addBeforeClass();
        writeFiles();
    }

    public void start(String method) throws IOException {
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate() && md.getNameAsString().equals(method)) {
                    Graph.createGraphNode(md);
                }
            });
            solver.dfs();
        }
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                /*
                 * I would gladly do this iwthout a visitor, but discovered a bug in findAll()
                 */
                if (md.getNameAsString().equals(method)) {
                    evaluateMethod(md, new NullArgumentGenerator());
                }
                super.visit(md, arg);
            }
        }, null);
        generator.addBeforeClass();
        writeFiles();
    }

    private void writeFiles() throws IOException {
        Optional<PackageDeclaration> pd = cu.getPackageDeclaration();
        String packageName = pd.isPresent() ? pd.get().getNameAsString() : "";

        Antikythera.getInstance().writeFilesToTest(
            packageName, AbstractCompiler.getPublicType(cu).getNameAsString() + "Test.java",
            generator.getCompilationUnit().toString());

    }

    private void evaluateMethod(MethodDeclaration md, ArgumentGenerator gen) {
        evaluator.setArgumentGenerator(gen);
        evaluator.reset();
        evaluator.resetColors();
        AntikytheraRunTime.reset();
        try {
            evaluator.visit(md);

        } catch (AntikytheraException | ReflectiveOperationException e) {
            if ("log".equals(Settings.getProperty("dependencies.on_error"))) {
                logger.warn("Could not complete processing {} due to {}", md.getName(), e.getMessage());
            } else {
                throw new GeneratorException(e);
            }
        } finally {
            logger.info(md.getNameAsString());
        }
    }

    private boolean checkEligible(MethodDeclaration md) {
        return !md.isPublic();
    }

}
