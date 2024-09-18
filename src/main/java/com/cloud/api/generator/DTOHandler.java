package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;

import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Optional;

/**
 * Recursively copy DTOs from the Application Under Test (AUT).
 *
 */
public class DTOHandler extends  ClassProcessor{
    private static final Logger logger = LoggerFactory.getLogger(DTOHandler.class);
    public static final String STR_GETTER = "Getter";

    private CompilationUnit cu;

    MethodDeclaration method = null;

    /**
     * Constructor to initialize the JavaParser and set things up
     */
    public DTOHandler() throws IOException {
       super();
    }

    /**
     * Copy the DTO from the AUT.
     * @param relativePath a path name relative to the base path of the application.
     * @throws IOException when the source code cannot be read
     */
    public void copyDTO(String relativePath) throws IOException{
        logger.info("\t{}", relativePath);
        Path sourcePath = Paths.get(basePath, relativePath);

        FileInputStream in = new FileInputStream(sourcePath.toFile());
        cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));

        if(resolved.contains(cu.toString())) {
            return ;
        }

        expandWildCards(cu);
        solveTypes();
        createFactory();

        cu.accept(new TypeCollector(), null);

        handleStaticImports(cu.getImports());

        removeUnusedImports(cu.getImports());

        if(method != null) {
            var variable = classToInstanceName(cu.getTypes().get(0));
            method.getBody().get().addStatement(new ReturnStmt(new NameExpr(variable)));
        }

        ProjectGenerator.getInstance().writeFile(relativePath, cu.toString());
        resolved.add(cu.toString());

        for(String dependency : dependencies) {
            copyDependencies(dependency);
        }
        dependencies.clear();
    }

    /**
     * Create a factory method for the DTO being processsed.
     * Does not return anything but the 'method' field will have a non null value.
     * the visitor can add a setter for each field that it encounters.
     */
    private void createFactory() {
        TypeDeclaration<?> cdecl = cu.getTypes().get(0);
        String className = cdecl.getNameAsString();

        if(cdecl.isClassOrInterfaceDeclaration() && !cdecl.asClassOrInterfaceDeclaration().isInterface()
                && !cdecl.asClassOrInterfaceDeclaration().isAbstract()
                && className.toLowerCase().endsWith("to")) {
            String variable = classToInstanceName(cdecl);

            method = new MethodDeclaration();
            method.setName("create" + className);
            method.setType(className);
            method.setModifiers(Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);
            cdecl.asClassOrInterfaceDeclaration().addMember(method);

            BlockStmt body = new BlockStmt();
            VariableDeclarationExpr varDecl = new VariableDeclarationExpr(new ClassOrInterfaceType(null, className), variable);
            ObjectCreationExpr newExpr = new ObjectCreationExpr(null, new ClassOrInterfaceType(null, className), new NodeList<>());
            body.addStatement(new AssignExpr(varDecl, newExpr, AssignExpr.Operator.ASSIGN));

            method.setBody(body);

        }
        else {
            method = null;
        }
    }

    void handleStaticImports(NodeList<ImportDeclaration> imports) {
        imports.stream().filter(importDeclaration -> importDeclaration.getNameAsString().startsWith(basePackage)).forEach(importDeclaration ->
        {
            if(importDeclaration.isStatic()) {
                String importName = importDeclaration.getNameAsString();
                if(importDeclaration.isAsterisk()) {
                    dependencies.add(importName);
                }
                else {
                    dependencies.add(importName.substring(0, importName.lastIndexOf(".")));
                }
            }
        });
    }


    /**
     * Iterates through all the classes in the file and processes them.
     * If we are inheriting from a class that's part of our AUT, we need to copy it.
     *
     */
    private void solveTypes() {
        cu.getTypes().forEach(typeDeclaration -> {
            if(typeDeclaration.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration classDecl = typeDeclaration.asClassOrInterfaceDeclaration();

                // remove all annotations. Later we will add some of them back.
                NodeList<AnnotationExpr> annotations = classDecl.getAnnotations();
                annotations.clear();
                addLombok(classDecl, annotations);
                // remove constructos and all methods. We are adding the @Getter and @Setter
                // annotations so no getters and setters are needed. Any other methods can
                // goto hell because they just shouldn't be here in the first place.
                classDecl.getConstructors().forEach(Node::remove);
                classDecl.getMethods().forEach(Node::remove);
                // resolve the parent class
                for (var parent : classDecl.getExtendedTypes()) {
                    if(findImport(cu, parent.getName().asString())) {
                        return;
                    }
                    String className = cu.getPackageDeclaration().get().getNameAsString() + "." + parent.getNameAsString();
                    dependencies.add(className);
                }
                // we don't want interfaces
                classDecl.getImplementedTypes().clear();
            }
            else if(typeDeclaration.isEnumDeclaration()) {
                for (var annotation : typeDeclaration.getAnnotations()) {
                    if(annotation.getNameAsString().equals("AllArgsConstructor")) {
                        ImportDeclaration importDeclaration = new ImportDeclaration("lombok.AllArgsConstructor", false, false);
                        cu.addImport(importDeclaration);
                    }
                    if(annotation.getNameAsString().equals(STR_GETTER)) {
                        ImportDeclaration importDeclaration = new ImportDeclaration("lombok.Getter", false, false);
                        cu.addImport(importDeclaration);
                    }
                }
            }
        });
    }

    /**
     * Add Lombok annotations to the class if it has any non static final fields
     * @param classDecl the class to which we are going to add lombok annotations
     * @param annotations the existing annotations (which will probably be empty)
     */
    void addLombok(ClassOrInterfaceDeclaration classDecl, NodeList<AnnotationExpr> annotations) {
        String[] annotationsToAdd;
        if (classDecl.getFields().size()<=255) {
            annotationsToAdd = new String[]{STR_GETTER, "NoArgsConstructor", "AllArgsConstructor", "Setter"};
        } else {
            annotationsToAdd = new String[]{STR_GETTER, "NoArgsConstructor", "Setter"};
        }

        // Find the CompilationUnit associated with the classDecl
        cu = classDecl.findCompilationUnit().orElseThrow(() -> new IllegalStateException("CompilationUnit not found"));

        if(classDecl.getFields().stream().filter(field -> !(field.isStatic() && field.isFinal())).anyMatch(field -> true)) {
            for (String annotation : annotationsToAdd) {
                ImportDeclaration importDeclaration = new ImportDeclaration("lombok." + annotation, false, false);
                cu.addImport(importDeclaration);
                NormalAnnotationExpr annotationExpr = new NormalAnnotationExpr();
                annotationExpr.setName(new Name(annotation));
                annotations.add(annotationExpr);
            }
        }
    }

    /**
     * Listens for events of field declarations.
     * All annotations associated with the field are removed. Then we try to extract
     * it's type. If the type is defined in the application under test we copy it.
     */
    class TypeCollector extends ModifierVisitor<Void> {

        @Override
        public Visitable visit(FieldDeclaration field, Void args) {


            String fieldAsString = field.getElementType().toString();
            if (fieldAsString.equals("DateScheduleUtil")
                    || fieldAsString.equals("Logger")
                    || fieldAsString.equals("Sort.Direction")) {
                return null;
            }
            field.getAnnotations().clear();
            extractEnums(field);
            extractComplexType(field.getElementType(), cu);

            return super.visit(field, args);
        }

        private void extractEnums(FieldDeclaration field) {
            Optional<Expression> expr = field.getVariables().get(0).getInitializer();
            if (expr.isPresent()) {
                var initializer = expr.get();
                if (initializer.isMethodCallExpr()) {
                    MethodCallExpr methodCall = (MethodCallExpr) initializer;
                    Optional<Expression> nameExpr = methodCall.getScope();
                    // Check if the scope of the method call is a field access expression
                    if (nameExpr.isPresent() && nameExpr.get().isFieldAccessExpr()) {
                        findImport(cu, nameExpr.get().asFieldAccessExpr().getScope().toString());
                    }
                }
                else if(initializer.isFieldAccessExpr()) {
                    findImport(cu, initializer.asFieldAccessExpr().getScope().toString());
                }
            }
            else {
                generateRandomValue(field);
            }
        }

        /**
         * Generate random values for use in the factory method for a DTO
         * @param field
         */
        private void generateRandomValue(FieldDeclaration field) {
            TypeDeclaration<?> cdecl = cu.getTypes().get(0);

            if(!field.isStatic() && method != null) {
                String instance = classToInstanceName(cdecl);

                String fieldName = field.getVariables().get(0).getNameAsString();
                MethodCallExpr setter = new MethodCallExpr(new NameExpr(instance), "set" + capitalize(fieldName));
                String type = field.getElementType().isClassOrInterfaceType()
                        ? field.getElementType().asClassOrInterfaceType().getNameAsString()
                        : field.getElementType().asString();

                switch (type) {
                    case "boolean":
                        if(fieldName.startsWith("is")) {
                            setter = new MethodCallExpr(new NameExpr(instance), "set" +
                                    capitalize(fieldName.replaceFirst("is","")));
                        }
                    case "Boolean":
                        setter.addArgument("true");
                        break;

                    case "Character":
                        setter.addArgument("'A'");
                        break;

                    case "Date":
                        if(field.getElementType().toString().contains(".")) {
                            setter.addArgument("new java.util.Date()");
                        } else {
                            setter.addArgument("new Date()");
                        }
                        break;

                    case "Double":
                    case "double":
                        setter.addArgument("0.0");
                        break;

                    case "Float":
                    case "float":
                        setter.addArgument("0.0f");
                        break;

                    case "Integer":
                    case "int":
                        setter.addArgument("0");
                        break;

                    case "byte":
                    case "Byte":
                        setter.addArgument("(byte) 0");
                        break;

                    case "List":
                        setter.addArgument("List.of()");
                        break;

                    case "long":
                    case "Long":
                        setter.addArgument("0L");
                        break;

                    case "String":
                        setter.addArgument("\"Hello world\"");
                        break;

                    case "Map":
                        setter.addArgument("Map.of()");
                        break;

                    case "Set":
                        setter.addArgument("Set.of()");
                        break;

                    case "UUID":
                        setter.addArgument("UUID.randomUUID()");
                        break;

                    default:
                        if(!field.resolve().getType().asReferenceType().getTypeDeclaration().get().isEnum()) {
                            setter.addArgument("new " + type + "()");
                        } else {
                            setter = null;
                        }
                }

                if(setter != null) {
                    method.getBody().get().addStatement(setter);
                }
            }
        }

        String capitalize(String s) {
            return Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }

        @Override
        public Visitable visit(MethodDeclaration method, Void args) {
            super.visit(method, args);
            method.getAnnotations().clear();
            extractComplexType(method.getType(), cu);
            return method;
        }
    }

    public static void main(String[] args) throws IOException{
        Settings.loadConfigMap();

        if (args.length != 1) {
            logger.error("Usage: java DTOHandler <base-path> <relative-path>");

        }
        else {
            DTOHandler processor = new DTOHandler();
            processor.copyDTO(args[0]);
        }
    }
}
