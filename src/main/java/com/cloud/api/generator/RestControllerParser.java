package com.cloud.api.generator;

import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.cloud.api.constants.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.SpringEvaluator;
import sa.com.cloudsolutions.antikythera.generator.ProjectGenerator;

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
        gen = new CompilationUnit();

        ClassOrInterfaceDeclaration cdecl = gen.addClass(cu.getTypes().get(0).getName() + "Test");
        cdecl.addExtendedType("TestHelper");

        gen.setPackageDeclaration(pd);

        expandWildCards(cu);

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


        evaluator = new SpringEvaluator();
        /*
         * There is a very valid reason for doing this in two steps.
         * We want to make sure that all the repositories are identified before we start processing the methods.
         *
         */
        evaluator.setupFields(cu);

        cu.accept(new ControllerMethodVisitor(), null);

        for (String s : dependencies) {
            gen.addImport(s);
        }
        for(String s: externalDependencies) {
            gen.addImport(s);
        }

        fileContent.append(gen.toString()).append("\n");
        ProjectGenerator.getInstance().writeFilesToTest(pd.getName().asString(), cu.getTypes().get(0).getName() + "Test.java",fileContent.toString());

        for(String dependency : dependencies) {
            copyDependencies(dependency);
        }

        dependencies.clear();
        externalDependencies.clear();
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
     * Visitor that will detect methods in the controller.
     *
     */
    private class ControllerMethodVisitor extends VoidVisitorAdapter<Void> {
        /*
         * Every public method in the source code will result in a call to the visit(MethodDeclaration...)
         * method of this class. In there will try to identify locals and whether any repository queries
         * are being executed. Armed with that information we will then use the return statement visitor
         * to identify the return type of the method and thereafter to generate the tests.
         */
        RepositoryQuery last = null;

        /**
         * This method will be called for each method call expression associated with a variable assignment.
         * @param node a node from an expression statement, which may have a method call expression.
         *             The result of which will be used in the variable assignment by the caller
         * @param arg The list of variables that are being assigned.
         *            Most likely to contain a single node
         * @return A repository query, if the variable assignment is the result of a query execution or null.
         */
        public RepositoryQuery processMCE(Node node, NodeList<VariableDeclarator> arg) {

            if (node instanceof MethodCallExpr) {
                MethodCallExpr mce = ((MethodCallExpr) node).asMethodCallExpr();
                Optional<Expression> scope = mce.getScope();
                if(scope.isPresent()) {


                }
            }
            else {
                for(Node n : node.getChildNodes()) {
                    RepositoryQuery q = processMCE(n, arg);
                    if(q != null) {
                        return q;
                    }
                }
            }
            return null;
        }

        /**
         * Prepares the ground for the MethodBLockVisitor to do it's work.
         *
         * reset the preConditions list
         * identify the locals
         * reset the context
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
                resolveMethodParameterTypes(md);

                try {
                    evaluator.executeMethod(md);
                } catch (EvaluatorException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private void resolveMethodParameterTypes(MethodDeclaration md) {
            for(var param : md.getParameters()) {
                solveTypeDependencies(param.getType(), cu);
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
                Optional<Node> parent = stmt.getParentNode();

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


