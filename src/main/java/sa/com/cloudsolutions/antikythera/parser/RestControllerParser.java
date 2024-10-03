package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import sa.com.cloudsolutions.antikythera.generator.ProjectGenerator;
import sa.com.cloudsolutions.antikythera.generator.SpringTestGenerator;

public class RestControllerParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);
    public static final String ANNOTATION_REQUEST_BODY = "@RequestBody";
    private final File controllers;

    private CompilationUnit gen;
    private HashMap<String, Object> parameterSet;

    private final Path dataPath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern controllerPattern = Pattern.compile(".*/([^/]+)\\.java$");

    private boolean evaluatorUnsupported = false;
    File current;
    private SpringEvaluator evaluator;

    /**
     * Store the conditions that a controller may expect the input to meet.
     */
    List<Expression> preConditions;

    /**
     * Creates a new RestControllerParser
     *
     * @param controllers either a folder containing many controllers or a single controller
     */
    public RestControllerParser(File controllers) throws IOException {
        super();
        this.controllers = controllers;

        dataPath = Paths.get(Settings.getProperty(Constants.OUTPUT_PATH).toString(), "src/test/resources/data");

        // Check if the dataPath directory exists, if not, create it
        if (!Files.exists(dataPath)) {
            Files.createDirectories(dataPath);
        }
        Files.createDirectories(Paths.get(Settings.getProperty(Constants.OUTPUT_PATH).toString(), "src/test/resources/uploads"));

    }

    public void start() throws IOException, EvaluatorException {
        processRestController(controllers);
    }

    private void processRestController(File path) throws IOException, EvaluatorException {
        current = path;

        logger.info(path.toString());
        if (path.isDirectory()) {
            int i = 0;
            for (File f : path.listFiles()) {
                if(f.toString().contains(controllers.toString())) {
                    new RestControllerParser(f).start();
                    i++;
                }
            }
            logger.info("Processed {} controllers", i);
        } else {
            String p = path.toString().replace("/",".");
            List<String> skip = (List<String>) Settings.getProperty("skip");
            if(skip != null) {
                for(String s : skip) {
                    if (p.endsWith(s)) {
                        return;
                    }
                }
            }

            Matcher matcher = controllerPattern.matcher(path.toString());

            String controllerName = null;
            if (matcher.find()) {
                controllerName = matcher.group(1);
            }
            parameterSet = new HashMap<>();
            FileInputStream in = new FileInputStream(path);
            cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
            if (cu.getPackageDeclaration().isPresent()) {
                processRestController(cu.getPackageDeclaration().get());
            }
            File file = new File(dataPath + File.separator + controllerName + "Params.json");
            objectMapper.writeValue(file, parameterSet);
        }
    }

    private void processRestController(PackageDeclaration pd) throws IOException, EvaluatorException {
        StringBuilder fileContent = new StringBuilder();
        expandWildCards(cu);

        evaluator = new SpringEvaluator();
        SpringTestGenerator generator = new SpringTestGenerator();
        evaluator.addGenerator(generator);
        generator.setCommonPath(getCommonPath());

        CompilationUnit gen = generator.getCompilationUnit();
        ClassOrInterfaceDeclaration cdecl = gen.addClass(cu.getTypes().get(0).getName() + "Test");
        cdecl.addExtendedType("TestHelper");
        gen.setPackageDeclaration(pd);

        gen.addImport("com.cloud.api.base.TestHelper");
        gen.addImport("org.testng.annotations.Test");
        gen.addImport("org.testng.Assert");
        gen.addImport("com.fasterxml.jackson.core.JsonProcessingException");
        gen.addImport("java.io.IOException");
        gen.addImport("java.util.List");
        gen.addImport("java.util.Map");
        gen.addImport("java.util.Date");
        gen.addImport("java.util.HashMap");
        gen.addImport("io.restassured.http.Method");
        gen.addImport("io.restassured.response.Response");
        gen.addImport("com.cloud.core.annotations.TestCaseType");
        gen.addImport("com.cloud.core.enums.TestType");

        /*
         * There is a very valid reason for doing this in two steps.
         * We want to make sure that all the repositories are identified before we start processing the methods.
         *
         */


        /*
         * Pass 1 : identify dependencies
         */
        cu.accept(new DepSolvingVisitor(), null);

        for (Map.Entry<String, Dependency> dep : dependencies.entrySet()) {
            gen.addImport(dep.getKey());
            copyDependencies(dep.getKey(), dep.getValue());
        }

        /*
         * Pass 2 : Generate the tests
         */
        evaluator.setupFields(cu);
        cu.accept(new ControllerMethodVisitor(), null);

        fileContent.append(gen.toString()).append("\n");
        ProjectGenerator.getInstance().writeFilesToTest(pd.getName().asString(), cu.getTypes().get(0).getName() + "Test.java",fileContent.toString());

    }


    /**
     * Extracts the type from a method call expression
     *
     * This is used when the controller directly returns the result of a method call.
     * we iterate through the imports to find the class. Then we iterate through the
     * methods in that class to identify what is being called. Finally when we find
     * the match, we extract it's type.
     *
     * todo - need to improve the handling of overloaded methods.
     *
     * @param type
     * @param methodCallExpr
     * @throws FileNotFoundException
     */
    private void extractTypeFromCall(Type type, MethodCallExpr methodCallExpr) throws IOException {
        for (var ref : cu.getImports()) {
            if (ref.getNameAsString().endsWith(type.asString())) {
                Path path = Paths.get(basePath, ref.getNameAsString().replace(".", "/"));
                File javaFile = new File(path + SUFFIX);

                ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
                JavaParser dependencyParser = new JavaParser(parserConfiguration);
                CompilationUnit dependencyCu = dependencyParser.parse(javaFile).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
                // get all non private methods in dependencyCu
                for (var dt : dependencyCu.getTypes()) {
                    for (var member : dt.getMembers()) {
                        if (member.isMethodDeclaration()) {
                            MethodDeclaration method = member.asMethodDeclaration();

                            if (!method.isPrivate() && method.getName().asString().equals(methodCallExpr.getNameAsString())) {
                                solveTypeDependencies(method.getType(), dependencyCu);
                            }
                        }
                    }
                }
                break;
            }
        }
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
            if (md.getAnnotationByName("ExceptionHandler").isPresent()) {
                return;
            }

            if (md.isPublic()) {
                preConditions = new ArrayList<>();
                try {
                    evaluator.executeMethod(md);
                } catch (EvaluatorException e) {
                    throw new RuntimeException(e);
                }
            }
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
                resolveMethodParameterTypes(md);
                md.accept(new ReturnStatmentVisitor(), null);
            }
        }

        private void resolveMethodParameterTypes(MethodDeclaration md) {
            for(var param : md.getParameters()) {
                solveTypeDependencies(param.getType(), cu);
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
                    solveTypeDependencies(variable.getType(), cu);
                }
            }
        }


        /**
         * Nested inner class to handle return statements.
         *
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

        private void identifyReturnType(ReturnStmt returnStmt, MethodDeclaration md) {
            TypeDeclaration<?> from = md.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
            Expression expression = returnStmt.getExpression().orElse(null);
            if (expression != null && from != null) {
                if (expression.isObjectCreationExpr()) {
                    ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
                    if (objectCreationExpr.getType().asString().contains("ResponseEntity")) {
                        for (Type typeArg : objectCreationExpr.getTypeArguments().orElse(new NodeList<>())) {
                            try {
                                String description = typeArg.resolve().describe();
                                if (!description.startsWith("java.")) {
                                    Dependency dependency = new Dependency(from, typeArg);
                                    for (var jarSolver : jarSolvers) {
                                        if (jarSolver.getKnownClasses().contains(description)) {
                                            dependency.setExtension(true);
                                            return;
                                        }
                                    }
                                    dependencies.put(description, dependency);
                                }

                            } catch (UnsolvedSymbolException e) {
                                logger.warn("Unresolvable {}", e);
                            }
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
        for (var classAnnotation : cu.getTypes().get(0).getAnnotations()) {
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
}
