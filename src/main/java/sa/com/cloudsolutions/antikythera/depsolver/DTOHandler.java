package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.Statement;
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
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

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

            visitTypes();

            if (!AntikytheraRunTime.isInterface( AbstractCompiler.pathToClass(relativePath))) {
                removeUnwanted();
            }

            for (ImportDeclaration imp : keepImports) {
                cu.addImport(imp);
            }

            if (method != null) {
                var variable = classToInstanceName(getPublicType(cu));
                method.getBody().get().addStatement(new ReturnStmt(new NameExpr(variable)));
            }


            String className = AbstractCompiler.pathToClass(relativePath);
            if (! (AntikytheraRunTime.isServiceClass(className) || AntikytheraRunTime.isInterface(className)
                    || AntikytheraRunTime.isControllerClass(className)
                    || AntikytheraRunTime.isComponentClass(className) || AbstractCompiler.shouldSkip(className))) {
                CopyUtils.writeFile(relativePath, cu.toString());
            }

            /*
             * We roll back the changes that we made to the compilation unit here. But theres' one modification
             * that we do keep. And that is the resolved imports. It makes things easier for the evaluator.
             * However things like services and components are not being copied so we don't mind about import
             * pollution in those classes.
             */
            if (!AntikytheraRunTime.isComponentClass(className) && !AntikytheraRunTime.isServiceClass(className)) {
                tmp.setImports(cu.getImports());
                cu = tmp;
            }
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
                    ClassDependency dependency = new ClassDependency(typeDeclaration, className);
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

    /**
     * Cleans up methods in the class declaration.
     *    remove all methods that are not getters or setters.
     *    clear out the body if a getter or setter has more than one line and replace it with a single line
     *       that will either be an assignment or a return statement.
     *    get rid of getters that take any sort of arguments.
     *
     * @param classDecl a Class or Interface that to be cleaned up (but obviously we are only interested in classes)
     */
    private void cleanUpMethods(ClassOrInterfaceDeclaration classDecl) {
        Set<MethodDeclaration> keep = new HashSet<>();
        for(MethodDeclaration md : classDecl.getMethods()) {
            String methodName = md.getNameAsString();
            if (methodName.startsWith("get") || methodName.startsWith("set")) {
                keep.add(md);
                md.getBody().ifPresent(body -> processBadGettersSetters(classDecl, md, body, methodName, keep));
            }
        }
        // for some reason clear throws an exception
        classDecl.getMethods().forEach(Node::remove);
        for(MethodDeclaration md : keep) {
            classDecl.addMember(md);
        }

    }

    private void processBadGettersSetters(ClassOrInterfaceDeclaration classDecl, MethodDeclaration md, BlockStmt body, String methodName, Set<MethodDeclaration> keep) {
        NodeList<Statement> statements = body.getStatements();
        if(statements.size() > 1 || statements.get(0).isBlockStmt() || statements.get(0).isIfStmt()) {
            if (methodName.startsWith("get")) {
                if(md.getParameters().isEmpty()) {
                    body.getStatements().clear();
                    body.addStatement(new ReturnStmt(new NameExpr(methodName.replaceFirst("get", ""))));
                }
                else {
                    keep.remove(md);
                }
            }
            else if (methodName.startsWith("set")) {
                body.getStatements().clear();
                if (!md.getParameters().isEmpty()) {
                    String fieldName = classToInstanceName(methodName.replaceFirst("set", ""));
                    Optional<FieldDeclaration> fd = classDecl.getFieldByName(fieldName);
                    Parameter parameter = md.getParameters().get(0);
                    if (fd.isPresent() && fd.get().getElementType().equals(parameter.getType())) {
                        body.addStatement(new AssignExpr(new NameExpr("this." + fieldName),
                                new NameExpr(parameter.getNameAsString()), AssignExpr.Operator.ASSIGN));
                    }
                }
            }
        }
    }

    /**
     * Cleans up all the annotations in the class.
     * The only annotations that we will preserve are four annotations from lombok
     * @param classDecl the class which we are going to clean up.
     */
    void cleanUpAnnotations(ClassOrInterfaceDeclaration classDecl) {
        NodeList<AnnotationExpr> annotations = classDecl.getAnnotations();
        Set<AnnotationExpr> preserve = new HashSet<>();
        for (AnnotationExpr annotation : annotations) {
            String annotationName = annotation.getNameAsString();
            if (annotationName.equals(STR_GETTER) || annotationName.equals("Setter") || annotationName.equals("Data")
                    || annotationName.equals("NoArgsConstructor") || annotationName.equals("AllArgsConstructor")
                    || annotationName.equals("Transient")) {
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
            collectField(field);

            Optional<ClassOrInterfaceDeclaration> ancestor = field.findAncestor(ClassOrInterfaceDeclaration.class);

            if (ancestor.isPresent()) {
                collectFieldAncestors(field, ancestor.get());
                VariableDeclarator firstVariable = field.getVariables().get(0);

                // handle custom getters and setters
                String fieldName = firstVariable.getNameAsString();
                TypeDeclaration<?> cdecl = getPublicType(cu);
                String className = cdecl.getNameAsString();
                Map<String, String> methodNames = Settings.loadCustomMethodNames(className, fieldName);

                if(!methodNames.isEmpty()){
                    String getterName = methodNames.getOrDefault("getter", "get" + capitalize(fieldName));
                    String setterName = methodNames.getOrDefault("setter", findNameOfSetter(fieldName, cdecl));

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
        TypeDeclaration<?> cdecl = AbstractCompiler.getPublicType(cu);

        if (!field.isStatic() && cdecl != null ) {
            boolean isArray = field.getElementType().getParentNode().toString().contains("[]");
            String instance = classToInstanceName(cdecl);
            String fieldName = field.getVariables().get(0).getNameAsString();
            String sn = findNameOfSetter(fieldName, cdecl);
            MethodCallExpr setter = new MethodCallExpr(new NameExpr(instance), sn);
            String type = field.getElementType().isClassOrInterfaceType()
                    ? field.getElementType().asClassOrInterfaceType().getNameAsString()
                    : field.getElementType().asString();

            String className = cdecl.getFullyQualifiedName().get();
            Map<String, String> methodNames = Settings.loadCustomMethodNames(className, fieldName);

            String argument = switch (type) {
                case "boolean" -> {
                    if(!methodNames.isEmpty()) {
                        String setterName = methodNames.get("setter");
                        setter = new MethodCallExpr(new NameExpr(instance), setterName);
                    }
                    if (fieldName.startsWith("is") && methodNames.isEmpty()) {
                        setter = new MethodCallExpr(new NameExpr(instance),
                                findNameOfSetter(fieldName.replaceFirst("is", ""), cdecl));
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

    /**
     * Given the field name and the class declaration find the name of the setter.
     * People don't apparently always use java beans naming conventions. So we can't blindly use
     * the name of the field to find the setter. That's why we can't rely on lombok either.
     * @param fieldName the name of the field
     * @param cdecl the class declaration that is supposed to contain the setter
     * @return the setter name
     */
    private static String findNameOfSetter(String fieldName, TypeDeclaration<?> cdecl) {
        String name = "set" + fieldName;
        if (cdecl.getMethodsByName(name).isEmpty()) {
            name = "set" + capitalize(fieldName);
        }
        return name;
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
                processor.copyDTO(AbstractCompiler.classToPath(args[0]));
            }
        }
    }

    public void setCompilationUnit(CompilationUnit cu) {
        this.cu = cu;
    }

    @Override
    protected ModifierVisitor<?> createTypeCollector() {
        return new TypeCollector();
    }
}
