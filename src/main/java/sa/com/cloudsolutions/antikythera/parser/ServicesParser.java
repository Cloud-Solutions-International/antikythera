package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
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
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.generator.Factory;
import sa.com.cloudsolutions.antikythera.generator.UnitTestGenerator;

import java.io.IOException;
import java.util.Set;


public class ServicesParser {
    private static final Logger logger = LoggerFactory.getLogger(ServicesParser.class);
    Set<MethodDeclaration> methods = new java.util.HashSet<>();
    CompilationUnit cu;
    SpringEvaluator evaluator;
    UnitTestGenerator generator;

    public ServicesParser(String cls) {
        this.cu = AntikytheraRunTime.getCompilationUnit(cls);
        if (this.cu == null) {
            throw new AntikytheraException("Class not found: " + cls);
        }
        evaluator = new SpringEvaluator(cls);
        generator = (UnitTestGenerator) Factory.create("unit", cu);

        evaluator.addGenerator(generator);
        evaluator.setOnTest(true);
        generator.setupImports();
        generator.addBeforeClass();
    }

    public void start() {
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate()) {
                    Graph.createGraphNode(md);
                    methods.add(md);
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
                if (!md.isPrivate() && md.getNameAsString().equals(method)) {
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
            evaluateMethod(md, new DummyArgumentGenerator());
        }
    }

    public void writeFiles() throws IOException {
        generator.save();
    }

    public void evaluateMethod(MethodDeclaration md, ArgumentGenerator gen) {
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
