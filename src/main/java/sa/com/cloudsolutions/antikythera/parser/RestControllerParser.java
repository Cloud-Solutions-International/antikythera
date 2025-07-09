package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.ArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.Branching;
import sa.com.cloudsolutions.antikythera.evaluator.DatabaseArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.DummyArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.EvaluatorFactory;
import sa.com.cloudsolutions.antikythera.evaluator.NullArgumentGenerator;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.generator.Antikythera;
import sa.com.cloudsolutions.antikythera.generator.SpringTestGenerator;

public class RestControllerParser extends DepsolvingParser {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);

    /**
     * Maintain stats of the controllers and methods parsed
     */
    private static final Stats stats = new Stats();

    /**
     * Creates a new RestControllerParser
     *
     * @param controller either a folder containing many controllers or a single controller
     */
    public RestControllerParser(String controller) throws IOException {
        super();
        this.cu = AntikytheraRunTime.getCompilationUnit(controller);

        Path dataPath = Paths.get(Settings.getProperty(Settings.OUTPUT_PATH).toString(), "src/test/resources/data");

        // Check if the dataPath directory exists, if not, create it
        if (!Files.exists(dataPath)) {
            Files.createDirectories(dataPath);
        }
        Files.createDirectories(Paths.get(Settings.getProperty(Settings.OUTPUT_PATH).toString(), "src/test/resources/uploads"));
    }

    @Override
    public void start() throws EvaluatorException, IOException {
        if(cu != null && cu.getPackageDeclaration().isPresent()) {
            processRestController(cu.getPackageDeclaration().get());
        }
    }

    @Override
    public void evaluateMethod(MethodDeclaration md, ArgumentGenerator gen) {
        throw new UnsupportedOperationException("To be completed");
    }

    private void processRestController(PackageDeclaration pd) throws IOException {

        TypeDeclaration<?> type = AbstractCompiler.getPublicType(cu);

        evaluator = EvaluatorFactory.create(type.getFullyQualifiedName().orElseThrow(), SpringEvaluator.class);
        evaluator.setOnTest(true);

        SpringTestGenerator generator = new SpringTestGenerator(cu);
        evaluator.addGenerator(generator);
        generator.setCommonPath(getCommonPath());

        CompilationUnit gen = generator.getCompilationUnit();
        generator.addBeforeClass();

        gen.addImport("com.fasterxml.jackson.core.JsonProcessingException");
        List<String> otherImports = (List<String>) Settings.getProperty(Settings.EXTRA_IMPORTS);
        if(otherImports != null) {
            for (String s : otherImports) {
                gen.addImport(s);
            }
        }

        /*
         * There is a very valid reason for doing this in two steps.
         * We want to make sure that all the repositories are identified before we start processing the methods.
         *
         */

        /*
         * Pass 2 : Generate the tests
         */
        AntikytheraRunTime.reset();
        cu.accept(new ControllerMethodVisitor(), null);

        Antikythera.getInstance().writeFilesToTest(
                pd.getName().asString(), type.getNameAsString() + "Test.java",
                gen.toString());

    }

    /**
     * Visitor that will cause the tests to be generated for each method.
     *
     * Test generation is carried out by the SpringTestGenerator which will be invoked by the
     * evaluator each time a return statement is encountered.
     *
     */
    private class ControllerMethodVisitor extends VoidVisitorAdapter<Void> {
        /**
         * Will trigger an evaluation which is like a fake execution of the code inside the method
         */
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);

            if (checkEligible(md)) {
                 evaluateMethod(md, new NullArgumentGenerator());
                 evaluateMethod(md, new DummyArgumentGenerator());
                 evaluateMethod(md, new DatabaseArgumentGenerator());
            }
        }

        protected void evaluateMethod(MethodDeclaration md, ArgumentGenerator gen) {
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

        private boolean checkEligible(MethodDeclaration md) {
            if (md.getAnnotationByName("ExceptionHandler").isPresent()) {
                return false;
            }
            if (md.isPublic()) {
                Optional<String> ctrl  = Settings.getProperty("controllers", String.class);
                if(ctrl.isPresent()) {
                    String[] crs = ctrl.get().split("#");
                    if (crs.length > 1) {
                        return md.getNameAsString().equals(crs[crs.length - 1]);
                    }
                }
                return true;
            }
            return false;
        }
    }


    /**
     * Each method in the controller will be a child of the main path for that controller
     * which is represented by the RequestMapping for that controller
     *
     * @return the path from the RequestMapping Annotation or an empty string
     */
    private String getCommonPath() {
        TypeDeclaration<?> decl =  AbstractCompiler.getPublicType(cu);
        if (decl != null) {
            Optional<AnnotationExpr> ann = decl.getAnnotationByName("RequestMapping");
            if (ann.isPresent()) {
                AnnotationExpr classAnnotation = ann.get();
                if (classAnnotation.isNormalAnnotationExpr()) {
                    return classAnnotation.asNormalAnnotationExpr().getPairs().get(0).getValue().toString();
                } else {
                    var memberValue = classAnnotation.asSingleMemberAnnotationExpr().getMemberValue();
                    if (memberValue.isArrayInitializerExpr()) {
                        return memberValue.asArrayInitializerExpr().getValues().get(0).toString();
                    }
                    return classAnnotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
                }
            }
        }
        return "";
    }

    public static Stats getStats() {
        return stats;
    }

}
