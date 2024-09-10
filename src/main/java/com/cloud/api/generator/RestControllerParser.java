package com.cloud.api.generator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;

import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestControllerParser extends ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RestControllerParser.class);
    private final JavaParser javaParser;
    private final JavaSymbolSolver symbolResolver;

    private final File controllers;
    StringBuilder generatedCode = new StringBuilder();
    private CompilationUnit cu;

    /**
     * Creates a new RestControllerParser
     *
     * @param controllers either a folder containing many controllers or a single controller
     */
    public RestControllerParser(File controllers) throws IOException {
        loadConfigMap();
        this.controllers = controllers;
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(basePath));
        symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        this.javaParser = new JavaParser(parserConfiguration);
    }

    public void start() throws IOException {
        processRestController(controllers);
    }

    private void processRestController(File path) throws IOException {
        System.out.println(path);
        if (path.isDirectory()) {
            int i = 0;
            for (File f : path.listFiles()) {
                new RestControllerParser(f).start();
                i++;
            }
            logger.info("Processed {} controllers", i);
        } else {
            FileInputStream in = new FileInputStream(path);
            cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
            if (cu.getPackageDeclaration().isPresent()) {
                processRestController(cu.getPackageDeclaration().get());
            }
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

        removeUnwantedImports(cu.getImports());
        expandWildCards(cu);

        fileContent.append("package " + pd.getName() + ";").append("\n");
        fileContent.append("\n").append("\n");
        fileContent.append("import com.cloud.api.base.TestHelper;").append("\n");

        fileContent.append("import org.testng.annotations.Test;").append("\n");
        fileContent.append("import org.testng.Assert;\n").append("\n");

        fileContent.append("import com.fasterxml.jackson.core.JsonProcessingException;").append("\n");
        fileContent.append("import io.restassured.http.Method;").append("\n");
        fileContent.append("import io.restassured.response.Response;").append("\n");
        fileContent.append("import com.cloud.core.annotations.TestCaseType;").append("\n");
        fileContent.append("import com.cloud.core.enums.TestType;").append("\n");

        cu.accept(new MethodVisitor(), null);

        //removeUnusedImports(cu.getImports());

        for (String s : dependencies) {
            fileContent.append("import " + s + ";").append("\n");
        }

        fileContent.append("\n").append("\n");
        fileContent.append("public class " + cu.getTypes().get(0).getName() + "Test extends TestHelper {").append("\n");

        fileContent.append(generatedCode).append("\n");

        fileContent.append("}").append("\n");

        ProjectGenerator.getInstance().writeFilesToTest(pd.getName().asString(), cu.getTypes().get(0).getName() + "Test.java",fileContent.toString());

        for(String dependency : dependencies) {
            copyDependencies(dependency);
        }
        dependencies.clear();
    }


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
                try {
                    if (returnType != null) {
                        extractComplexType(returnType, cu);
                    }
                    for(var param : md.getParameters()) {
                        extractComplexType(param.getType(), cu);
                    }
                } catch (IOException e) {
                    throw new GeneratorException("Error extracting type", e);
                }

                createTests(md, returnType);
            }
        }

        private void createTests(MethodDeclaration md, Type returnType) {
            for (AnnotationExpr annotation : md.getAnnotations()) {
                if (annotation.getNameAsString().equals("GetMapping") ) {
                    buildGetMethodTests(md, annotation, returnType);
                }
                if(annotation.getNameAsString().equals("PostMapping")) {
                    buildPostMethodTests(md, annotation, returnType);
                }
            }
        }

        private void buildGetMethodTests(MethodDeclaration md, AnnotationExpr annotation, Type returnType) {
            if(md.getParameters().isEmpty()) {
                generatedCode.append("""
                        \n\t@TestCaseType(types = {TestType.BVT, TestType.REGRESSION})
                        \t@Test
                        \tpublic void %sTest() {
                        \t\tResponse response = makeGet(headers, "%s");
                        \t\t// Assert that the response status code is in the 2xx range, indicating a successful response (e.g., 200, 201)
                        \t\tsoftAssert.assertTrue(String.valueOf(response.getStatusCode()).startsWith("2"),\s
                                             "Expected status code starting with 2xx, but got: " + response.getStatusCode());
                        \t\tsoftAssert.assertAll();
                        \t}\n
                        """.formatted(md.getName(),
                        getPath(annotation).replace("\"", "")));
            }
            else {
                String path = getPath(annotation).replace("\"", "");
                for(var param : md.getParameters()) {
                    switch(param.getTypeAsString()) {
                        case "Integer":
                        case "int":
                            path = path.replace('{' + param.getNameAsString() +'}', "1");
                            break;
                        case "Long":
                            path = path.replace('{' + param.getNameAsString() +'}', "1L");
                            break;
                        case "String":
                            path = path.replace('{' + param.getNameAsString() +'}', "Ibuprofen");
                    }
                }

                Parameter param = md.getParameter(0);
                String className = param.getTypeAsString();
                generatedCode.append("""
                        \n\t@TestCaseType(types = {TestType.BVT, TestType.REGRESSION})
                        \t@Test
                        \tpublic void %sTest() {
                        \t\tResponse response = makeGet(headers, "%s");
                        \t\t// Assert that the response status code is in the 2xx range, indicating a successful response (e.g., 200, 201)
                        \t\tsoftAssert.assertTrue(String.valueOf(response.getStatusCode()).startsWith("2"),\s
                                             "Expected status code starting with 2xx, but got: " + response.getStatusCode());
                        \t\tsoftAssert.assertAll();
                        \t}\n
                        """.formatted(md.getName(), path));
            }
        }

        private void buildPostMethodTests(MethodDeclaration md, AnnotationExpr annotation, Type returnType) {
            if(md.getParameters().isEmpty()) {

            }
            else {
                Parameter param = md.getParameter(0);
                String paramClassName = param.getTypeAsString();
                String body = "objectMapper.writeValueAsString(%s)";
                var assignment = "%s %s = new %s();".formatted(paramClassName, classToInstanceName(paramClassName), paramClassName);

                if(param.getType().isClassOrInterfaceType()) {
                    var cdecl = param.getType().asClassOrInterfaceType();
                    switch(cdecl.getNameAsString()) {
                        case "List":
                            assignment = "%s list = List.of();".formatted(paramClassName);
                            body = body.formatted("list");
                            break;
                        case "Map":
                            assignment = "%s map = Map.of();".formatted(paramClassName);
                            body = body.formatted("map");
                            break;
                        default:
                            body = body.formatted(classToInstanceName(paramClassName));
                    }
                }

                generatedCode.append("""
                        \n\t@TestCaseType(types = {TestType.BVT, TestType.REGRESSION})
                        \t@Test
                        \tpublic void %sTest() throws JsonProcessingException {
                        \t\t%s
                        \t\tResponse response = makePost(%s, headers, \n\t\t\t"%s");
                        """.formatted(md.getName(),
                        assignment,
                        body, getPath(annotation).replace("\"", "")));

                if(returnType != null) {
                    if(returnType.isClassOrInterfaceType() && returnType.asClassOrInterfaceType().getTypeArguments().isPresent()) {
                    } else {
                        if(returnType.toString().equals(paramClassName)) {
                            // special case. The return type and the data that are posted to the server happen to be the same.
                            generatedCode.append("""
                                \t\t%s res%s = response.as(%s.class);
                                """.formatted(returnType.asString(), returnType.toString(), returnType.asString()));
                        }
                        else {
                            generatedCode.append("""
                                    \t\t%s %sValue = response.as(%s.class);
                                    """.formatted(returnType.asString(), classToInstanceName(returnType.asString()), returnType.asString()));
                        }
                    }
                }
                generatedCode.append("""
                        \t\t// Assert that the response status code is in the 2xx range, indicating a successful response (e.g., 200, 201)
                        \t\tsoftAssert.assertTrue(String.valueOf(response.getStatusCode()).startsWith("2"),\s
                        "Expected status code starting with 2xx, but got: " + response.getStatusCode());
                        \t\tsoftAssert.assertAll(); 
                        """);
                generatedCode.append("\t}\n\n");
            }
        }
    }

    private String getPath(AnnotationExpr annotation) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return getCommonPath() + annotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
        } else if (annotation.isNormalAnnotationExpr()) {
            NormalAnnotationExpr normalAnnotation = annotation.asNormalAnnotationExpr();
            for (var pair : normalAnnotation.getPairs()) {
                if (pair.getNameAsString().equals("path")) {
                    return getCommonPath() + pair.getValue().toString();
                }
            }
        }
        return "";
    }

    private String getCommonPath() {
        for (var classAnnotation : cu.getTypes().get(0).getAnnotations()) {
            if (classAnnotation.getName().asString().equals("RequestMapping")) {
                if (classAnnotation.isNormalAnnotationExpr()) {
                    return classAnnotation.asNormalAnnotationExpr().getPairs().get(0).getValue().toString();
                } else {
                    return classAnnotation.asSingleMemberAnnotationExpr().getMemberValue().toString();
                }
            }
        }
        return null;
    }
}
