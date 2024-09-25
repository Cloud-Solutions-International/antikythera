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

import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.*;
import java.lang.reflect.Method;
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

public class RestControllerParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);
    public static final String ANNOTATION_REQUEST_BODY = "@RequestBody";
    private final File controllers;

    Set<String> testMethodNames;
    private CompilationUnit gen;
    private HashMap<String, Object> parameterSet;

    private final Path dataPath;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern controllerPattern = Pattern.compile(".*/([^/]+)\\.java$");

    private boolean evaluatorUnsupported = false;
    File current;
    private Evaluator evaluator;

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

    public void start() throws IOException {
        processRestController(controllers);
    }

    private void processRestController(File path) throws IOException {
        current = path;
        testMethodNames = new HashSet<>();
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
        gen.addImport("java.util.Map");
        gen.addImport("java.util.Date");
        gen.addImport("java.util.HashMap");
        gen.addImport("io.restassured.http.Method");
        gen.addImport("io.restassured.response.Response");
        gen.addImport("com.cloud.core.annotations.TestCaseType");
        gen.addImport("com.cloud.core.enums.TestType");


        evaluator = new Evaluator();
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
     * Will be called for each field of the controller.
     * Primary purpose is to identify services and repositories that are being used in the controller.
     *
     */
    private class ControllerFieldVisitor extends VoidVisitorAdapter<Void> {

        /**
         * The field visitor will be used to identify the repositories that are being used in the controller.
         *
         * @param field the field to inspect
         * @param arg not used
         */
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (var variable : field.getVariables()) {
                try {
                    if (variable.getType().isClassOrInterfaceType()) {
                        String shortName = variable.getType().asClassOrInterfaceType().getNameAsString();
                        if (Evaluator.getRespositories().containsKey(shortName)) {
                            return;
                        }
                        Type t = variable.getType().asClassOrInterfaceType();
                        evaluator.identifyFieldVariables(t, shortName);
                    }
                     evaluator.setField(variable.getNameAsString(), field.getElementType());
                } catch (UnsolvedSymbolException e) {
                    logger.debug("ignore {}", variable);
                } catch (IOException e) {
                    String action = Settings.getProperty("dependencies.on_error").toString();
                    if(action == null || action.equals("exit")) {
                        throw new GeneratorException("Exception while processing fields", e);
                    }
                    logger.error("Exception while processing fields");
                    logger.error("\t{}",e.getMessage());
                }
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
        public Object processMethodCallExpression(Node node, NodeList<VariableDeclarator> arg) {

            if (node instanceof MethodCallExpr) {
                MethodCallExpr mce = ((MethodCallExpr) node).asMethodCallExpr();
                Optional<Expression> scope = mce.getScope();
                if(scope.isPresent()) {
                    Map<String, Evaluator.Variable> fields = evaluator.getFields();
                    var obj = fields.get(scope.get().toString());
                    if (obj != null) {
                        RepositoryParser repository = Evaluator.getRespositories().get(obj.toString());
                        if (repository != null) {
                            /*
                             * This method call expression is associated with a repository query.
                             */
                            RepositoryQuery q = repository.getQueries().get(mce.getNameAsString());
                            try {
                                /*
                                 * We have one more challenge; to find the parameters that are being used in the repository
                                 * method. These will then have to be mapped to the jdbc place holders and reverse mapped
                                 * to the arguments that are passed in when the method is actually being called.
                                 */
                                MethodDeclaration repoMethod = repository.getCompilationUnit().getTypes().get(0).getMethodsByName(mce.getNameAsString()).get(0);
                                for (int i = 0, j = mce.getArguments().size(); i < j; i++) {
                                    q.getMethodArguments().add(new RepositoryQuery.QueryMethodArgument(mce.getArgument(i), i));
                                    q.getMethodParameters().add(new RepositoryQuery.QueryMethodParameter(repoMethod.getParameter(i), i));
                                }

                                ResultSet rs = repository.executeQuery(mce.getNameAsString(), q);
                                q.setResultSet(rs);
                            } catch (Exception e) {
                                logger.warn(e.getMessage());
                                logger.warn("Could not execute query {}", mce);
                            }
                            return q;
                        }
                        else {
                            try {
                                if(obj.getValue() != null) {
                                    Class<?> clazz = obj.getValue().getClass();
                                    Method method = clazz.getMethod(mce.getNameAsString());
                                    Object result = method.invoke(obj);
                                    System.out.println(result);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            return evaluator.getValue(scope.get().toString());
                        }
                    }
                    else {
                        if(scope.get().isMethodCallExpr()) {
                            return processMethodCallExpression(scope.get(), arg);
                        }
                        Object val = evaluator.getValue(scope.get().toString());
                        if(val != null) {
                            System.out.println("bada");
                        }
                    }
                }
            }
            else {
                for(Node n : node.getChildNodes()) {
                    Object q = processMethodCallExpression(n, arg);
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
            if (md.isPublic()) {
                preConditions = new ArrayList<>();

                buildContext(md);

                if (md.getAnnotationByName("ExceptionHandler").isPresent()) {
                    return;
                }
                Optional<BlockStmt> body = md.getBody();
                if(body.isPresent()) {
                    logger.info("Method: {}", md.getName());
                    last = null;

                    for(Statement st : body.get().getStatements()) {
                        //NodeList<VariableDeclarator> variables = evaluator.identifyLocals(st);
                        if (st.isExpressionStmt()) {
                            /*
                             * we have just encountered a variable assignment.
                             *
                             * If the variable assignment is associated with a repository query, the visitor
                             * will return a non-null value.
                             */
                            Object query = processMethodCallExpression(st, null);
                            if (query != null && query instanceof RepositoryQuery &&
                                    ((RepositoryQuery)query).getResultSet() != null) {
                                last = (RepositoryQuery)query;
                            }
                        }
                        else {
                            st.accept(new ReturnStatmentVisitor(), md);
                        }
                    }
                }
            }
        }

        private void buildContext(MethodDeclaration md) {

            for(var param : md.getParameters()) {
                evaluator.setArgument(param.getNameAsString(), null);
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
                try {
                    if (parent.isPresent() && !evaluatorUnsupported) {
                        // the return statement will have a parent no matter what but the optionals approach
                        // requires the use of isPresent.
                        if (parent.get() instanceof IfStmt) {
                            IfStmt ifStmt = (IfStmt) parent.get();
                            Expression condition = ifStmt.getCondition();
                            if (evaluator.evaluateCondition(condition)) {
                                identifyReturnType(stmt, md);
                                buildPreconditions(md, condition);
                            }
                        } else {
                            BlockStmt blockStmt = (BlockStmt) parent.get();
                            Optional<Node> gramps = blockStmt.getParentNode();
                            if (gramps.isPresent()) {
                                if (gramps.get() instanceof IfStmt) {
                                    // we have found ourselves a conditional return statement.
                                    IfStmt ifStmt = (IfStmt) gramps.get();
                                    Expression condition = ifStmt.getCondition();
                                    if (evaluator.evaluateCondition(condition)) {
                                        identifyReturnType(stmt, md);
                                        buildPreconditions(md, condition);
                                    }
                                } else if (gramps.get() instanceof MethodDeclaration) {
                                    identifyReturnType(stmt, md);
                                }
                            }
                        }
                    }
                } catch (EvaluatorException e) {
                    logger.error("Evaluator exception");
                    logger.error("\t{}", e.getMessage());
                    evaluatorUnsupported = true;
                }
            }
        }

        private void buildPreconditions(MethodDeclaration md, Expression expr) {
            if(expr instanceof BinaryExpr) {
                BinaryExpr binaryExpr = expr.asBinaryExpr();
                if(binaryExpr.getOperator().equals(BinaryExpr.Operator.AND) || binaryExpr.getOperator().equals(BinaryExpr.Operator.OR)) {
                    buildPreconditions(md, binaryExpr.getLeft());
                    buildPreconditions(md, binaryExpr.getRight());
                }
                else {
                    buildPreconditions(md, binaryExpr.getLeft());
                    buildPreconditions(md, binaryExpr.getRight());
                }
            }
            if(expr instanceof MethodCallExpr) {
                MethodCallExpr mce = expr.asMethodCallExpr();
                Parameter reqBody = findRequestBody(md);
                if(reqBody != null && reqBody.getNameAsString().equals(mce.getScope().get().toString())) {
                    try {
                        if(!reqBody.getType().asClassOrInterfaceType().getTypeArguments().isPresent()) {

                            String fullClassName = reqBody.resolve().describeType();
                            String fieldName = classToInstanceName(mce.getName().asString().replace("get", ""));

                            DTOHandler handler = new DTOHandler();
                            handler.compile(AbstractCompiler.classToPath(fullClassName));
                            Map<String, FieldDeclaration> fields = getFields(handler.getCompilationUnit(), reqBody.getTypeAsString());

                            FieldDeclaration fieldDeclaration = fields.get(fieldName);
                            if (fieldDeclaration != null) {
                                MethodCallExpr methodCall = DTOHandler.generateRandomValue(fieldDeclaration, handler.getCompilationUnit());
                                preConditions.add(methodCall);
                            }
                        }
                    } catch (UnsolvedSymbolException e) {
                        logger.warn("Unsolved symbol exception");
                    } catch (IOException e) {
                        logger.error("Current controller: {}", current);
                        if(Settings.getProperty("dependencies.on_error").toString().equals("exit")) {
                            throw new GeneratorException("Exception while identifying dependencies", e);
                        }
                        logger.error(e.getMessage());
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
                            String nameAsString = typeArg.asNameExpr().getNameAsString();
                            if(nameAsString != null && evaluator.getLocal(nameAsString) != null) {
                                response.setType(evaluator.getLocal(nameAsString).getType());
                            }
                            else {
                                logger.warn("NameExpr is null in identify return type");
                            }
                        } else if (typeArg.isStringLiteralExpr()) {
                            response.setType(StaticJavaParser.parseType("java.lang.String"));
                            response.setResponse(typeArg.asStringLiteralExpr().asString());
                        } else if (typeArg.isMethodCallExpr()) {
                            MethodCallExpr methodCallExpr = typeArg.asMethodCallExpr();
                            try {
                                Optional<Expression> scope = methodCallExpr.getScope();
                                if (scope.isPresent()) {
                                    Expression exprScope = scope.get();
                                    Map<String, Evaluator.Variable> fields = evaluator.getFields();

                                    Evaluator.Variable variable = (exprScope.isFieldAccessExpr())
                                            ? fields.get(exprScope.asFieldAccessExpr().getNameAsString())
                                            : fields.get(exprScope.asNameExpr().getNameAsString());
                                    if(variable != null) {
                                        extractTypeFromCall(variable.getType(), methodCallExpr);
                                        logger.debug(variable.getType().toString());
                                    }
                                    else {
                                        logger.debug("Type not found {}", exprScope);
                                    }
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

        /**
         * Create tests based on the method declarion and return type
         * @param md
         * @param returnType
         */
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
                testName += "_" + (char)('A' + testMethodNames.size()  % 26 -1);
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

        private void httpWithoutBody(MethodDeclaration md, AnnotationExpr annotation, String call)  {
            MethodDeclaration testMethod = buildTestMethod(md);
            MethodCallExpr makeGetCall = new MethodCallExpr(call);
            makeGetCall.addArgument(new NameExpr("headers"));
            BlockStmt body = testMethod.getBody().get();

            if(md.getParameters().isEmpty()) {
                /*
                 * Empty parameters are very easy.
                 */
                makeGetCall.addArgument(new StringLiteralExpr(getCommonPath().replace("\"", "")));
            }
            else {
                /*
                 * Non empty parameters.
                 */
                ControllerRequest request = new ControllerRequest();
                request.setPath(getPath(annotation).replace("\"", ""));

                try {
                    replaceURIVariablesFromDb(md, request);
                } catch (SQLException e) {
                    logger.warn(e.getMessage());
                }
                handleURIVariables(md, request);

                makeGetCall.addArgument(new StringLiteralExpr(request.getPath()));
                if(!request.getQueryParameters().isEmpty()) {
                    body.addStatement("Map<String, String> queryParams = new HashMap<>();");
                    for(Map.Entry<String, String> entry : request.getQueryParameters().entrySet()) {
                        body.addStatement(String.format("queryParams.put(\"%s\", \"%s\");", entry.getKey(), entry.getValue()));
                    }
                    makeGetCall.addArgument(new NameExpr("queryParams"));
                }
            }
            VariableDeclarationExpr responseVar = new VariableDeclarationExpr(new ClassOrInterfaceType(null, "Response"), "response");
            AssignExpr assignExpr = new AssignExpr(responseVar, makeGetCall, AssignExpr.Operator.ASSIGN);

            body.addStatement(new ExpressionStmt(assignExpr));

            addCheckStatus(testMethod);
            gen.getType(0).addMember(testMethod);

        }

        /*
         * Replace PathVariable and RequestParam values with the values from the database.
         *
          We need to figure out if any of the path or request parameters are supposed to
         * match the values from the database.
         *
         * If the last field is not null that means there is likely to be a query associated
         * with those parameters.
         *
         * Mapping parameters works like this.
         *    Request or path parameter becomes an argument to a method call.
         *    The argument in the method call becomes a parameter for a placeholder
         *    The placeholder may have been removed though!
         */
        private void replaceURIVariablesFromDb(MethodDeclaration md, ControllerRequest request) throws SQLException {
            if (last != null && last.getResultSet() != null) {
                ResultSet rs = last.getResultSet();
                List<RepositoryQuery.QueryMethodParameter> paramMap = last.getMethodParameters();
                List<RepositoryQuery.QueryMethodArgument> argsMap = last.getMethodArguments();

                if(rs.next()) {
                    for(int i = 0 ; i < paramMap.size() ; i++) {
                        RepositoryQuery.QueryMethodParameter param = paramMap.get(i);
                        RepositoryQuery.QueryMethodArgument arg = argsMap.get(i);

                        if(param.getColumnName() != null) {
                            String[] parts = param.getColumnName().split("\\.");
                            String col = parts.length > 1 ? parts[1] : parts[0];

                            logger.debug(param.getColumnName() + " " + arg.getArgument() + " " + rs.getObject(col));

                            // finally try to match it against the path and request variables
                            for (Parameter p : md.getParameters()) {
                                Optional<AnnotationExpr> requestParam = p.getAnnotationByName("RequestParam");
                                Optional<AnnotationExpr> pathParam = p.getAnnotationByName("PathVariable");
                                if (requestParam.isPresent()) {
                                    String name = getParamName(p);
                                    if (name.equals(arg.getArgument().toString())) {
                                        request.getQueryParameters().put(name, rs.getObject(col).toString());
                                    }
                                } else if (pathParam.isPresent()) {
                                    String name = getParamName(p);
                                    final String target = '{' + name + '}';
                                    if (name.equals(arg.getArgument().toString())) {
                                        request.setPath(request.getPath().replace(target, rs.getObject(col).toString()));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        private void buildPostMethodTests(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse returnType) {
            httpWithBody(md, annotation, returnType, "makePost");
        }

        private void httpWithBody(MethodDeclaration md, AnnotationExpr annotation, ControllerResponse resp, String call) {

            MethodDeclaration testMethod = buildTestMethod(md);
            MethodCallExpr makePost = new MethodCallExpr(call);
            BlockStmt body = testMethod.getBody().get();

            ControllerRequest request = new ControllerRequest();
            request.setPath(getPath(annotation).replace("\"", ""));
            handleURIVariables(md, request);

            if(md.getParameters().isNonEmpty()) {
                Parameter requestBody = findRequestBody(md);
                if(requestBody != null) {
                    String paramClassName = requestBody.getTypeAsString();

                    if (requestBody.getType().isClassOrInterfaceType()) {
                        var cdecl = requestBody.getType().asClassOrInterfaceType();
                        switch (cdecl.getNameAsString()) {
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
                                body.addStatement(new VariableDeclarationExpr(variableDeclarator));

                                break;
                            }

                            case "MultipartFile": {
                                dependencies.add("org.springframework.web.multipart.MultipartFile");
                                ClassOrInterfaceType multipartFile = new ClassOrInterfaceType(null, "MultipartFile");
                                VariableDeclarator variableDeclarator = new VariableDeclarator(multipartFile, "req");
                                MethodCallExpr methodCallExpr = new MethodCallExpr("uploadFile");
                                methodCallExpr.addArgument(new StringLiteralExpr(testMethod.getNameAsString()));
                                variableDeclarator.setInitializer(methodCallExpr);
                                testMethod.getBody().get().addStatement(new VariableDeclarationExpr(variableDeclarator));
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
                                body.addStatement(variableDeclarationExpr);
                        }

                        for (Expression expr : preConditions) {
                            if (expr.isMethodCallExpr()) {
                                String s = expr.toString();
                                if (s.contains("set")) {
                                    body.addStatement(s.replaceFirst("^[^.]+\\.", "req.") + ";");
                                }
                            }
                        }

                        if (cdecl.getNameAsString().equals("MultipartFile")) {
                            makePost.addArgument(new NameExpr("req"));
                            testMethod.addThrownException(new ClassOrInterfaceType(null, "IOException"));
                        } else {
                            MethodCallExpr writeValueAsStringCall = new MethodCallExpr(new NameExpr("objectMapper"), "writeValueAsString");
                            writeValueAsStringCall.addArgument(new NameExpr("req"));
                            makePost.addArgument(writeValueAsStringCall);
                            testMethod.addThrownException(new ClassOrInterfaceType(null, "JsonProcessingException"));
                        }
                    }
                }
                else {
                    makePost.addArgument(new StringLiteralExpr(""));
                    logger.warn("No RequestBody found for {}", md.getName());
                }
            }

            prepareRequest(makePost, request, body);

            gen.getType(0).addMember(testMethod);

            VariableDeclarationExpr responseVar = new VariableDeclarationExpr(new ClassOrInterfaceType(null, "Response"), "response");
            AssignExpr assignExpr = new AssignExpr(responseVar, makePost, AssignExpr.Operator.ASSIGN);
            body.addStatement(new ExpressionStmt(assignExpr));


            addHttpStatusCheck(body, resp.getStatusCode());
            Type returnType = resp.getType();
            if (returnType != null) {
                // There maybe controllers that do not return a body. In that case the
                // return type will be null
                if (returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    System.out.println("bada 2");
                } else if (!
                        (returnType.toString().equals("void") || returnType.toString().equals("CompletableFuture"))) {
                    Type respType = new ClassOrInterfaceType(null, returnType.asClassOrInterfaceType().getNameAsString());
                    if (respType.toString().equals("String")) {
                        body.addStatement("String resp = response.getBody().asString();");
                        if(resp.getResponse() != null) {
                            body.addStatement(String.format("Assert.assertEquals(resp,\"%s\");", resp.getResponse().toString()));
                        }
                        else {
                            body.addStatement("Assert.assertNotNull(resp);");
                            logger.warn("Reponse body is empty for {}", md.getName());
                        }
                    } else {
                        System.out.println("bada 1");
                        // todo get thsi back on line
//                                VariableDeclarator variableDeclarator = new VariableDeclarator(respType, "resp");
//                                MethodCallExpr methodCallExpr = new MethodCallExpr(new NameExpr("response"), "as");
//                                methodCallExpr.addArgument(returnType.asClassOrInterfaceType().getNameAsString() + ".class");
//                                variableDeclarator.setInitializer(methodCallExpr);
//                                VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
//                                ExpressionStmt expressionStmt = new ExpressionStmt(variableDeclarationExpr);
//                                body.addStatement(expressionStmt);
                    }
                }
            }
        }

        private void prepareRequest(MethodCallExpr makePost, ControllerRequest request, BlockStmt body) {
            makePost.addArgument(new NameExpr("headers"));
            makePost.addArgument(new StringLiteralExpr(request.getPath()));
            if(!request.getQueryParameters().isEmpty()) {
                body.addStatement("Map<String, String> queryParams = new HashMap<>();");
                for(Map.Entry<String, String> entry : request.getQueryParameters().entrySet()) {
                    body.addStatement(String.format("queryParams.put(\"%s\", \"%s\");", entry.getKey(), entry.getValue()));
                }
                makePost.addArgument(new NameExpr("queryParams"));
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
        MethodCallExpr getStatusCodeCall = new MethodCallExpr(new NameExpr("response"), "getStatusCode");

        MethodCallExpr assertTrueCall = new MethodCallExpr(new NameExpr("Assert"), "assertEquals");
        assertTrueCall.addArgument(new IntegerLiteralExpr(statusCode));
        assertTrueCall.addArgument(getStatusCodeCall);

        blockStmt.addStatement(new ExpressionStmt(assertTrueCall));
    }

    private void handleURIVariables(MethodDeclaration md, ControllerRequest request) {
        for(var param : md.getParameters()) {
            String paramString = String.valueOf(param);

            String paramName = getParamName(param);
            if (paramString.startsWith("@RequestParam")) {
                if (!request.getQueryParameters().containsKey(paramName)) {
                    request.addQueryParameter(paramName, switch (param.getTypeAsString()) {
                        case "Boolean" -> "1";
                        case "float", "Float", "double", "Double" -> "1";
                        case "Integer", "int", "Long" -> "1";
                        case "String" -> "Ibuprofen";
                        default -> "0";
                    });
                }
            } else if (paramString.startsWith("@PathVariable")) {
                final String target = '{' + paramName + '}';

                String path = switch (param.getTypeAsString()) {
                    case "Boolean" -> request.getPath().replace(target, "false");
                    case "float", "Float", "double", "Double" -> request.getPath().replace(target, "1.0");
                    case "Integer", "int", "Long" -> request.getPath().replace(target, "1");
                    case "String" -> request.getPath().replace(target, "Ibuprofen");
                    default -> request.getPath().replace(target, "0");
                };
                request.setPath(path);
            }
        }
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

        for(var param : md.getParameters()) {
            if(param.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("RequestBody"))) {
                return param;
            }
        }
        return null;
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
}


