package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestControllerParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);
    private final File controllers;
    private CompilationUnit cu;

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

    protected Map<String, Type> getFields(CompilationUnit cu) {
        Map<String, Type> fields = new HashMap<>();
        for (var type : cu.getTypes()) {
            for (var member : type.getMembers()) {
                if (member.isFieldDeclaration()) {
                    FieldDeclaration field = member.asFieldDeclaration();
                    for (var variable : field.getVariables()) {
                        fields.put(variable.getNameAsString(), field.getElementType());
                    }
                }
            }
        }
        return fields;
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

        cu.accept(new MethodVisitor(), null);

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
     *
     * @param blockStmt
     * @return
     */
    private Type findReturnType(BlockStmt blockStmt) {
        Map<String, Type> variables = new HashMap<>();
        Map<String, Type> fields = getFields(cu);

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
     * We use it to identify the return types and the arguments of each method in the class.
     */
    private class MethodVisitor extends VoidVisitorAdapter<Void> {

        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);

            if (md.isPublic()) {
                if(md.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals("ExceptionHandler"))) {
                    return;
                }
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
                    if(returnType.toString().equals("ResponseEntity")) {
                        BlockStmt blockStmt = md.getBody().orElseThrow(() -> new IllegalStateException("Method body not found"));
                        returnType = findReturnType(blockStmt);
                    }
                }

                if (returnType != null) {
                    extractComplexType(returnType, cu);
                }
                for(var param : md.getParameters()) {
                    parameterSet.put(param.getName().toString(), "");
                    extractComplexType(param.getType(), cu);
                }

                createTests(md, returnType);
            }
        }

        private void createTests(MethodDeclaration md, Type returnType) {
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

        private void buildDeleteMethodTests(MethodDeclaration md, AnnotationExpr annotation, Type returnType) {
            httpWithoutBody(md, annotation, "makeDelete");
        }

        private void buildPutMethodTests(MethodDeclaration md, AnnotationExpr annotation, Type returnType) {
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
                paramNames.append(param.getNameAsString().substring(0, 1).toUpperCase())
                        .append(param.getNameAsString().substring(1));
            }

            String testName = String.valueOf(md.getName());
            if (md.getParameters().isNonEmpty()) {
                testName += "By" + paramNames + "Test";
            } else {
                testName += "Test";
            }
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

        private void buildGetMethodTests(MethodDeclaration md, AnnotationExpr annotation, Type returnType) {
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

        private void buildPostMethodTests(MethodDeclaration md, AnnotationExpr annotation, Type returnType) {
            httpWithBody(md, annotation, returnType, "makePost");
        }

        private void httpWithBody(MethodDeclaration md, AnnotationExpr annotation, Type returnType, String call) {
            MethodDeclaration testMethod = buildTestMethod(md);
            MethodCallExpr makeGetCall = new MethodCallExpr(call);


            if( md.getParameters().isNonEmpty()) {
                Parameter requestBody = findRequestBody(md);
                String path = handlePathVariables(md, getPath(annotation).replace("\"", ""));
                String paramClassName = requestBody.getTypeAsString();

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
                            testMethod.getBody().get().addStatement(new VariableDeclarationExpr(variableDeclarator));

                            break;
                        }

                        case "MultipartFile": {
                            dependencies.add("org.springframework.mock.web.MockMultipartFile");
//                            prepareBody("org.springframework.web.multipart.MultipartFile", new ClassOrInterfaceType(null, "MultipartFile"), "new MockMultipartFile", testMethod);
                            dependencies.add("org.springframework.web.multipart.MultipartFile");
                            ClassOrInterfaceType multipartFile = new ClassOrInterfaceType(null, "MultipartFile");
                            VariableDeclarator variableDeclarator = new VariableDeclarator(multipartFile, "req");
                            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(
                                    null,
                                    new ClassOrInterfaceType(null, "MockMultipartFile"),
                                    NodeList.nodeList(
                                            new StringLiteralExpr("file"),
                                            new StringLiteralExpr("test.csv"),
                                            new StringLiteralExpr("text/csv"),
                                            new MethodCallExpr(new NameExpr("\"some,csv,data\""), "getBytes")
                                    )
                            );
                            variableDeclarator.setInitializer(objectCreationExpr);
                            testMethod.getBody().get().addStatement(new VariableDeclarationExpr(variableDeclarator));
                            break;
                        }

                        case "Object": {
                            // SOme methods incorrectly have their DTO listed as of type Object. We will treat
                            // as a String
                            ClassOrInterfaceType str = new ClassOrInterfaceType(null, "String");
                            VariableDeclarator variableDeclarator = new VariableDeclarator(str, "req");
                            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, str, new NodeList<>());
                            variableDeclarator.setInitializer(objectCreationExpr);
                            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
                            testMethod.getBody().get().addStatement(variableDeclarationExpr);
                            break;
                        }

                        default:
                            ClassOrInterfaceType csiGridDtoType = new ClassOrInterfaceType(null, paramClassName);
                            VariableDeclarator variableDeclarator = new VariableDeclarator(csiGridDtoType, "req");
                            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, csiGridDtoType, new NodeList<>());
                            variableDeclarator.setInitializer(objectCreationExpr);
                            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr(variableDeclarator);
                            testMethod.getBody().get().addStatement(variableDeclarationExpr);
                    }

                    if (cdecl.getNameAsString().equals("MultipartFile")){
                        makeGetCall.addArgument(new NameExpr("req"));
                        testMethod.addThrownException(new ClassOrInterfaceType(null, "IOException"));
                    }
                    else {
                        MethodCallExpr writeValueAsStringCall = new MethodCallExpr(new NameExpr("objectMapper"), "writeValueAsString");
                        writeValueAsStringCall.addArgument(new NameExpr("req"));
                        makeGetCall.addArgument(writeValueAsStringCall);
                        testMethod.addThrownException(new ClassOrInterfaceType(null, "JsonProcessingException"));
                    }
                    makeGetCall.addArgument(new NameExpr("headers"));

                }
                makeGetCall.addArgument(new StringLiteralExpr(path));


                gen.getType(0).addMember(testMethod);

                VariableDeclarationExpr responseVar = new VariableDeclarationExpr(new ClassOrInterfaceType(null, "Response"), "response");
                AssignExpr assignExpr = new AssignExpr(responseVar, makeGetCall, AssignExpr.Operator.ASSIGN);
                testMethod.getBody().get().addStatement(new ExpressionStmt(assignExpr));

                addCheckStatus(testMethod);

                if(returnType != null) {
                    if(returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    } else if(!returnType.toString().equals("void")){
                        if(returnType.toString().equals(md.getParameter(0).getTypeAsString())) {

                        }
                        else {

                        }
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
}
