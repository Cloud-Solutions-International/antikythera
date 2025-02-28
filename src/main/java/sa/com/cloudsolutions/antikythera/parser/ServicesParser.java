package sa.com.cloudsolutions.antikythera.parser;


import com.github.javaparser.ast.body.MethodDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.generator.Factory;
import sa.com.cloudsolutions.antikythera.generator.UnitTestGenerator;

import java.io.IOException;

public class ServicesParser extends DepsolvingParser {
    private static final Logger logger = LoggerFactory.getLogger(ServicesParser.class);

    private final SpringEvaluator evaluator;
    private final UnitTestGenerator generator;

    public ServicesParser(String cls) {
        this.cu = AntikytheraRunTime.getCompilationUnit(cls);
        if (this.cu == null) {
            throw new AntikytheraException("Class not found: " + cls);
        }
        evaluator = new SpringEvaluator(cls);
        generator = (UnitTestGenerator) Factory.create("unit", cu);

        evaluator.addGenerator(generator);
        evaluator.setOnTest(true);
        evaluator.setupFields(cu);

        generator.setupImports();
        generator.addBeforeClass();
    }

    @Override
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

    public void writeFiles() throws IOException {
        generator.save();
    }

    private boolean checkEligible(MethodDeclaration md) {
        return !md.isPublic();
    }
}
