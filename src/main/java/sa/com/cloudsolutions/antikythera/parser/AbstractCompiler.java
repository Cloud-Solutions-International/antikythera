package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import sa.com.cloudsolutions.antikythera.depsolver.InterfaceSolver;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;

import javax.swing.text.html.Option;

/**
 * Sets up the Java Parser and maintains a cache of the classes that have been compiled.
 */
public class AbstractCompiler {
    /*
     * Let's define some terms.
     * A fully qualified class name is something that looks like java.util.List or
     * org.apache.commons.lang3.StringUtils.
     *
     * A simple class name is just the class name without the package name. Which
     * means we have List and StringUtils.
     *
     * A relative path is a path that's relative to the base path of the project.
     */
    /*
     * Many of the fields in this class are static, naturally indicating that they should be shared
     * amongst all instances of the class. Others like the CompilationUnit property
     * are specific to each instance.
     */

    private static final Logger logger = LoggerFactory.getLogger(AbstractCompiler.class);
    public static final String SUFFIX = ".java";

    private static JavaParser javaParser;
    protected static JavaSymbolSolver symbolResolver;
    protected static CombinedTypeSolver combinedTypeSolver;
    protected static ArrayList<JarTypeSolver> jarSolvers;
    protected static ClassLoader loader;
    protected CompilationUnit cu;
    protected String className;

    protected AbstractCompiler() throws IOException {
        if (combinedTypeSolver == null) {
            setupParser();
        }
    }

    protected static void setupParser() throws IOException {
        combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(Settings.getBasePath()));
        jarSolvers = new ArrayList<>();

        URL[] urls = new URL[Settings.getJarFiles().length];

        for(int i = 0 ; i < Settings.getJarFiles().length ; i++) {
            String jarFile = Settings.getJarFiles()[i];
            JarTypeSolver jarSolver = new JarTypeSolver(jarFile);
            jarSolvers.add(jarSolver);
            combinedTypeSolver.add(jarSolver);
            urls[i] = Paths.get(jarFile).toUri().toURL();
        }
        loader = new URLClassLoader(urls);

        Object f = Settings.getProperty("finch");
        if(f != null) {
            List<String> finch = (List<String>) f;
            for(String path : finch) {
                combinedTypeSolver.add(new JavaParserTypeSolver(path));
            }
        }
        symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        javaParser = new JavaParser(parserConfiguration);
    }

    /**
     * Converts a class name to a path name.
     * Simply replaces the . with the /
     * @param className the fully qualified class name
     * @return a path relative to the base
     */
    public static String classToPath(String className) {
        if(className.endsWith(SUFFIX)) {
            className = className.replace(SUFFIX, "");
        }

        String path = className.replace(".", "/");
        return path + SUFFIX;
    }

    /**
     * Given a path creates a fully qualified class name
     * @param path a file
     * @return a fully qualified class
     */
    public static String pathToClass(String path) {
        if(path.endsWith(SUFFIX)) {
            path = path.replace(SUFFIX, "");
        }
        return  path.replace("/", ".");
    }

    public static Class<?> loadClass(String resolvedClass) throws ClassNotFoundException {
        return loader.loadClass(resolvedClass);
    }

    public static void reset() throws IOException {
        setupParser();
    }


    /**
     * Creates a compilation unit from the source code at the relative path.
     *
     * If this file has previously been resolved, it will not be recompiled rather, it will be
     * fetched from the resolved map.
     * @param relativePath a path name relative to the base path of the application.
     * @throws FileNotFoundException when the source code cannot be found
     */
    public boolean compile(String relativePath) throws FileNotFoundException {
        this.className = pathToClass(relativePath);

        cu = AntikytheraRunTime.getCompilationUnit(className);
        if (cu != null) {
            // this has already been compiled
            return true;
        }

        logger.debug("\t{}", relativePath);
        Path sourcePath = Paths.get(Settings.getBasePath(), relativePath);

        File file = sourcePath.toFile();

        // Proceed with parsing the controller file
        FileInputStream in = new FileInputStream(file);
        cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        AntikytheraRunTime.addClass(className, cu);

        // fresh meat
        return false;
    }


    /**
     * Get the name of the parameter for a rest controller
     * @param param the parameter
     * @return the name of the parameter
     */
    public static String getRestParameterName(Parameter param) {
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
     * Alternative approach to resolving a class in Java Parser without having to catch exception
     *
     * In other words we are catching it here and giving you null
     * @param node the node to resolve
     * @return an optional of the resolved type
     */
    public static Optional<ResolvedType> resolveTypeSafely(ClassOrInterfaceType node) {
        Optional<CompilationUnit> compilationUnit = node.findCompilationUnit();
        if (compilationUnit.isPresent()) {
            try {
                return Optional.of(node.resolve());
            } catch (Exception e) {
                // Handle the exception or log it
                logger.debug("Error resolving type: {}", node);
            }
        }
        return Optional.empty();
    }

    public static String absolutePathToClassName(String abs) {
        abs = abs.replace(Settings.getBasePath(), "");
        if(abs.startsWith("/")) {
            abs = abs.substring(1);
        }
        return abs.replace(SUFFIX, "").replace("/",".");
    }
    /**
     * Get the compilation unit for the current class
     * @return a CompilationUnit instance.
     */
    public CompilationUnit getCompilationUnit() {
        return cu;
    }

    protected static boolean shouldSkip(String p) {
        List<?> skip = Settings.getProperty("skip", List.class).orElseGet(List::of);
        for (Object s : skip) {
            if (p.endsWith(s.toString())) {
                return true;
            }
        }
        return false;
    }

    protected JavaParser getJavaParser() {
        return javaParser;
    }

    /**
     * Get the public class in a compilation unit
     * @param cu the compilation unit
     * @return the public class
     */
    protected static TypeDeclaration<?> getPublicClass(CompilationUnit cu) {
        for (var type : cu.getTypes()) {
            if (type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isPublic()) {
                return type;
            }
        }
        return null;
    }

    /**
     * Finds the class inside the compilation unit that matches the class name
     * @param cu compilation unit
     * @param className the name of the class to find
     * @return the type declaration or null if no match is found
     */
    public static TypeDeclaration<?> getMatchingClass(CompilationUnit cu, String className) {
        for (var type : cu.getTypes()) {
            if (type.getNameAsString().equals(className)) {
                return type;
            }
            Optional<String> fullyQualifiedName = type.getFullyQualifiedName();
            if (fullyQualifiedName.isPresent() && fullyQualifiedName.get().equals(className)) {
                return type;
            }
        }
        return null;
    }

    public static Optional<ConstructorDeclaration> findMatchingConstructor(CompilationUnit cu, ObjectCreationExpr oce) {
        return findMatchingConstructor(cu.findAll(ConstructorDeclaration.class), oce.getArguments());
    }

    private static Optional<ConstructorDeclaration> findMatchingConstructor(List<ConstructorDeclaration> constructors, List<Expression> arguments) {
        for (ConstructorDeclaration constructor : constructors) {
            ResolvedConstructorDeclaration resolvedConstructor = constructor.resolve();
            if (resolvedConstructor.getNumberOfParams() == arguments.size()) {
                boolean matched = true;
                for (int i = 0; i < resolvedConstructor.getNumberOfParams(); i++) {
                    ResolvedParameterDeclaration p = resolvedConstructor.getParam(i);
                    ResolvedType argType = arguments.get(i).calculateResolvedType();
                    if (!p.getType().describe().equals(argType.describe())) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    return Optional.of(constructor);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the fully qualified classname given the short name of a class.
     * @param cu Compilation unit where the classname name was discovered
     * @param className to find the fully qualified name for. If the class name is a already a
     *                  fully qualified name the same will be returned.
     * @return the fully qualified name of the class.
     */
    public static String findFullyQualifiedName(CompilationUnit cu, String className) {
        /*
         * The strategy is threefold. First check if there exists an import that ends with the
         * short class name as it's last component. Our preprocessing would have already replaced
         * all the wild card imports with individual imports.
         * If we are unable to find a match, we will check for the existence of a file in the same
         * package locally.
         * Lastly, if we will try to invoke Class.forName to see if the class can be located in
         * any jar file that we have loaded.
         */
        ImportDeclaration imp = findImport(cu, className);
        if (imp != null) {
            return imp.getNameAsString();
        }

        String packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
        String fileName = packageName + "." + className + SUFFIX;
        if (new File(Settings.getBasePath(), classToPath(fileName)).exists()) {
            return packageName + "." + className;
        }

        try {
            Class.forName(className);
            return className;
        } catch (ClassNotFoundException e) {
            /*
             * It's ok to silently ignore this one. It just means that the class cannot be
             * located in a jar. That maybe because we don't still have a fully qualified name.
             */
        }

        try {
            Class.forName("java.lang." + className);
            return "java.lang." + className;
        } catch (ClassNotFoundException ex) {
            /*
             * Once again ignore the exception. We don't have the class in the lang package
             * but it can probably still be found in the same package as the current CU
             */
        }

        try {
            Class.forName(packageName + className);
            return packageName + className;
        } catch (ClassNotFoundException ex) {
            /*
             * Once again ignore the exception. We don't have the class in the lang package
             * but it can probably still be found in the same package as the current CU
             */
            return null;
        }
    }

    public static List<ImportDeclaration> findImport(CompilationUnit cu, Type t) {
        List<ImportDeclaration> imports = new ArrayList<>();
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType ctype = t.asClassOrInterfaceType();
            Optional<NodeList<Type>> typeArguments = ctype.getTypeArguments();
            if (typeArguments.isPresent()) {
                for (Type type : typeArguments.get()) {
                    ImportDeclaration imp = findImport(cu, type.asString());
                    if(imp != null) {
                        imports.add(imp);
                    }
                }
            }
            ImportDeclaration imp = findImport(cu, ctype.getNameAsString());
            if (imp != null) {
                imports.add(imp);
            }
        }
        else {
            ImportDeclaration imp = findImport(cu, t.asString());
            if (imp != null) {
                imports.add(imp);
            }
        }
        return imports;
    }

    /**
     * Finds an import statement corresponding to the class name in the compilation unit
     * @param cu The Compilation unit
     * @param className the class to search for
     * @return the import declaration or null if not found
     */
    public static ImportDeclaration findImport(CompilationUnit cu, String className) {
        ImportDeclaration imp = findNonWildcardImport(cu, className);
        if (imp != null) {
            return imp;
        }
        imp = findWildcardImport(cu, className);
        if (imp != null) {
            return imp;
        }

        /*
         * We are still not done, there's one more thing we can do. Check the extra_exports section
         * which is used precisely for situations where we have a nearly impossible import to
         * resolve
         */
        for (Object e : Settings.getProperty("extra_exports", List.class).orElseGet(List::of)) {
            if (e.toString().endsWith(className)) {
                return new ImportDeclaration(e.toString(), false, false);
            }
        }
        return null;
    }

    private static ImportDeclaration findNonWildcardImport(CompilationUnit cu, String className) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(className)) {
                /*
                 * Easy one straight-up match involving a fully qualified name as className
                 */
                return imp;
            }
            String[] parts = imp.getNameAsString().split("\\.");
            if (parts.length > 0 && className.equals(parts[parts.length - 1])) {
                /*
                 * last part of the import matches the class name
                 */
                return imp;
            }
        }

        return null;
    }

    private static ImportDeclaration findWildcardImport(CompilationUnit cu, String className) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk() && !className.contains("\\.")) {
                String impName = imp.getNameAsString();

                String fullClassName = impName + "." + className;
                try {
                    Class.forName(fullClassName);
                    /*
                     * Wild card import. Append the class name to the end and load the class,
                     * we are on this line because it has worked so this is the correct import.
                     */
                    return new ImportDeclaration(fullClassName, false, false);
                } catch (ClassNotFoundException e) {
                    try {
                        AbstractCompiler.loadClass(fullClassName);
                        /*
                         * We are here because the previous attempt at class forname was
                         * unsuccessfully simply because the class had not been loaded.
                         * Here we have loaded it, which obviously means it's there
                         */
                        return new ImportDeclaration(fullClassName, false, false);
                    } catch (ClassNotFoundException ex) {
                        /*
                         * There's one more thing that we can try, append the class name to the
                         * end of the wildcard import and see if the corresponding file can be
                         * located on the base folder.
                         */
                        if (new File(Settings.getBasePath(), classToPath(fullClassName)).exists()) {
                            return new ImportDeclaration(fullClassName, false, false);
                        }
                        CompilationUnit cu2 = AntikytheraRunTime.getCompilationUnit(impName);
                        if (cu2 != null && imp.isStatic()) {
                            Optional<FieldDeclaration> field =  cu2.findFirst(FieldDeclaration.class,
                                    f -> f.getVariable(0).getNameAsString().equals(className)
                            );
                            if (field.isPresent()) {
                                return imp;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Optional<MethodDeclaration> findMethodDeclaration(MethodCallExpr methodCall,
                                                                    ClassOrInterfaceDeclaration decl) {
        return findMethodDeclaration(methodCall, decl.getMethods());
    }


    static Optional<String> describeType(Expression arg) {
        try {
            ResolvedType argType = arg.calculateResolvedType();
            return Optional.of(argType.describe());
        } catch (Exception e) {
            try {
                ResolvedType argType = symbolResolver.calculateType(arg);
                return Optional.of(argType.describe());
            } catch (UnsolvedSymbolException ex) {
                Optional<ClassOrInterfaceDeclaration> cdecl = arg.findAncestor(ClassOrInterfaceDeclaration.class);
                if (cdecl.isPresent()) {
                    Optional<String> fqn = cdecl.get().getFullyQualifiedName();
                    if(fqn.isPresent()) {
                        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqn.get());
                        if (cu != null) {
                            ImportDeclaration imp = findImport(cu, arg.toString());
                            if (imp != null) {
                                if (imp.isStatic() && imp.isAsterisk() && !imp.getNameAsString().endsWith(arg.toString())) {
                                    CompilationUnit other = AntikytheraRunTime.getCompilationUnit(imp.getNameAsString());
                                    if (other != null) {
                                        Optional<FieldDeclaration> f = other.findFirst(FieldDeclaration.class,
                                                field -> field.getVariable(0).getNameAsString().equals(arg.toString())
                                        );
                                        if (f.isPresent()) {
                                            FieldDeclaration field = f.get();
                                            return Optional.of(field.getVariable(0).getType().asString());
                                        }
                                    }
                                }
                            }
                            String className = findFullyQualifiedName(cu, arg.asNameExpr().getNameAsString());
                            if (className != null) {
                                return Optional.of(className);
                            }
                        }
                        else {
                            return Optional.of(arg.toString());
                        }
                    }
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Find the method declaration matching the given method call expression
     * @param methodCall the method call expression
     * @param methods the list of method declarations to search from
     * @return the method declaration or empty if not found
     */
    public static Optional<MethodDeclaration> findMethodDeclaration(MethodCallExpr methodCall, List<MethodDeclaration> methods) {
        List<String> arguments = new ArrayList<>();
        for (Expression arg : methodCall.getArguments()) {
            Optional<String> d = describeType(arg);
            if (d.isPresent()) {
                arguments.add(d.get());
            }
            else {
                return Optional.empty();
            }
        }

        for (MethodDeclaration method : methods) {
            if (method.getParameters().size() == methodCall.getArguments().size() && method.getNameAsString().equals(methodCall.getNameAsString())) {
                if(method.getParameters().isEmpty()) {
                    return Optional.of(method);
                }
                for (int i =0 ; i < method.getParameters().size(); i++) {
                    ResolvedType paramType = method.getParameter(i).getType().resolve();
                    if (arguments.get(i).equals(paramType.describe())
                            || paramType.describe().equals("java.lang.Object")
                            || paramType.describe().equals(Reflect.primitiveToWrapper(arguments.get(i)))
                            || arguments.get(i).equals(Reflect.primitiveToWrapper(paramType.describe()))
                    )
                    {
                        return Optional.of(method);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Precompile all the java files in the base folder.
     * While doing so we will try to determine what interfaces are implemented by each class.
     *
     * @throws IOException when the files cannot be precompiled.
     */
    public static void preProcess() throws IOException {
        try (var paths = Files.walk(Paths.get(Settings.getBasePath()))) {
            List<File> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(SUFFIX))
                    .map(Path::toFile)
                    .toList();
            for (File javaFile : javaFiles) {
                InterfaceSolver solver = new InterfaceSolver();
                solver.compile(Paths.get(Settings.getBasePath()).relativize(javaFile.toPath()).toString());
            }
        }
    }

    public static ClassOrInterfaceDeclaration getEnclosingClassOrInterface(Node n) {
        if (n instanceof ClassOrInterfaceDeclaration cdecl) {
            return cdecl;
        }
        Optional<Node> parent = n.getParentNode();
        if (parent.isPresent()) {
            return getEnclosingClassOrInterface(parent.get());
        }

        else {
            return null;
        }
    }
}
