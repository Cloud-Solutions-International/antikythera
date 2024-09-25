package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;


import java.util.Map;
import java.util.Optional;

/**
 * Recursively copy DTOs from the Application Under Test (AUT).
 *
 */
public class DTOHandler extends  ClassProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DTOHandler.class);
    public static final String STR_GETTER = "Getter";


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
        parseDTO(relativePath);

        ProjectGenerator.getInstance().writeFile(relativePath, cu.toString());

        for(String dependency : dependencies) {
            copyDependencies(dependency);
        }
        dependencies.clear();
    }

    public void parseDTO(String relativePath) throws FileNotFoundException {
        compile(relativePath);
        expandWildCards(cu);
        solveTypes();
        createFactory();

        cu.accept(new TypeCollector(), null);

        handleStaticImports(cu.getImports());
        removeUnusedImports(cu.getImports());

        if (method != null) {
            var variable = classToInstanceName(cu.getTypes().get(0));
            method.getBody().get().addStatement(new ReturnStmt(new NameExpr(variable)));
        }
    }

    /**
     * Create a factory method for the DTO being processed.
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
                    if(findImport(cu, parent.getName().asString()) || parent.resolve().describe().startsWith("java.lang")) {
                        return;
                    }
                    String className = cu.getPackageDeclaration().get().getNameAsString() + "." + parent.getNameAsString();
                    dependencies.add(className);
                }
                // we don't want interfaces
                classDecl.getImplementedTypes().clear();
            }
            else if(typeDeclaration.isEnumDeclaration()) {
                typeDeclaration.getMethods().forEach(Node::remove);
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


            // Filter annotations to retain only @JsonFormat and @JsonIgnore
            NodeList<AnnotationExpr> filteredAnnotations = new NodeList<>();
            for (AnnotationExpr annotation : field.getAnnotations()) {
                String annotationName = annotation.getNameAsString();
                if (annotationName.equals("JsonFormat") || annotationName.equals("JsonIgnore")) {
                    filteredAnnotations.add(annotation);
                }
            }
            field.getAnnotations().clear();
            field.setAnnotations(filteredAnnotations);

            extractEnums(field);
            solveTypeDependencies(field.getElementType(), cu);

            // handle custom getters and setters
            String fieldName = field.getVariables().get(0).getNameAsString();
            String className = cu.getTypes().get(0).getNameAsString();
            Map<String, String> methodNames = Settings.loadCustomMethodNames(className, fieldName);

            if(!methodNames.isEmpty()){
                String getterName = methodNames.getOrDefault("getter", "get" + capitalize(fieldName));
                String setterName = methodNames.getOrDefault("setter", "set" + capitalize(fieldName));

                // Use custom getter and setter names
                generateGetter(field, getterName);
                generateSetter(field, setterName);

            }


            return super.visit(field, args);
        }

        private void generateGetter(FieldDeclaration field, String getterName) {
            // Create a new MethodDeclaration for the getter
            MethodDeclaration getter = new MethodDeclaration();
            getter.setName(getterName);
            getter.setType(field.getElementType());
            getter.setModifiers(Modifier.Keyword.PUBLIC);

            // Create a ReturnStmt that returns the field's value
            String fieldName = field.getVariables().get(0).getNameAsString();
            ReturnStmt returnStmt = new ReturnStmt(new NameExpr(fieldName));

            // Add the ReturnStmt to the method body
            BlockStmt body = new BlockStmt();
            body.addStatement(returnStmt);
            getter.setBody(body);

            // Add the getter method to the class
            ((ClassOrInterfaceDeclaration) field.getParentNode().get()).addMember(getter);
        }

        private void generateSetter(FieldDeclaration field, String setterName) {
            // Create a new MethodDeclaration for the setter
            MethodDeclaration setter = new MethodDeclaration();
            setter.setName(setterName);
            setter.setType(new VoidType());
            setter.setModifiers(Modifier.Keyword.PUBLIC);

            // Add a parameter to the method with the field's type and name
            String fieldName = field.getVariables().get(0).getNameAsString();
            Parameter param = new Parameter(field.getElementType(), fieldName);
            setter.addParameter(param);

            // Create an AssignExpr that assigns the parameter value to the field
            AssignExpr assignExpr = new AssignExpr(
                    new NameExpr("this." + fieldName),
                    new NameExpr(fieldName),
                    AssignExpr.Operator.ASSIGN
            );

            // Add the AssignExpr to the method body
            BlockStmt body = new BlockStmt();
            body.addStatement(assignExpr);
            setter.setBody(body);

            // Add the setter method to the class
            ((ClassOrInterfaceDeclaration) field.getParentNode().get()).addMember(setter);
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
                else if (initializer.isNameExpr()) {
                    findImport(cu, initializer.asNameExpr().toString());
                }
            }
            else {
                if(method != null) {
                    MethodCallExpr setter = generateRandomValue(field, cu);
                    if (setter != null) {
                        method.getBody().get().addStatement(setter);
                    }
                }
            }
        }

        @Override
        public Visitable visit(MethodDeclaration method, Void args) {
            super.visit(method, args);
            method.getAnnotations().clear();
            solveTypeDependencies(method.getType(), cu);
            return method;
        }
    }

    static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Generate random values for use in the factory method for a DTO
     * @param field
     */
    public static MethodCallExpr generateRandomValue(FieldDeclaration field, CompilationUnit cu) {
        TypeDeclaration<?> cdecl = cu.getTypes().get(0);

        if (!field.isStatic()) {
            boolean isArray = field.getElementType().getParentNode().toString().contains("[]");
            String instance = classToInstanceName(cdecl);
            String fieldName = field.getVariables().get(0).getNameAsString();
            MethodCallExpr setter = new MethodCallExpr(new NameExpr(instance), "set" + capitalize(fieldName));
            String type = field.getElementType().isClassOrInterfaceType()
                    ? field.getElementType().asClassOrInterfaceType().getNameAsString()
                    : field.getElementType().asString();

            String className = cu.getTypes().get(0).getNameAsString();
            Map<String, String> methodNames = Settings.loadCustomMethodNames(className, fieldName);

            String argument = switch (type) {
                case "boolean" -> {
                    if(!methodNames.isEmpty()) {
                        String setterName = methodNames.get("setter");
                        setter = new MethodCallExpr(new NameExpr(instance), setterName);
                    }
                    if (fieldName.startsWith("is") && methodNames.isEmpty()) {
                        setter = new MethodCallExpr(new NameExpr(instance), "set" + capitalize(fieldName.replaceFirst("is", "")));
                    }
                    yield isArray ? "new boolean[] {true, false}" : "true";
                }
                case "Boolean" -> isArray ? "new Boolean[] {true, false}" : "true";
                case "Character" -> isArray ? "new Character[] {'A', 'B'}" : "'A'";
                case "Date" -> isArray
                        ? (field.getElementType().toString().contains(".")
                        ? "new java.util.Date[] {new java.util.Date(), new java.util.Date()}"
                        : "new Date[] {new Date(), new Date()}")
                        : (field.getElementType().toString().contains(".") ? "new java.util.Date()" : "new Date()");
                case "Double", "double" -> isArray ? "new Double[] {0.0, 1.0}" : "0.0";
                case "Float", "float" -> isArray ? "new Float[] {0.0f, 1.0f}" : "0.0f";
                case "Integer", "int" -> isArray ? "new Integer[] {0, 1}" : "0";
                case "Byte" -> isArray ? "new Byte[] {(byte) 0, (byte) 1}" : "(byte) 0";
                case "String" -> isArray ? "new String[] {\"Hello\", \"World\"}" : "\"Hello world\"";
                case "Long", "long" -> isArray ? "new Long[] {0L, 1L}" : "0L";
                case "UUID" -> isArray ? "new UUID[] {UUID.randomUUID(), UUID.randomUUID()}" : "UUID.randomUUID()";
                case "LocalDate" -> isArray ? "new LocalDate[] {LocalDate.now(), LocalDate.now()}" : "LocalDate.now()";
                case "LocalDateTime" -> isArray ? "new LocalDateTime[] {LocalDateTime.now(), LocalDateTime.now()}" : "LocalDateTime.now()";
                case "Short" -> isArray ? "new Short[] {(short) 0, (short) 1}" : "(short) 0";
                case "byte" -> isArray ? "new byte[] {(byte) 0, (byte) 1}" : "(byte) 0";
                case "List" -> "List.of()";
                case "Map" -> "Map.of()";
                case "Set" -> "Set.of()";
                case "T" -> "null";
                case "BigDecimal" -> isArray ? "new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ONE}" : "BigDecimal.ZERO";
                case "EnumSet", "Class" -> { yield null; }
                default -> {
                    if (!field.resolve().getType().asReferenceType().getTypeDeclaration().get().isEnum()) {
                        yield isArray ? "new " + type + "[] {}" : "new " + type + "()";
                    } else {
                        yield null;
                    }
                }
            };

            if (argument != null) {
                setter.addArgument(argument);
                return setter;
            }
        }
        return null;
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
