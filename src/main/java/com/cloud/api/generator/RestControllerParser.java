package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.cloud.api.evaluator.Evaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
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
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

public class RestControllerParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);
    private final File controllers;

    Set<String> testMethodNames;
    private CompilationUnit gen;
    private HashMap<String, Object> parameterSet;
    private final Map<String, ?> typeDefsForPathVars = Map.of(
            "Integer", 1,
            "int", 1,
            "Long", 1L,
            "String", "Ibuprofen"
    );
    private final Path dataPath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Store the conditions that a controller may expect the input to meet.
     */
    List<Expression> preConditions;

    /**
     * Maintains a list of repositories that we have already encountered.
     */
    private static Map<String, RepositoryParser> respositories = new HashMap<>();
    private Map<String, Type> fields;
    Map<String, Type> variables;

    /**
     * Creates a new RestControllerParser
     *
     * @param controllers either a folder containing many controllers or a single controller
     */
    public RestControllerParser(File controllers) throws IOException {
        super();
        this.controllers = controllers;

        dataPath = Paths.get(Settings.getProperty(Constants.OUTPUT_PATH).toString(), "src/test/resources/data");

        if (!Files.exists(dataPath)) {
            Files.createDirectories(dataPath);
        }
    }

    public void start() throws IOException {
        processRestController(controllers);
    }

    private void processRestController(File path) throws IOException {
        logger.debug(path.toString());
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
            Pattern pattern = Pattern.compile(".*/([^/]+)\\.java$");
            Matcher matcher = pattern.matcher(path.toString());

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

    private void processRestController(PackageDeclaration pd) throws IOException {
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
        gen.addImport("io.restassured.http.Method");
        gen.addImport("io.restassured.response.Response");
        gen.addImport("com.cloud.core.annotations.TestCaseType");
        gen.addImport("com.cloud.core.enums.TestType");

        fields = new HashMap<>();

        /*
         * There is a very valid reason for doing this in two steps.
         * We want to make sure that all the repositories are identified before we start processing the methods.
         */
        cu.accept(new ControllerFieldVisitor(), null);
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
     * Find the return type of a method.
     *
     * This is a recursive function that will begin at the block that denotes the body of the method.
     * thereafter it will descend into child blocks that are parts of conditionals or try catch blocks
     * @Deprecated
     * @param blockStmt
     * @return
     */
    private Type findReturnType(BlockStmt blockStmt) {

        for (var stmt : blockStmt.getStatements()) {
            if (stmt.isExpressionStmt()) {
                Expression expr = stmt.asExpressionStmt().getExpression();
                if (expr.isVariableDeclarationExpr()) {
                    VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
                    variables.put(varDeclExpr.getVariable(0).getNameAsString(), varDeclExpr.getElementType());
                } else if (expr.isAssignExpr()) {
                    AssignExpr assignExpr = expr.asAssignExpr();
                    Expression left = assignExpr.getTarget();
                    if (left.isVariableDeclarationExpr()) {
                        VariableDeclarationExpr varDeclExpr = left.asVariableDeclarationExpr();
                        return varDeclExpr.getElementType();
                    }
                }
            } else if (stmt.isReturnStmt()) {
                ReturnStmt returnStmt = stmt.asReturnStmt();
                Expression expression = returnStmt.getExpression().orElse(null);
                if (expression != null && expression.isObjectCreationExpr()) {
                    ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
                    if (objectCreationExpr.getType().asString().contains("ResponseEntity")) {
                        Expression typeArg = objectCreationExpr.getArguments().get(0);
                        if (typeArg.isNameExpr()) {
                            return variables.get(typeArg.asNameExpr().getNameAsString());
                        }
                        if (typeArg.isMethodCallExpr()) {
                            MethodCallExpr methodCallExpr = null;
                            try {
                                methodCallExpr = typeArg.asMethodCallExpr();
                                Optional<Expression> scope = methodCallExpr.getScope();
                                if (scope.isPresent()) {
                                    Type type = (scope.get().isFieldAccessExpr())
                                            ? fields.get(scope.get().asFieldAccessExpr().getNameAsString())
                                            : fields.get(scope.get().asNameExpr().getNameAsString());
                                    extractTypeFromCall(type, methodCallExpr);
                                    logger.debug(type.toString());
                                }
                            } catch (IOException e) {
                                throw new GeneratorException("Exception while identifying dependencies", e);
                            }
                        }
                    }
                }
            } else if (stmt.isBlockStmt()) {
                return findReturnType(stmt.asBlockStmt());
            } else if (stmt.isTryStmt()) {
                return findReturnType(stmt.asTryStmt().getTryBlock());
            }
        }
        return null;
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
                                extractComplexType(method.getType(), dependencyCu);
                            }
                        }
                    }
                }
                break;
            }
        }
    }


    /**
     * Will be called for each method of the controller.
     *
     * Identifies the return types and the arguments of each method in the class.
     */
    private class ControllerFieldVisitor extends VoidVisitorAdapter<Void> {

        /**
         * The field visitor will be used to identify the repositories that are being used in the controller.
         *
         * @param field
         * @param arg
         */
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (var variable : field.getVariables()) {
                if (variable.getType().isClassOrInterfaceType()) {
                    String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
                    if (respositories.containsKey(shortName)) {
                        return;
                    }

                    Type t = variable.getType().asClassOrInterfaceType();
                    try {
                        String className = t.resolve().describe();
                        if (className.startsWith(basePackage)) {
                            ClassProcessor proc = new ClassProcessor();
                            proc.compile(AbstractCompiler.classToPath(className));
                            CompilationUnit cu = proc.getCompilationUnit();
                            for (var typeDecl : cu.getTypes()) {
                                if (typeDecl.isClassOrInterfaceDeclaration()) {
                                    ClassOrInterfaceDeclaration cdecl = typeDecl.asClassOrInterfaceDeclaration();
                                    if (cdecl.getNameAsString().equals(shortName)) {
                                        for (var ext : cdecl.getExtendedTypes()) {
                                            if (ext.getNameAsString().contains(RepositoryParser.JPA_REPOSITORY)) {
                                                RepositoryParser parser = new RepositoryParser();
                                                parser.compile(AbstractCompiler.classToPath(className));

                                                respositories.put(shortName, parser);
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (UnsolvedSymbolException e) {
                        logger.debug("ignore {}", t.toString());
                    } catch (IOException e) {
                        throw new GeneratorException("Exception while processing fields", e);
                    }
                }
                fields.put(variable.getNameAsString(), field.getElementType());
            }
        }
    }

    private class ControllerMethodVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            if (md.isPublic()) {
                preConditions = new ArrayList<>();
                if (md.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("ExceptionHandler"))) {
                    return;
                }
                if(md.getBody().isPresent()) {
                    identifyLocals(md.getBody().get());
                }
                testMethodNames = new HashSet<>();
                logger.debug("Method: {}\n", md.getName());
                Type returnType = null;
                Type methodType = md.getType();
                if (methodType.asString().contains("<")) {
                    if (!methodType.asString().endsWith("<Void>")) {
                        if (methodType.isClassOrInterfaceType()
                                && methodType.asClassOrInterfaceType().getTypeArguments().isPresent()
                                && !methodType.asClassOrInterfaceType().getTypeArguments().get().get(0).toString().equals("Object")
                        ) {
                            returnType = methodType.asClassOrInterfaceType().getTypeArguments().get().get(0);
                        } else {
                            BlockStmt blockStmt = md.getBody().orElseThrow(() -> new IllegalStateException("Method body not found"));
                            returnType = findReturnType(blockStmt);
                        }
                    }
                } else {
                    returnType = methodType;
                    if (returnType.toString().equals("ResponseEntity")) {
                        BlockStmt blockStmt = md.getBody().orElseThrow(() -> new IllegalStateException("Method body not found"));
                        returnType = findReturnType(blockStmt);
                    }
                }

                if (returnType != null) {
                    extractComplexType(returnType, cu);
                }
                for (var param : md.getParameters()) {
                    parameterSet.put(param.getName().toString(), "");
                    extractComplexType(param.getType(), cu);
                }

                md.accept(new MethodBlockVisitor(), md);
            }
        }

        private void identifyLocals(BlockStmt blockStmt) {
            variables = new HashMap<>();
            for (var stmt : blockStmt.getStatements()) {
                if (stmt.isExpressionStmt()) {
                    Expression expr = stmt.asExpressionStmt().getExpression();
                    if (expr.isVariableDeclarationExpr()) {
                        VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
                        variables.put(varDeclExpr.getVariable(0).getNameAsString(), varDeclExpr.getElementType());
                    }
                }
            }
        }
    }

    /**
     * A visitor that will iterate through the block of a method and identify the return statements.
     */
    private class MethodBlockVisitor extends VoidVisitorAdapter<MethodDeclaration> {
        Evaluator evaluator = new Evaluator();
        Map<String, Comparable> context = new HashMap<>();

        /**
         * This method will be called once for each return statment inside the method block.
         * @param stmt the return statement
         * @param md the method declaration that contains the return statement.
         */
        @Override
        public void visit(ReturnStmt stmt, MethodDeclaration md) {
            super.visit(stmt, md);
            Optional<Node> parent = stmt.getParentNode();

            if (parent.isPresent()) {
                // the return statement will have a parent no matter what but the optionals approach
                // requires the use of isPresent.

                BlockStmt blockStmt = (BlockStmt) parent.get();
                Optional<Node> gramps = blockStmt.getParentNode();
                if (gramps.isPresent()) {
                    if (gramps.get() instanceof IfStmt) {
                        // we have found ourselves a conditional return statement.
                        IfStmt ifStmt = (IfStmt) gramps.get();
                        Expression condition = ifStmt.getCondition();
                        if (evaluator.evaluateCondition(condition, context)) {
                            logger.debug("Condition is true");
                            identifyReturnType(stmt, md);

                            buildPreconditions(md, condition);
                        }
                    }
                    else if (gramps.get() instanceof MethodDeclaration) {
                        identifyReturnType(stmt, md);
                    }
                }

            }
        }

        private void buildPreconditions(MethodDeclaration md, Expression expr) {
            if(expr instanceof BinaryExpr) {
                buildPreconditions(md, expr.asBinaryExpr().getLeft());
                buildPreconditions(md, expr.asBinaryExpr().getRight());
            }
            if(expr instanceof MethodCallExpr) {
                MethodCallExpr mce = expr.asMethodCallExpr();
                Parameter reqBody = findRequestBody(md);
                if(reqBody.getNameAsString().equals(mce.getScope().get().toString())) {
                    try {
                        String fullClassName = reqBody.resolve().describeType();
                        String fieldName = classToInstanceName(mce.getName().asString().replace("get",""));

                        DTOHandler handler = new DTOHandler();
                        handler.compile(AbstractCompiler.classToPath(fullClassName));
                        Map<String, FieldDeclaration> fields = getFields(handler.getCompilationUnit(), reqBody.getTypeAsString());

                        FieldDeclaration fieldDeclaration = fields.get(fieldName);
                        if(fieldDeclaration != null) {
                            MethodCallExpr methodCall = DTOHandler.generateRandomValue(fieldDeclaration, handler.getCompilationUnit());
                            preConditions.add(methodCall);
                        }
                    } catch (UnsolvedSymbolException e) {
                        System.out.println("Unsolved symbol exception");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            else {
                System.out.println(expr);
            }
        }
        private ControllerResponse identifyReturnType(ReturnStmt returnStmt, MethodDeclaration md) {
            Expression expression = returnStmt.getExpression().orElse(null);
            if (expression != null && expression.isObjectCreationExpr()) {
                ControllerResponse response = new ControllerResponse();
                ObjectCreationExpr objectCreationExpr = expression.asObjectCreationExpr();
                if (objectCreationExpr.getType().asString().contains("ResponseEntity")) {
                    for(Expression typeArg : objectCreationExpr.getArguments()) {
                        if (typeArg.isFieldAccessExpr()) {
                            FieldAccessExpr fae = typeArg.asFieldAccessExpr();
                            if (fae.getScope().isNameExpr() && fae.getScope().toString().equals("HttpStatus")) {
                                response.setStatusCode(fae.getNameAsString());
                            }
                        }
                        if (typeArg.isNameExpr()) {
                            response.setType(variables.get(typeArg.asNameExpr().getNameAsString()));
                        } else if (typeArg.isStringLiteralExpr()) {
                            response.setType(StaticJavaParser.parseType("java.lang.String"));
                            response.setResponse(typeArg.asStringLiteralExpr().asString());
                        } else if (typeArg.isMethodCallExpr()) {
                            MethodCallExpr methodCallExpr = null;
                            try {
                                methodCallExpr = typeArg.asMethodCallExpr();
                                Optional<Expression> scope = methodCallExpr.getScope();
                                if (scope.isPresent()) {
                                    Type type = (scope.get().isFieldAccessExpr())
                                            ? fields.get(scope.get().asFieldAccessExpr().getNameAsString())
                                            : fields.get(scope.get().asNameExpr().getNameAsString());
                                    extractTypeFromCall(type, methodCallExpr);
                                    logger.debug(type.toString());
                                }
                            } catch (IOException e) {
                                throw new GeneratorException("Exception while identifying dependencies", e);
                            }
                        }
                    }
                }
                createTests(md, response);
                return response;
            }
            return null;
        }

        private void createTests(MethodDeclaration md, ControllerResponse returnType) {
            for (AnnotationExpr annotation : md.getAnnotations()) {
                if (annotation.getNameAsString().equals("GetMapping") ) {
                    buildGetMethodTests(md, annotation, returnType);
                }
                else if(annotation.getNameAsString().equals("PostMapping")) {
                    buildPostMethodTests(md, annotation, returnType);
                }
                else if(annotation.getNameAsString().equals("DeleteMapping")) {
                    buildDeleteMethodTests(md, annotation, returnType);
                }
                else if(annotation.getNameAsString().equals("RequestMapping") && annotation.isNormalAnnotationExpr()) {
                    NormalAnnotationExpr normalAnnotation = annotation.asNormalAnnotationExpr();
                    for (var pair : normalAnnotation.getPairs()) {
                        if (pair.getNameAsString().equals("method")) {
                            if (pair.getValue().toString().equals("RequestMethod.GET")) {
                                buildGetMethodTests(md, annotation, returnType);
                            }
                            if (pair.getValue().toString().equals("RequestMethod.POST")) {
                                buildPostMethodTests(md, annotation, returnType);
                            }
                            if (pair.getValue().toString().equals("RequestMethod.PUT")) {
                                buildPutMethodTests(md, annotation, returnType);
                            }
                            if (pair.getValue().toString().equals("RequestMethod.DELETE")) {
                                buildDeleteMethodTests(md, annotation, returnType);
                            }
                        }
                    }
                }
            }
        }

        private void buildDeleteMethodTests(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse returnType) {
            httpWithoutBody(md, annotation, "makeDelete");
        }

        private void buildPutMethodTests(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse returnType) {
            httpWithBody(md, annotation, returnType, "makePut");
        }

        private MethodDeclaration buildTestMethod(MethodDeclaration md) {
            MethodDeclaration testMethod = new MethodDeclaration();

            NormalAnnotationExpr testCaseTypeAnnotation = new NormalAnnotationExpr();
            testCaseTypeAnnotation.setName("TestCaseType");
            testCaseTypeAnnotation.addPair("types", "{TestType.BVT, TestType.REGRESSION}");

            testMethod.addAnnotation(testCaseTypeAnnotation);
            testMethod.addAnnotation("Test");
            StringBuilder paramNames = new StringBuilder();
            for(var param : md.getParameters()) {
                for(var ann : param.getAnnotations()) {
                    if(ann.getNameAsString().equals("PathVariable")) {
                        paramNames.append(param.getNameAsString().substring(0, 1).toUpperCase())
                                .append(param.getNameAsString().substring(1));
                        break;
                    }
                }
            }

            String testName = String.valueOf(md.getName());
            if (paramNames.isEmpty()) {
                testName += "Test";
            } else {
                testName += "By" + paramNames + "Test";

            }

            if (testMethodNames.contains(testName)) {
                testName += "_" + (char)('A' + testMethodNames.size() -1);
            }
            testMethodNames.add(testName);
            testMethod.setName(testName);

            BlockStmt body = new BlockStmt();

            testMethod.setType(new VoidType());

            testMethod.setBody(body);
            return testMethod;
        }

        private void addCheckStatus(MethodDeclaration md) {
            MethodCallExpr check = new MethodCallExpr("checkStatusCode");
            check.addArgument(new NameExpr("response"));
            md.getBody().get().addStatement(new ExpressionStmt(check));
        }

        private void buildGetMethodTests(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse returnType) {
            httpWithoutBody(md, annotation, "makeGet");
        }

        private void httpWithoutBody(MethodDeclaration md, AnnotationExpr annotation, String call) {
            MethodDeclaration testMethod = buildTestMethod(md);
            MethodCallExpr makeGetCall = new MethodCallExpr(call);
            makeGetCall.addArgument(new NameExpr("headers"));

            if(md.getParameters().isEmpty()) {
                makeGetCall.addArgument(new StringLiteralExpr(getCommonPath().replace("\"", "")));
            }
            else {
                String path = handlePathVariables(md, getPath(annotation).replace("\"", ""));
                makeGetCall.addArgument(new StringLiteralExpr(path));
            }
            VariableDeclarationExpr responseVar = new VariableDeclarationExpr(new ClassOrInterfaceType(null, "Response"), "response");
            AssignExpr assignExpr = new AssignExpr(responseVar, makeGetCall, AssignExpr.Operator.ASSIGN);
            BlockStmt body = testMethod.getBody().get();
            body.addStatement(new ExpressionStmt(assignExpr));

            addCheckStatus(testMethod);
            gen.getType(0).addMember(testMethod);

        }

        private void buildPostMethodTests(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse returnType) {
            httpWithBody(md, annotation, returnType, "makePost");
        }

        private void httpWithBody(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse resp, String call) {

            MethodDeclaration testMethod = buildTestMethod(md);
            MethodCallExpr makeGetCall = new MethodCallExpr(call);


            if(md.getParameters().isNonEmpty()) {
                Parameter requestBody = findRequestBody(md);
                String path = handlePathVariables(md, getPath(annotation).replace("\"", ""));
                String paramClassName = requestBody.getTypeAsString();

                BlockStmt blockStmt = testMethod.getBody().get();
                if(requestBody.getType().isClassOrInterfaceType()) {
                    var cdecl = requestBody.getType().asClassOrInterfaceType();
                    switch(cdecl.getNameAsString()) {
                        case "List": {
                            prepareBody("java.util.List", new ClassOrInterfaceType(null, paramClassName), "List.of", testMethod);
                            break;
                        }

                        case "Set": {
                            prepareBody("java.util.Set", new ClassOrInterfaceType(null, paramClassName), "Set.of", testMethod);
                            break;
                        }

                        case "Map": {
                            prepareBody("java.util.Map", new ClassOrInterfaceType(null, paramClassName), "Map.of", testMethod);
                            break;
                        }
                        case "Integer":
                        case "Long": {
                            VariableDeclarator variableDeclarator = new VariableDeclarator(new ClassOrInterfaceType(null, "long"), "req");
                            variableDeclarator.setInitializer("0");
                            blockStmt.addStatement(new VariableDeclarationExpr(variableDeclarator));

                            break;
                        }

                        case "Object": {
                            // SOme methods incorrectly have their DTO listed as of type Object. We will treat
                            // as a String
                            prepareBody("java.lang.String", new ClassOrInterfaceType(null, "String"), "new String", testMethod);
                            break;
                        }

                        default:
                            ClassOrInterfaceType csiGridDtoType = new ClassOrInterfaceType(null, paramClassName);
                            VariableDeclarator variableDeclarator = new VariableDeclarator(csiGridDtoType, "req");
                            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, csiGridDtoType, new NodeList<>());
                            variableDeclarator.setInitializer(objectCreationExpr);
                            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
                            blockStmt.addStatement(variableDeclarationExpr);
                    }

                    for(Expression expr : preConditions) {
                        if(expr.isMethodCallExpr()) {
                            String s = expr.toString();
                            if(s.contains("set")) {
                                String[] parts = s.split("\\.");
                                blockStmt.addStatement(String.format("req.%s;", parts[1]));
                            }
                        }
                    }

                    MethodCallExpr writeValueAsStringCall = new MethodCallExpr(new NameExpr("objectMapper"), "writeValueAsString");
                    writeValueAsStringCall.addArgument(new NameExpr("req"));
                    makeGetCall.addArgument(writeValueAsStringCall);
                    testMethod.addThrownException(new ClassOrInterfaceType(null, "JsonProcessingException"));
                    makeGetCall.addArgument(new NameExpr("headers"));

                }
                makeGetCall.addArgument(new StringLiteralExpr(path));

                gen.getType(0).addMember(testMethod);

                VariableDeclarationExpr responseVar = new VariableDeclarationExpr(new ClassOrInterfaceType(null, "Response"), "response");
                AssignExpr assignExpr = new AssignExpr(responseVar, makeGetCall, AssignExpr.Operator.ASSIGN);
                blockStmt.addStatement(new ExpressionStmt(assignExpr));


                addHttpStatusCheck(blockStmt, resp.getStatusCode());
                Type returnType = resp.getType();
                if(returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    System.out.println();
                } else if(!
                        (returnType.toString().equals("void") || returnType.toString().equals("CompletableFuture"))) {
                    Type respType = new ClassOrInterfaceType(null, returnType.asClassOrInterfaceType().getNameAsString());
                    if (respType.toString().equals("String")) {
                        blockStmt.addStatement("String resp = response.getBody().asString();");
                        blockStmt.addStatement(String.format("Assert.assertEquals(resp,\"%s\");",resp.getResponse().toString()));
                    }
                    else {
                        VariableDeclarator variableDeclarator = new VariableDeclarator(respType, "resp");
                        MethodCallExpr methodCallExpr = new MethodCallExpr(new NameExpr("response"), "as");
                        methodCallExpr.addArgument(returnType.asClassOrInterfaceType().getNameAsString() + ".class");
                        variableDeclarator.setInitializer(methodCallExpr);
                        VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
                        ExpressionStmt expressionStmt = new ExpressionStmt(variableDeclarationExpr);
                        blockStmt.addStatement(expressionStmt);
                    }
                }


            }
        }

        private void prepareBody(String e, ClassOrInterfaceType paramClassName, String name, MethodDeclaration testMethod) {
            dependencies.add(e);
            VariableDeclarator variableDeclarator = new VariableDeclarator(paramClassName, "req");
            MethodCallExpr methodCallExpr = new MethodCallExpr(name);
            variableDeclarator.setInitializer(methodCallExpr);
            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);

            testMethod.getBody().get().addStatement(variableDeclarationExpr);
        }
    }

    private void addHttpStatusCheck(BlockStmt blockStmt, int statusCode)
    {
        // Create a method call expression for response.getStatusCode()
        MethodCallExpr getStatusCodeCall = new MethodCallExpr(new NameExpr("response"), "getStatusCode");

        // Create a binary expression to compare the status code with 200
        BinaryExpr comparison = new BinaryExpr(getStatusCodeCall, new IntegerLiteralExpr(statusCode), BinaryExpr.Operator.EQUALS);

        // Create a method call expression for Assert.assertTrue
        MethodCallExpr assertTrueCall = new MethodCallExpr(new NameExpr("Assert"), "assertTrue");
        assertTrueCall.addArgument(comparison);

        // Add the assertTrue call to the block statement
        blockStmt.addStatement(new ExpressionStmt(assertTrueCall));
    }

    private String handlePathVariables(MethodDeclaration md, String path){
        for(var param : md.getParameters()) {
            String paramString = String.valueOf(param);
            if(!paramString.startsWith("@RequestBody")){
                String paramName = getParamName(param);
                switch(param.getTypeAsString()) {
                    case "Boolean":
                        path = path.replace('{' + paramName +'}', "false");
                        break;

                    case "float":
                    case "Float":
                    case "double":
                    case "Double":
                        path = path.replace('{' + paramName +'}', "1.0");
                        break;

                    case "Integer":
                    case "int":
                    case "Long":
                        path = path.replace('{' + paramName +'}', "1");
                        break;

                    case "String":
                        path = path.replace('{' + paramName +'}', "Ibuprofen");

                    default:
                        // some get methods rely on an enum.
                        // todo handle this properly
                        path = path.replace('{' + paramName +'}', "0");

                }
            }
        }
        return path;
    }

    private static String getParamName(Parameter param) {
        String paramString = String.valueOf(param);
        if(paramString.startsWith("@PathVariable")) {
            Optional<AnnotationExpr> ann = param.getAnnotations().stream().findFirst();
            if(ann.isPresent()) {
                if(ann.get().isSingleMemberAnnotationExpr()) {
                    return ann.get().asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
                }
                if(ann.get().isNormalAnnotationExpr()) {
                    for (var pair : ann.get().asNormalAnnotationExpr().getPairs()) {
                        if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("name")) {
                            return pair.getValue().toString().replace("\"", "");
                        }
                    }
                }
            }
        }
        return param.getNameAsString();
    }

    /**
     * Of the various params in the method, which one is the RequestBody
     * @param md a method argument
     * @return the parameter identified as the RequestBody
     */
    private Parameter findRequestBody(MethodDeclaration md) {
        Parameter requestBody = md.getParameter(0);
        for(var param : md.getParameters()) {
            if(param.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("RequestBody"))) {
                return param;
            }
        }
        return requestBody;
    }

    /**
     * Given an annotation for a method in a controller find the full path in the url
     * @param annotation a GetMapping, PostMapping etc
     * @return the path url component
     */
    private String getPath(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return getCommonPath() + annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
        } else if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotation = annotation.asNormalAnnotationExpr();
            for (var pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("path") || pair.getNameAsString().equals("value")) {
                    return getCommonPath() + pair.getValue().toString();
                }
            }
        }
        return getCommonPath();
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

    public Map<String, Type> getFields() {
        return fields;
    }
}


