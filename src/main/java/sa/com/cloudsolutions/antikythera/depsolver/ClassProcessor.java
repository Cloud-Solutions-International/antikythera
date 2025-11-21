package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.model.SymbolReference;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserFieldDeclaration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Class processor will parse a class and track it's dependencies.
 *
 */
public class ClassProcessor extends AbstractCompiler {
    /*
     * The overall strategy:
     *   it is described here even though several different classes are involved.
     *
     *   We are only interested in copying the DTOs from the application under test. Broadly a DTO is a
     *   class is either a return type of a controller or an input to a controller.
     *
     *   A controller has a lot of other dependencies, most notably services and even though repositories
     *   are only supposed to be accessed through services sometimes you find them referred directly in
     *   the controller. These will not be copied across to the test folder.
     */

    /**
     * The logger
     */
    private static final Logger logger = LoggerFactory.getLogger(ClassProcessor.class);

    /**
     * Essentially dependencies are a graph.
     *
     * The key in this map is the fully qualified class. The values will be the other types it
     * refers to.
     */
    protected static final Map<String, Set<ClassDependency>> dependencies = new HashMap<>();

    static final Set<String> copied = new HashSet<>();
    public static final String PREFIX = "java.";

    /**
     * A collection of all imports encountered in a class.
     * This maybe a huge list because sometimes we find wild card imports.
     */
    protected final Set<ImportDeclaration> allImports = new HashSet<>();

    /**
     * This is a collection of imports that we want to preserve.
     *
     * Most classes contain a bunch of imports that are not used + there will be some that are
     * obsolete after we strip out the unwanted dependencies. Lastly we are trying to avoid
     * asterisk imports, so they are expanded and then the asterisk is removed.
     *
     */
    protected final Set<ImportDeclaration> keepImports = new HashSet<>();

    public ClassProcessor() throws IOException {
        super();
    }

    /**
     * Copy a dependency from the application under test.
     *
     * @param nameAsString a fully qualified class name
     */
    protected void copyDependency(String nameAsString, ClassDependency dependency) throws IOException {
        if (dependency.isExternal() || nameAsString.startsWith(PREFIX)
                || AntikytheraRunTime.isInterface(nameAsString)
                || nameAsString.startsWith("org.springframework")) {
            return;
        }
        /*
         * First thing is to find the compilation unit. Obviously we can only copy the DTO
         * if we have a compilation unit.
         */
        CompilationUnit depCu = getCompilationUnit(dependency.getTo());
        if (depCu == null) {
            return;
        }

        for (var decl : depCu.getTypes()) {
            if (decl.isClassOrInterfaceDeclaration() && decl.asClassOrInterfaceDeclaration().isInterface()) {
                continue;
            }
            String targetName = dependency.getTo();
            if (!copied.contains(targetName) && targetName.startsWith(Settings.getBasePackage())) {
                /*
                 * There maybe cyclic dependencies, specially if you have @Entity mappings. Therefor
                 * it's best to make sure that we haven't copied this file already and also to make
                 * sure that the class is directly part of the application under test.
                 */

                copyDependencyHelper(targetName);
            }
        }
    }

    private static void copyDependencyHelper(String targetName) throws IOException {
        try {
            copied.add(targetName);
            DTOHandler handler = new DTOHandler();
            handler.copyDTO(classToPath(targetName));

        } catch (FileNotFoundException fe) {
            if ("log".equals(Settings.getProperty("dependencies.on_error"))) {
                logger.warn("Could not find {} for copying", targetName);
            } else {
                throw fe;
            }
        }
    }

    /**
     * Find dependencies given a type
     *
     * For each type we encounter, we need to figure out if it's something from the java
     * packages, an external dependency or something from the application under test.
     *
     * If it's a DTO in the AUT, we may need to copy it as well. Those that are identified
     * as being local dependencies in the AUT are added to the dependencies set. Those are
     * destined to be copied once parsing the controller has been completed.
     *
     * Types that are found in external jars are added to the externalDependencies set.
     * These are not copied across with the generated tests.
     *
     * @param type the type to resolve
     */
    protected void solveTypeDependencies(TypeDeclaration<?> from, Type type) {
        if (type.isClassOrInterfaceType()) {
            if (type.asClassOrInterfaceType().isBoxedType()) {
                solveConstant(from, type);
            } else {
                solveClassDependency(from, type);
            }
        } else {
            /*
             * Primitive constants that are assigned a value based on a static import is the
             * hardest thing to solve.
             */
            solveConstant(from, type);
        }
    }

    /**
     * Solve the dependency problem for a field declaration that has an assignment.
     * This method does not return anything but has a side effect. It will result
     * in the dependency graph being updated.
     *
     * @param from the node from which we are trying to solve the dependency
     * @param type the type of the field being resolved.
     */
    private void solveConstant(TypeDeclaration<?> from, Type type) {
        Optional<VariableDeclarator> vdecl = type.findAncestor(VariableDeclarator.class);
        if (vdecl.isEmpty()) {
            return;
        }

        Optional<Expression> optExpr = vdecl.get().getInitializer();
        if(optExpr.isEmpty()) {
            return;
        }
        Expression expr = optExpr.get();

        if(expr.isNameExpr()) {
            String name = expr.asNameExpr().getName().asString();
            ImportDeclaration imp = resolveImport(name);
            if (imp != null) {
                String className = imp.getNameAsString();

                int index = className.lastIndexOf(".");
                if (index > 0) {
                    String pkg = className.substring(0, index);
                    CompilationUnit depCu = getCompilationUnit(pkg);
                    if (depCu != null) {
                        ClassDependency dep = new ClassDependency(from, pkg);
                        addEdge(from.getFullyQualifiedName().orElse(null), dep);
                    }
                }
            }
        }
    }

    /**
     * Find the compilation unit for the given class
     * @param className the class name
     * @return a CompilationUnit instance or null
     */
    private static CompilationUnit getCompilationUnit(String className) {
        CompilationUnit depCu = AntikytheraRunTime.getCompilationUnit(className);
        if (depCu == null) {
            try {
                DTOHandler handler = new DTOHandler();
                handler.parse(AbstractCompiler.classToPath(className));
                depCu = handler.getCompilationUnit();
            } catch (IOException iex) {
                logger.debug("No compilation unit for {}", className);
            }
        }
        return depCu;
    }

    private void solveClassDependency(TypeDeclaration<?> from, Type type) {
        ClassOrInterfaceType classType = type.asClassOrInterfaceType();

        String mainType = classType.getNameAsString();
        NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

        if("DateScheduleUtil".equals(mainType) || "Logger".equals(mainType)) {
            /*
             * Absolutely no reason for a DTO to have DateScheduleUtil or Logger as a dependency.
             */

            return;
        }
        if (secondaryType != null) {
            for (Type t : secondaryType) {
                if(t.asString().length() != 1 ) {
                    solveTypeDependencies(from, t);
                }
            }
        }

        ImportDeclaration imp = resolveImport(mainType);
        if (imp == null) {
            /*
             * No import for this. Lets find out if there exists a class in the same folder
             */
            cu.getPackageDeclaration().ifPresent(pkg -> {
                String className = pkg.getNameAsString() + "." + mainType;
                ClassDependency dep = new ClassDependency(from, className);
                addEdge(from.getFullyQualifiedName().get(), dep);
            });
        }
        else {
            createEdge(classType, from);
        }
    }

    /**
     * Finds all the classes in a package with in the application under test.
     * We do not search jars, external dependencies or the java standard library.
     *
     * @param packageName the package name
     */
    protected void findClassInPackage(String packageName) {
        Path p = Paths.get(Settings.getBasePath(), packageName.replace(".", "/"));
        File directory = p.toFile();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    String fname = f.getName();
                    if (fname.endsWith(AbstractCompiler.SUFFIX)) {
                        String imp = packageName + "." + fname.substring(0, fname.length() - 5);
                        ImportDeclaration importDeclaration = new ImportDeclaration(imp, false, false);
                        allImports.add(importDeclaration);
                    }
                }
            }
        }
    }

    /**
     * Expands wild card imports.
     * Which means we delete the asterisk import and add all the classes in the package as
     * individual imports.
     *
     * @param cu the compilation unit
     */
    protected void expandWildCards(CompilationUnit cu) {

        for(var imp : cu.getImports()) {
            if(imp.isAsterisk() && !imp.isStatic()) {
                String packageName = imp.getNameAsString();
                if (packageName.startsWith(Settings.getBasePackage())) {
                    findClassInPackage(packageName);
                }
            }
        }
    }

    protected boolean createEdge(Type typeArg, TypeDeclaration<?> from) {
        try {
            return createEdgeHelper(typeArg, from);
        } catch (UnsolvedSymbolException e) {
            ImportDeclaration decl = resolveImport(typeArg.asClassOrInterfaceType().getNameAsString());
            if (decl != null && !decl.getNameAsString().startsWith(PREFIX)) {
                addEdge(from.getFullyQualifiedName().orElse(null), new ClassDependency(from, decl.getNameAsString()));
                return true;
            }
            logger.debug("Unresolvable {}", typeArg);
        }
        return false;
    }

    private boolean createEdgeHelper(Type typeArg, TypeDeclaration<?> from) {
        if(typeArg.isPrimitiveType() ||
                (typeArg.isClassOrInterfaceType() && typeArg.asClassOrInterfaceType().isBoxedType())) {
            Node parent = typeArg.getParentNode().orElse(null);
            return createEdgeHelper(typeArg, from, parent);
        }
        String description = typeArg.resolve().describe();
        if (!description.startsWith(PREFIX)) {
            ClassDependency dependency = new ClassDependency(from, description);
            for (var jarSolver : jarSolvers) {
                if (jarSolver.getKnownClasses().contains(description)) {
                    dependency.setExternal(true);
                    return true;
                }
            }
            addEdge(from.getFullyQualifiedName().orElse(null), dependency);
        }
        return false;
    }

    private boolean createEdgeHelper(Type typeArg, TypeDeclaration<?> from, Node parent) {
        if (parent instanceof VariableDeclarator vadecl) {
            Expression init = vadecl.getInitializer().orElse(null);
            if (init != null) {
                if (init.isFieldAccessExpr()) {
                    FieldAccessExpr fae = init.asFieldAccessExpr();
                    ResolvedValueDeclaration rfd = fae.resolve();
                    addEdge(from.getFullyQualifiedName().orElse(null), new ClassDependency(from, rfd.getType().describe()));

                }
                else if (!init.isConditionalExpr() && !init.isEnclosedExpr() && !init.isCastExpr() &&
                        !init.isMethodCallExpr() && !init.isLiteralExpr()) {
                    JavaParserFieldDeclaration fieldDeclaration = symbolResolver.resolveDeclaration(init, JavaParserFieldDeclaration.class);
                    ResolvedTypeDeclaration declaringType = fieldDeclaration.declaringType();
                    addEdge(from.getFullyQualifiedName().orElse(null), new ClassDependency(from, declaringType.getQualifiedName()));
                    return true;
                }
            }
            else {
                logger.debug("Variable {} being initialized through method call but it should not matter", typeArg);
            }
        }
        return false;
    }

    protected void addEdge(String fromName, ClassDependency dependency) {
        dependencies.computeIfAbsent(fromName, k -> new HashSet<>()).add(dependency);
    }

    /**
     * Finds an import that matches the given type name
     * @param name the name of an interface, a class or an enum. This maybe a fully qualified name or a simple name.
     * @return an ImportDeclaration instance if one can be found or null
     */
    protected ImportDeclaration resolveImport(String name) {
        for (ImportDeclaration importDeclaration : allImports) {
            Name importedName = importDeclaration.getName();
            if (importedName.toString().equals(name)) {
                keepImports.add(importDeclaration);
                return importDeclaration;
            }
            String[] parts = importedName.toString().split("\\.");
            if(parts.length > 1 && parts[parts.length - 1].equals(name)) {
                keepImports.add(importDeclaration);
                return importDeclaration;
            }

            if(importDeclaration.isAsterisk()) {
                ImportDeclaration solvedImport = matchWildCard(name, importedName);
                if (solvedImport != null) return solvedImport;
            }
        }
        return null;
    }

    /**
     * Asterisk imports are tricky.
     * We have so much code for resolving them. Please make life easier for compiler writers
     * they don't you want to use them in your code.
     * Checks all the classes under the package to find if there is a match. Sometimes what we
     * think to be a package is not really a package but a class and the import happens to be a
     * static import.
     *
     * @param name of the class to match
     * @param importedName the name in the import
     * @return an ImportDeclaration instance if one can be found or null.
     */
    private ImportDeclaration matchWildCard(String name, Name importedName) {
        String packageName = importedName.toString();
        SymbolReference<ResolvedReferenceTypeDeclaration> ref = combinedTypeSolver.tryToSolveType(packageName + "." + name);
        if (ref.isSolved()) {
            ImportDeclaration solvedImport = new ImportDeclaration(ref.getCorrespondingDeclaration().getQualifiedName(), false, false);
            keepImports.add(solvedImport);
            return solvedImport;
        }

        ref = combinedTypeSolver.tryToSolveType(packageName);
        if (ref.isSolved()) {
            Optional<ResolvedReferenceTypeDeclaration> resolved = ref.getDeclaration();
            if(resolved.isPresent()) {
                for(ResolvedFieldDeclaration field : resolved.get().getDeclaredFields()) {
                    if (field.getName().equals(name)) {
                        ImportDeclaration solvedImport = new ImportDeclaration(
                                packageName + "." + name, field.isStatic(), false);
                        keepImports.add(solvedImport);
                        return solvedImport;
                    }
                }
            }
        }
        return null;
    }

    protected void copyDependencies() throws IOException {
        List<Map.Entry<String, ClassDependency>> toCopy = new ArrayList<>();

        for (TypeDeclaration<?> declaration : cu.getTypes()) {
            Optional<String> fullyQualifiedName = declaration.getFullyQualifiedName();
            if (fullyQualifiedName.isPresent()) {
                Set<ClassDependency> deps = dependencies.get(fullyQualifiedName.get());

                if (deps != null) {
                    for (ClassDependency dependency : deps) {
                        toCopy.add(new AbstractMap.SimpleEntry<>(fullyQualifiedName.get(), dependency));
                    }
                }
            }
        }

        for (Map.Entry<String, ClassDependency> entry : toCopy) {
            copyDependency(entry.getKey(), entry.getValue());
        }
    }

    protected void addEdgeFromImport(TypeDeclaration<?> fromType, ClassOrInterfaceType ext, ImportDeclaration imp) {
        if (imp != null) {
            keepImports.add(imp);
            ClassDependency dep = new ClassDependency(fromType, imp.getNameAsString());
            addEdge(fromType.getFullyQualifiedName().orElseThrow(), dep);
        }
        else {
            /*
             * No import for this. Lets find out if there exists a class in the same folder
             */
            cu.getPackageDeclaration().ifPresent(pkg -> {
                String className = pkg.getNameAsString() + "." + ext.getNameAsString();
                ClassDependency dep = new ClassDependency(fromType, className);
                addEdge(fromType.getFullyQualifiedName().get(), dep);
            });
        }
    }


    public void parse(String relativePath) throws IOException {
        compile(relativePath);
        if (cu != null) {
            CompilationUnit tmp = cu;
            cu = cu.clone();

            visitTypes();

            cu.getImports().addAll(keepImports);
            cu = tmp;
        }
    }

    protected void visitTypes() {
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
                    cdecl.accept(createTypeCollector(), null);
                }
                for(var ext : cdecl.getExtendedTypes()) {
                    ClassOrInterfaceType parent = ext.asClassOrInterfaceType();
                    ImportDeclaration imp = resolveImport(parent.getNameAsString());
                    addEdgeFromImport(t, ext, imp);
                }
            }
        }
    }

    /**
     * Puts all the classes marked as dependencies of the current class through java parser.
     * @throws IOException if source file could not be processed
     */
    protected void compileDependencies() throws IOException {
        Optional<String> fullyQualifiedName = getPublicType(cu).getFullyQualifiedName();
        if (fullyQualifiedName.isPresent()) {
            Set<ClassDependency> deps = dependencies.get(fullyQualifiedName.get());
            if (deps != null) {
                for (ClassDependency dep : deps) {
                    ClassProcessor cp = new ClassProcessor();
                    cp.compile(AbstractCompiler.classToPath(dep.getTo()));
                }
            }
        }
    }

    class TypeCollector extends ModifierVisitor<Void> {

        @Override
        public Visitable visit(FieldDeclaration field, Void args) {
            collectField(field);
            field.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(
                    classOrInterfaceDeclaration -> collectFieldAncestors(field, classOrInterfaceDeclaration)
            );
            return super.visit(field, args);
        }
    }

    @SuppressWarnings("java:S125")
    protected void collectFieldAncestors(FieldDeclaration field, ClassOrInterfaceDeclaration ancestor) {
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
        solveTypeDependencies(ancestor, field.getElementType());
        VariableDeclarator firstVariable = field.getVariables().get(0);
        firstVariable.getInitializer().ifPresent(init -> {
            if (init.isObjectCreationExpr()) {
                solveTypeDependencies(ancestor, init.asObjectCreationExpr().getType());
            }
        });
    }

    protected void collectField(FieldDeclaration field) {
        String fieldAsString = field.getElementType().toString();
        if (fieldAsString.equals("DateScheduleUtil")
                || fieldAsString.equals("Logger")
                || fieldAsString.equals("Sort.Direction")) {
            return ;
        }

        // Filter annotations to retain only @JsonFormat and @JsonIgnore
        NodeList<AnnotationExpr> filteredAnnotations = new NodeList<>();
        for (AnnotationExpr annotation : field.getAnnotations()) {
            String annotationName = annotation.getNameAsString();
            if (annotationName.equals("JsonFormat") || annotationName.equals("Transient")
                    || annotationName.equals("JsonIgnore")) {
                resolveImport(annotationName);
                filteredAnnotations.add(annotation);
            }
            if( (annotationName.equals("Id") || annotationName.equals("NotNull")) &&
                    "Long".equals(field.getElementType().asString())) {
                field.setAllTypes(new PrimitiveType(PrimitiveType.Primitive.LONG));
            }
        }
        field.getAnnotations().clear();
        field.setAnnotations(filteredAnnotations);
    }

    @SuppressWarnings("java:S1452")
    protected ModifierVisitor<?> createTypeCollector() {
        return new TypeCollector();
    }
}
