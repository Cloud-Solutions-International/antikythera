package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.exception.GeneratorException;
import sa.com.cloudsolutions.antikythera.generator.ProjectGenerator;
import sa.com.cloudsolutions.antikythera.generator.SpringTestGenerator;

public class RestControllerParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);
    private final File controllers;

    /**
     * Maintain stats of the controllers and methods parsed
     */
    private static Stats stats = new Stats();

    private boolean evaluatorUnsupported = false;
    File current;
    private SpringEvaluator evaluator;

    /**
     * Creates a new RestControllerParser
     *
     * @param controllers either a folder containing many controllers or a single controller
     */
    public RestControllerParser(File controllers) throws IOException {
        super();
        this.controllers = controllers;

        Path dataPath = Paths.get(Settings.getProperty(Constants.OUTPUT_PATH).toString(), "src/test/resources/data");

        // Check if the dataPath directory exists, if not, create it
        if (!Files.exists(dataPath)) {
            Files.createDirectories(dataPath);
        }
        Files.createDirectories(Paths.get(Settings.getProperty(Constants.OUTPUT_PATH).toString(), "src/test/resources/uploads"));

    }

    public void start() throws IOException, EvaluatorException {
        processControllers(controllers);
    }

    /**
     * Process the controllers in the given folder.
     * If the path is a directory, it will process all the files in the directory.
     * @param path the path in which to look for controllers
     * @throws IOException if the file could not be read
     * @throws EvaluatorException if the file could not be processed due to compilation related issues.
     */
    private void processControllers(File path) throws IOException, EvaluatorException {
        current = path;
        if (path.isDirectory()) {
            for (File f : path.listFiles()) {
                if(f.toString().contains(controllers.toString())) {
                    new RestControllerParser(f).start();
                    stats.controllers++;
                }
            }

        } else {
            parseController(path);
        }
    }

    private void parseController(File path) throws IOException {
        String absolutePath = path.getAbsolutePath();
        logger.info(absolutePath);

        if (shouldSkip(AbstractCompiler.absolutePathToClassName(absolutePath))) {
            return;
        }

        compile(AbstractCompiler.classToPath(
                AbstractCompiler.absolutePathToClassName(absolutePath)
        ));

        if (cu.getPackageDeclaration().isPresent()) {
            processRestController(cu.getPackageDeclaration().get());
        }
    }

    private void processRestController(PackageDeclaration pd) throws IOException {
        expandWildCards(cu);

        TypeDeclaration<?> type = getPublicClass(cu);

        evaluator = new SpringEvaluator(type.getFullyQualifiedName().get());

        SpringTestGenerator generator = new SpringTestGenerator();
        evaluator.addGenerator(generator);
        generator.setCommonPath(getCommonPath());

        CompilationUnit gen = generator.getCompilationUnit();
        ClassOrInterfaceDeclaration cdecl = gen.addClass(type.getNameAsString() + "Test");
        cdecl.addExtendedType("TestHelper");
        gen.setPackageDeclaration(pd);

        allImports.addAll(cu.getImports());

        List<String> otherImports = (List<String>) Settings.getProperty("extra_imports");
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
         * Pass 1 : identify dependencies
         */
        cu.accept(new DepSolvingVisitor(), null);

        copyDependencies();

        /*
         * Pass 2 : Generate the tests
         */
        AntikytheraRunTime.reset();
        evaluator.setupFields(cu);
        cu.accept(new ControllerMethodVisitor(), null);

        for(ImportDeclaration imp : keepImports) {
            String name = imp.getNameAsString();
            if (! (AntikytheraRunTime.isServiceClass(name)
                    || AntikytheraRunTime.isInterface(name)
                    || AntikytheraRunTime.isAbstractClass(name)
                    || AntikytheraRunTime.isControllerClass(name)
                    || AntikytheraRunTime.isComponentClass(name))) {
                boolean found = false;
                var deps = dependencies.get(type.getFullyQualifiedName().get());
                if(deps != null) {
                    for (Dependency dependency : deps) {
                        if (dependency.getTo().equals(name)) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        gen.addImport(name);
                    }
                }
            }
        }

        ProjectGenerator.getInstance().writeFilesToTest(
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
            evaluatorUnsupported = false;

            if (checkEligible(md)) {
                evaluator.reset();
                evaluator.resetColors();
                AntikytheraRunTime.reset();
                try {
                    evaluator.visit(md);
                } catch (AntikytheraException | ReflectiveOperationException e) {
                    if (Settings.getProperty("dependencies.on_error").equals("log")) {
                        logger.warn("Could not complete processing {} due to {}", md.getName(), e.getMessage());
                    } else {
                        throw new GeneratorException(e);
                    }
                } finally {
                    logger.info(md.getNameAsString());
                }
            }
        }

        private boolean checkEligible(MethodDeclaration md) {
            if (md.getAnnotationByName("ExceptionHandler").isPresent()) {
                return false;
            }
            if (md.isPublic()) {
                Optional<String> ctrl  = Settings.getProperty("controllers", String.class);
                if(ctrl.isPresent()) {
                    String[] controllers = ctrl.get().split("#");
                    if (controllers.length > 1) {
                        if (md.getNameAsString().equals(controllers[controllers.length - 1])) {
                            return true;
                        }
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }


    /**
     * Class to resolve dependencies of the controller.
     *
     * Generating tests is a two step process, in the first step we will identify all the dependencies
     * and compile them with java parser. This is a requirement for efficient type resolution when
     * building the tests
     */
    class DepSolvingVisitor extends VoidVisitorAdapter<Void> {
        /**
         * Prepares the ground for the ControllerMethodVisitor to do it's work.
         *
         * This visitor will identify the dependencies of each method in the controller.
         */
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            evaluatorUnsupported = false;
            if (md.getAnnotationByName("ExceptionHandler").isPresent()) {
                return;
            }
            if (md.isPublic()) {
                stats.methods++;
                resolveMethodParameterTypes(md);
                md.accept(new ReturnStatmentVisitor(), md);
                md.accept(new StatementVisitor(), md);
            }
        }

        private void resolveMethodParameterTypes(MethodDeclaration md) {
            for(var param : md.getParameters()) {
                solveTypeDependencies(md.findAncestor(ClassOrInterfaceDeclaration.class).orElseGet(null),
                        param.getType());
                resolveImport(param.getType().asString());
            }
        }

        /**
         * The field visitor will be used to identify the types used by the controllers.
         *
         * @param field the field to inspect
         * @param arg not used
         */
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (var variable : field.getVariables()) {
                if (variable.getType().isClassOrInterfaceType()) {
                    String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
                    if (SpringEvaluator.getRepositories().containsKey(classToInstanceName(shortName))) {
                        return;
                    }
                    solveTypeDependencies(field.findAncestor(ClassOrInterfaceDeclaration.class).orElseGet(null), variable.getType());
                }
            }
        }


        /**
         * Nested inner class to handle return statements.
         * To generate tests, points of exit from a controller function are crucial. It maybe that the exit
         * happens due to a validation failure or because the method has completed successfully.
         * We will investigate the return to find that out and then based on the condition will taylor the
         * inputs accordingly.
         */
        class ReturnStatmentVisitor extends VoidVisitorAdapter<MethodDeclaration> {
            /**
             * This method will be called once for each return statment inside the method block.
             *
             * @param statement A statement that maybe a return statement.
             * @param md        the method declaration that contains the return statement.
             */
            @Override
            public void visit(ReturnStmt statement, MethodDeclaration md) {
                ReturnStmt stmt = statement.asReturnStmt();
                identifyReturnType(stmt, md);
            }
        }

        /**
         * Visit each statement in a method to identify the types used.
         * This is used to build a dependency graph so that classes that are part of the AUT can be processed
         * as needed. There is no need to process all classes. Only those in the dependency graph.
         */
        class StatementVisitor extends VoidVisitorAdapter<MethodDeclaration> {
            /**
             * This method will be called for each statement in a method.
             * @param statement the statment being processed
             * @param md the method declaration
             */
            @Override
            public void visit(ExpressionStmt statement, MethodDeclaration md) {
                super.visit(statement, md);
                Expression expr = statement.getExpression();
                if(expr.isVariableDeclarationExpr()) {
                    Type t = expr.asVariableDeclarationExpr().getElementType();
                    if (!t.isPrimitiveType()) {
                        TypeDeclaration<?> from = md.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                        if (from != null) {
                            createEdge(t, from);
                        }
                    }
                }
            }
        }

        private void identifyReturnType(ReturnStmt returnStmt, MethodDeclaration md) {
            TypeDeclaration<?> from = md.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
            Expression expression = returnStmt.getExpression().orElse(null);
            if (expression != null && from != null) {
                if (expression.isObjectCreationExpr()) {
                    ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
                    if (objectCreationExpr.getType().asString().contains("ResponseEntity")) {
                        for (Type typeArg : objectCreationExpr.getType().getTypeArguments().orElse(new NodeList<>())) {
                            solveTypeDependencies(from, typeArg);
                        }
                    }
                }
            }
        }
    }

    /**
     * Each method in the controller will be a child of the main path for that controller
     * which is represented by the RequestMapping for that controller
     *
     * @return the path from the RequestMapping Annotation or an empty string
     */
    private String getCommonPath() {
        for (var classAnnotation : getPublicClass(cu).getAnnotations()) {
            if (classAnnotation.getName().asString().equals("RequestMapping")) {
                if (classAnnotation.isNormalAnnotationExpr()) {
                    return classAnnotation.asNormalAnnotationExpr().getPairs().get(0).getValue().toString();
                } else {
                    var memberValue = classAnnotation.asSingleMemberAnnotationExpr().getMemberValue();
                    if(memberValue.isArrayInitializerExpr()) {
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

    public static class Stats {
        int controllers;
        int methods;
        int tests = 0;

        public int getControllers() {
            return controllers;
        }

        public int getMethods() {
            return methods;
        }

        public void setTests(int tests) {
            this.tests = tests;
        }

        public int getTests() {
            return tests;
        }
    }
}
