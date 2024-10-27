package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

            try {
                setupParser();
            } catch (ReflectiveOperationException e) {
                logger.error("Could not load custom jar files");
            }
        }
    }

    protected static void setupParser() throws IOException, ReflectiveOperationException {
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
            urls[i] = new URL("file:///" + jarFile);
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

    public static void reset() throws ReflectiveOperationException, IOException {
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
     * Build a map of all the fields in a class by field name.
     * Warning: Most of the time you should use a Visitor instead of this method
     *
     * @param cu Compilation unit
     * @param className the name of the class that we are searching for in the compilation unit
     * @return a collection of fields in this class
     */
    public static Map<String, FieldDeclaration> getFields(CompilationUnit cu, String className) {
        Map<String, FieldDeclaration> fields = new HashMap<>();
        for (var type : cu.getTypes()) {
            if(type.getNameAsString().equals(className)) {
                for (var member : type.getMembers()) {
                    if (member.isFieldDeclaration()) {
                        FieldDeclaration field = member.asFieldDeclaration();
                        for (var variable : field.getVariables()) {
                            fields.put(variable.getNameAsString(), field);
                        }
                    }
                }
            }
        }
        return fields;
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
                logger.info("Error resolving type: {}", node);
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
        }
        return null;
    }

    protected static TypeDeclaration<?> getMatchingClass(String fullyQualifiedClassName) {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedClassName);
        String[] parts = fullyQualifiedClassName.split(".");
        return getMatchingClass(cu, parts[parts.length - 1]);
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
     * @param cu
     * @param className
     * @return
     */
    public static String findFullyQualifiedName(CompilationUnit cu, String className) {
        /*
         * The strategy is three fold. First check if there exists an import that ends with the
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

    /**
     * Finds an import statement corresponding to the class name in the compilation unit
     * @param cu The Compilation unit
     * @param className the class to search for
     * @return the import declaration or null if not found
     */
    public static ImportDeclaration findImport(CompilationUnit cu, String className) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(className)) {
                return imp;
            }
            String[] parts = imp.getNameAsString().split("\\.");
            if (parts.length > 0 && className.equals(parts[parts.length - 1])) {
                return imp;
            }
        }
        return null;
    }

    public static Optional<MethodDeclaration> findMethodDeclaration(MethodCallExpr methodCall,
                                                                    ClassOrInterfaceDeclaration decl) {
        return findMethodDeclaration(methodCall, decl.getMethods());
    }


    /**
     * Find the method declaration matching the given method call expression
     * @param methodCall the method call exppression
     * @param methods the list of method declarations to search from
     * @return the method declaration or empty if not found
     */
    public static Optional<MethodDeclaration> findMethodDeclaration(MethodCallExpr methodCall, List<MethodDeclaration> methods) {
        for (MethodDeclaration method : methods) {
            if (method.getParameters().size() == methodCall.getArguments().size() && method.getNameAsString().equals(methodCall.getNameAsString())) {
                if(method.getParameters().isEmpty()) {
                    return Optional.of(method);
                }
                for (int i =0 ; i < method.getParameters().size(); i++) {
                    ResolvedType argType = methodCall.getArguments().get(i).calculateResolvedType();
                    ResolvedType paramType = method.getParameter(i).getType().resolve();
                    if (argType.describe().equals(paramType.describe())
                            || paramType.describe().equals("java.lang.Object")
                            || paramType.describe().equals(Reflect.primitiveToWrapper(argType.describe()))
                            || argType.describe().equals(Reflect.primitiveToWrapper(paramType.describe()))
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
     * @throws IOException
     */
    public static void preProcess() throws IOException {
        List<File> javaFiles = Files.walk(Paths.get(Settings.getBasePath()))
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
