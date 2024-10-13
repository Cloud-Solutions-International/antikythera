package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.ProjectGenerator;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recursively copy DTOs from the Application Under Test (AUT).
 */
public class DTOHandler extends ClassProcessor {
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
        if (relativePath.contains("SearchParams")){
            return;
        }
        parse(relativePath);

        copyDependencies();
    }

    /**
     * Apply java parser on the class at the given path
     * @param relativePath a relative path to a java file
     * @throws IOException if the file could not be read.
     */
    public void parse(String relativePath) throws IOException {
        compile(relativePath);
        if (cu != null) {
            CompilationUnit tmp = cu;
            cu = cu.clone();

            expandWildCards(cu);

            allImports.addAll(cu.getImports());
            cu.setImports(new NodeList<>());

            for (var t : cu.getTypes()) {
                if (t.isClassOrInterfaceDeclaration()) {
                    ClassOrInterfaceDeclaration cdecl = t.asClassOrInterfaceDeclaration();
                    if (!cdecl.isInnerClass()) {
                        /*
                         * we are iterating through all the types defined in the source file through the for loop
                         * above. At this point we create a visitor and identifying the various dependencies will
                         * be the job of the visitor.
                         * Because the visitor will not look at parent classes so we do that below.
                         */
                        cdecl.accept(new TypeCollector(), null);
                    }
                    for(var ext : cdecl.getExtendedTypes()) {
                        ClassOrInterfaceType parent = ext.asClassOrInterfaceType();
                        ImportDeclaration imp = resolveImport(parent.getNameAsString());
                        addEdgeFromImport(t, ext, imp);
                    }
                }
            }

            if (!AntikytheraRunTime.isInterface( AbstractCompiler.pathToClass(relativePath))) {
                removeUnwanted();
            }

            for (ImportDeclaration imp : keepImports) {
                cu.addImport(imp);
            }

            if (method != null) {
                var variable = classToInstanceName(getPublicClass(cu));
                method.getBody().get().addStatement(new ReturnStmt(new NameExpr(variable)));
            }


            String className = AbstractCompiler.pathToClass(relativePath);
            if (! (AntikytheraRunTime.isServiceClass(className) || AntikytheraRunTime.isInterface(className)
                    || AntikytheraRunTime.isControllerClass(className)
                    || AntikytheraRunTime.isComponentClass(className) || AbstractCompiler.shouldSkip(className))) {
                ProjectGenerator.getInstance().writeFile(relativePath, cu.toString());
            }
            /*
             * We roll back the changes that we made to the compilation unit here. But theres' one modification
             * that we do keep. And that is the resolved imports. It makes things easier for the evaluator
             */
            tmp.setImports(cu.getImports());
            cu = tmp;
        }
    }

    /**
     * Iterates through all the classes in the compilation unit and processes them.
     */
    void removeUnwanted() {
        cu.getTypes().forEach(typeDeclaration -> {
            if(typeDeclaration.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration classDecl = typeDeclaration.asClassOrInterfaceDeclaration();

                // Remove all annotations except lombok.
                cleanUpAnnotations(classDecl);
                // Remove all the constructors for now, we may have to add them back later.
                classDecl.getConstructors().forEach(Node::remove);
                // Remove all methods that are not getters or setters. These are DTOs they
                // should not have any logic.
                cleanUpMethods(classDecl);

                // resolve the parent class
                for (var parent : classDecl.getExtendedTypes()) {

                    String className = cu.getPackageDeclaration().get().getNameAsString() + "." + parent.getNameAsString();
                    if (className.startsWith("java")) {
                        continue;
                    }
                    Dependency dependency = new Dependency(typeDeclaration, className);
                    dependency.setExtension(true);
                    addEdge(className, dependency);
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

    private void cleanUpMethods(ClassOrInterfaceDeclaration classDecl) {
        Set<MethodDeclaration> keep = new HashSet<>();
        for(MethodDeclaration md : classDecl.getMethods()) {
            if (md.getNameAsString().startsWith("get") || md.getNameAsString().startsWith("set")) {
                keep.add(md);
            }
        }
        // for some reason clear throws an exception
        classDecl.getMethods().forEach(Node::remove);
        for(MethodDeclaration md : keep) {
            classDecl.addMember(md);
        }

    }

    /**
     * Cleans up all the annotations in the clas.
     * The only annotations that we will preserve are four annotations from lombok
     * @param classDecl the class which we are going to clean up.
     */
    void cleanUpAnnotations(ClassOrInterfaceDeclaration classDecl) {
        NodeList<AnnotationExpr> annotations = classDecl.getAnnotations();
        Set<AnnotationExpr> preserve = new HashSet<>();
        for (AnnotationExpr annotation : annotations) {
            String annotationName = annotation.getNameAsString();
            if (annotationName.equals(STR_GETTER) || annotationName.equals("Setter") || annotationName.equals("NoArgsConstructor") || annotationName.equals("AllArgsConstructor")) {
                preserve.add(annotation);
                ImportDeclaration importDeclaration = new ImportDeclaration("lombok." + annotationName, false, false);
                cu.addImport(importDeclaration);
            }
        }
        annotations.clear();
        annotations.addAll(preserve);
    }

    /**
     * Listens for events of field declarations.
     * All annotations associated with the field are removed. Then we try to extract
     * its type. If the type is defined in the application under test we copy it.
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
                    resolveImport(annotationName);
                    filteredAnnotations.add(annotation);
                }
                else if(annotationName.equals("Id") || annotationName.equals("NotNull")) {
                    switch(field.getElementType().asString()) {
                        case "Long": field.setAllTypes(new PrimitiveType(PrimitiveType.Primitive.LONG));
                        break;
                    }
                }
            }
            field.getAnnotations().clear();
            field.setAnnotations(filteredAnnotations);

            Optional<ClassOrInterfaceDeclaration> ancestor = field.findAncestor(ClassOrInterfaceDeclaration.class);

            if (ancestor.isPresent()) {
                /*
                 * We need not have this ancestor check because java can't have global variables but that's the
                 * way the library is structured.
                 * Having identified that we have a field, we need to solve it's type dependencies. Matters are
                 * slightly complicated by the fact that sometimes a field may have an initializer. This may be
                 * a method call (which we will ignore for now) but this is more often simply an object creation
                 * It may look like this:
                 *
                 *     List<String> list = new ArrayList<>();
                 *
                 * Because the field type and the initializer differ we need to solve the type dependencies for
                 * the initializer as well.
                 */
                solveTypeDependencies(ancestor.get(), field.getElementType());
                VariableDeclarator firstVariable = field.getVariables().get(0);
                firstVariable.getInitializer().ifPresent(init -> {
                    if (init.isObjectCreationExpr()) {
                        solveTypeDependencies(ancestor.get(), init.asObjectCreationExpr().getType());
                    }
                });


                // handle custom getters and setters

                String fieldName = firstVariable.getNameAsString();
                String className = getPublicClass(cu).getNameAsString();
                Map<String, String> methodNames = Settings.loadCustomMethodNames(className, fieldName);

                if(!methodNames.isEmpty()){
                    String getterName = methodNames.getOrDefault("getter", "get" + capitalize(fieldName));
                    String setterName = methodNames.getOrDefault("setter", "set" + capitalize(fieldName));

                    // Use custom getter and setter names
                    generateGetter(field, getterName);
                    generateSetter(field, setterName);

                }
            }
            return super.visit(field, args);
        }

        void generateGetter(FieldDeclaration field, String getterName) {
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

        void generateSetter(FieldDeclaration field, String setterName) {
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

        @Override
        public Visitable visit(MethodDeclaration method, Void args) {
            super.visit(method, args);
            method.getAnnotations().clear();
            solveTypeDependencies(method.findAncestor(ClassOrInterfaceDeclaration.class).orElseGet(null), method.getType());
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
        TypeDeclaration<?> cdecl = AbstractCompiler.getPublicClass(cu);

        if (!field.isStatic()) {
            boolean isArray = field.getElementType().getParentNode().toString().contains("[]");
            String instance = classToInstanceName(cdecl);
            String fieldName = field.getVariables().get(0).getNameAsString();
            MethodCallExpr setter = new MethodCallExpr(new NameExpr(instance), "set" + capitalize(fieldName));
            String type = field.getElementType().isClassOrInterfaceType()
                    ? field.getElementType().asClassOrInterfaceType().getNameAsString()
                    : field.getElementType().asString();

            String className = AbstractCompiler.getPublicClass(cu).getNameAsString();
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
            logger.error("Usage: java DTOHandler <relative-path> | <class-name>");
        }
        else {
            DTOHandler processor = new DTOHandler();
            if (args[0].endsWith(".java")) {
                processor.copyDTO(args[0]);
            }
            else {
                processor.copyDTO(processor.classToPath(args[0]));
            }
        }
    }

    public void setCompilationUnit(CompilationUnit cu) {
        this.cu = cu;
    }
}
