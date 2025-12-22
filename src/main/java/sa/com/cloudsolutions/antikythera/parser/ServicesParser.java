package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Branching;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.generator.Factory;
import sa.com.cloudsolutions.antikythera.generator.UnitTestGenerator;

import java.io.IOException;
import java.util.Set;


public class ServicesParser {
    private static final Logger logger = LoggerFactory.getLogger(ServicesParser.class);

    /**
     * Maintain stats of the controllers and methods parsed
     */
    private static final Stats stats = new Stats();
    boolean testPrivates = Settings.getProperty("test_privates", Boolean.class).orElse(false);

    Set<MethodDeclaration> methods = new java.util.HashSet<>();
    CompilationUnit cu;
    String cls;
    SpringEvaluator evaluator;
    UnitTestGenerator generator;

    public ServicesParser(String cls) {
        this.cls = cls;
        this.cu = AntikytheraRunTime.getCompilationUnit(cls);
        if (this.cu == null) {
            throw new AntikytheraException("Class not found: " + cls);
        }
    }

    public void start() {

        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate() || testPrivates) {
                    Graph.createGraphNode(md);
                    methods.add(md);
                }
                else {
                    logger.debug("Skipping private method {}", md.getNameAsString());
                }
            });
            solver.dfs();
        }
        eval();
    }

    public void start(String method) {
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if ((!md.isPrivate() || testPrivates ) && md.getNameAsString().equals(method)) {
                    Graph.createGraphNode(md);
                    methods.add(md);
                }
            });
            solver.dfs();
        }
        eval();
    }

    private void eval() {
        for (MethodDeclaration md : methods) {
            stats.methods++;
            evaluateMethod(md, new DummyArgumentGenerator());
        }
    }

    public void writeFiles() throws IOException {
        if (generator != null) {
            generator.save();
        }
        // If generator is null, it means no methods were evaluated (e.g., interface with no default methods)
        // This is expected and we can safely skip writing files
    }

    public void evaluateMethod(MethodDeclaration md, ArgumentGenerator gen) {
        generator = (UnitTestGenerator) Factory.create("unit", cu);
        generator.addBeforeClass();

        evaluator = EvaluatorFactory.create(cls, SpringEvaluator.class);
        evaluator.addGenerator(generator);
        evaluator.setOnTest(true);
        evaluator.setArgumentGenerator(gen);
        evaluator.reset();
        Branching.clear();
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

}
