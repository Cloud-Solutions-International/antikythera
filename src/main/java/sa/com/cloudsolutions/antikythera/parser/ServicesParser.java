package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
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


public class ServicesParser extends DepsolvingParser {
    private static final Logger logger = LoggerFactory.getLogger(ServicesParser.class);

    /**
     * Maintain stats of the controllers and methods parsed
     */
    private static final Stats stats = new Stats();
    boolean testPrivates = Settings.getProperty("test_privates", Boolean.class).orElse(false);
    boolean generateConstructorTests = Settings.getProperty(Settings.GENERATE_CONSTRUCTOR_TESTS, Boolean.class).orElse(false);

    Set<CallableDeclaration<?>> methods = new java.util.HashSet<>();
    String cls;
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
            if (generateConstructorTests) {
                decl.findAll(ConstructorDeclaration.class).forEach(cd -> {
                    if (!cd.isPrivate() || testPrivates) {
                        Graph.createGraphNode(cd);
                        methods.add(cd);
                    }
                });
            }
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
            if (generateConstructorTests) {
                decl.findAll(ConstructorDeclaration.class).forEach(cd -> {
                    if ((!cd.isPrivate() || testPrivates ) && cd.getNameAsString().equals(method)) {
                        Graph.createGraphNode(cd);
                        methods.add(cd);
                    }
                });
            }
            solver.dfs();
        }
        eval();
    }

    private void eval() {
        for (CallableDeclaration<?> md : methods) {
            stats.methods++;
            evaluateCallable(md, new DummyArgumentGenerator());
        }
    }

    public void writeFiles() throws IOException {
        if (generator != null) {
            generator.save();
        }
        // If generator is null, it means no methods were evaluated (e.g., interface with no default methods)
        // This is expected and we can safely skip writing files
    }

    @Override
    public void evaluateMethod(MethodDeclaration md, ArgumentGenerator gen) {
        evaluateCallable(md, gen);
    }

    @Override
    public void evaluateCallable(CallableDeclaration<?> md, ArgumentGenerator gen) {
        generator = (UnitTestGenerator) Factory.create("unit", cu);
        generator.addBeforeClass();

        TypeDeclaration<?> type = md.findAncestor(TypeDeclaration.class).orElse(null);
        String targetCls = type != null ? type.getFullyQualifiedName().orElse(cls) : cls;
        evaluator = EvaluatorFactory.create(targetCls, SpringEvaluator.class);
        evaluator.addGenerator(generator);
        evaluator.setOnTest(true);
        super.evaluateCallable(md, gen);
    }

}
