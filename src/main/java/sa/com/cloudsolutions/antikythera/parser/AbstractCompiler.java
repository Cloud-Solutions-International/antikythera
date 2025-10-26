package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import sa.com.cloudsolutions.antikythera.depsolver.InterfaceSolver;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.ReflectionArguments;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

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
    public static final String SUFFIX = ".java";

    private static JavaParser javaParser;
    protected static JavaSymbolSolver symbolResolver;
    protected static CombinedTypeSolver combinedTypeSolver;
    protected static ArrayList<JarTypeSolver> jarSolvers;
    protected static ClassLoader loader;
    protected CompilationUnit cu;
    protected String className;
    protected static Map<String, TypeWrapper> typeCache = new HashMap<>();

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

        Set<String> jarFiles = new HashSet<>();
        List<URL> urls = new ArrayList<>();
        for (String s : Settings.getJarFiles()) {
            jarFiles.add(s);
            urls.add(Paths.get(s).toUri().toURL());
        }
        for (String s : MavenHelper.getJarPaths()) {
            jarFiles.add(s);
            urls.add(Paths.get(s).toUri().toURL());
        }

        for (String jarFile : jarFiles) {
            JarTypeSolver jarSolver = new JarTypeSolver(jarFile);
            jarSolvers.add(jarSolver);
            combinedTypeSolver.add(jarSolver);
        }

        loader = new URLClassLoader(urls.toArray(new URL[0]),
                loader == null ? AbstractCompiler.class.getClassLoader() : loader);
        Collection<String> finch = Settings.getPropertyList("finch", String.class);

        for (String path : finch) {
            combinedTypeSolver.add(new JavaParserTypeSolver(path));
        }

        symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        javaParser = new JavaParser(parserConfiguration);
    }

    /**
     * Converts a class name to a path name.
     * Simply replaces the `.` with the `/`
     *
     * @param className the fully qualified class name
     * @return a path relative to the base
     */
    public static String classToPath(String className) {
        if (className.endsWith(SUFFIX)) {
            className = className.replace(SUFFIX, "");
        }

        String path = className.replace(".", "/");
        return path + SUFFIX;
    }

    /**
     * Given a path creates a fully qualified class name
     *
     * @param path a file
     * @return a fully qualified class
     */
    public static String pathToClass(String path) {
        if (path.endsWith(SUFFIX)) {
            path = path.replace(SUFFIX, "");
        }
        return path.replace("/", ".");
    }

    public static Class<?> loadClass(String resolvedClass) throws ClassNotFoundException {
        try {
            return Class.forName(resolvedClass);
        } catch (ClassNotFoundException cnf) {
            return loader.loadClass(resolvedClass);
        }
    }

    public static void reset() throws IOException {
        setupParser();
    }

    static Optional<TypeDeclaration<?>> findInSamePackage(CompilationUnit compilationUnit, Type fd) {
        String packageName = compilationUnit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
        String name = fd.isClassOrInterfaceType() ? fd.asClassOrInterfaceType().getNameAsString() : fd.toString();
        String fileName = packageName + "." + name + SUFFIX;

        if (new File(Settings.getBasePath(), classToPath(fileName)).exists()) {
            CompilationUnit other = AntikytheraRunTime.getCompilationUnit(fileName.replace(SUFFIX, ""));
            if (other != null) {
                return getMatchingType(other, name);

            }
        }
        return Optional.empty();
    }


    /**
     * Finds the types required to fully represent the given variable
     * @param variable a Node representing either a VariableDeclarator or a Parameter
     * @return a list of TypeWrappers representing all the types that make up this particular
     *      variable. An empty list will be returned when type resolution has failed.
     *      A single item list means this variable does not use generics.
     */
    public static List<TypeWrapper> findTypesInVariable(Node variable) {
        Optional<CompilationUnit> cu = variable.findCompilationUnit();
        if (cu.isEmpty()) {
            return List.of();
        }

        Type type = switch (variable) {
            case VariableDeclarator v -> v.getType();
            case FieldDeclaration f -> f.getElementType();
            case Parameter parameter -> parameter.getType();
            default -> null;
        };

        if (type == null) {
            return List.of();
        }

        return findWrappedTypes(cu.get(), type);
    }

    public static List<TypeWrapper> findWrappedTypes(CompilationUnit cu, Type type) {
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if (classType.getTypeArguments().isPresent()) {
                List<TypeWrapper> typeWrappers = new ArrayList<>();
                List<Type> args = classType.getTypeArguments().orElseThrow();
                for (Type arg : args) {
                    typeWrappers.add(findType(cu, arg));
                }
                typeWrappers.add(findType(cu, classType.getNameAsString()));
                return typeWrappers;
            }
        }

        TypeWrapper foundType = findType(cu, type);
        return foundType != null ? List.of(foundType) : List.of();
    }

    /**
     * <p>Creates a compilation unit from the source code at the relative path.</p>
     * <p>
     * If this file has previously been resolved, it will not be recompiled rather, it will be
     * fetched from the resolved map.
     *
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

        Path sourcePath = Paths.get(Settings.getBasePath(), relativePath);

        File file = sourcePath.toFile();

        // Proceed with parsing the controller file
        FileInputStream in = new FileInputStream(file);
        cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        cache(cu);
        return false;
    }

    private void cache(CompilationUnit cu) {
        for (TypeDeclaration<?> type : cu.getTypes()) {
            findContainedTypes(type, cu);
        }
    }

    private void findContainedTypes(TypeDeclaration<?> declaration, CompilationUnit cu) {
        for(TypeDeclaration<?> type : declaration.findAll(TypeDeclaration.class)) {
            TypeWrapper typeWrapper = new TypeWrapper(type);
            if(type.isAnnotationPresent("Service") || type.isAnnotationPresent("org.springframework.stereotype.Service")) {
                typeWrapper.setService(true);
            } else if(type.isAnnotationPresent("RestController")
                    || type.isAnnotationPresent("Controller")) {
                typeWrapper.setController(true);
            } else if(type.isAnnotationPresent("Component")) {
                typeWrapper.setComponent(true);
            }

            if(type.isClassOrInterfaceDeclaration()) {
                ClassOrInterfaceDeclaration cdecl = type.asClassOrInterfaceDeclaration();
                typeWrapper.setInterface(cdecl.isInterface());
            }
            type.getFullyQualifiedName().ifPresent(name -> {
                AntikytheraRunTime.addType(name, typeWrapper);
                AntikytheraRunTime.addCompilationUnit(name, cu);

            });
            if (!type.equals(declaration)) {
                findContainedTypes(type, cu);
            }
        }
    }

    /**
     * Get the name of the parameter for a rest controller
     *
     * @param param the parameter
     * @return the name of the parameter
     */
    public static String getRestParameterName(Parameter param) {
        Optional<AnnotationExpr> ann = param.getAnnotationByName("PathVariable");

        if (ann.isPresent()) {
            if (ann.get().isSingleMemberAnnotationExpr()) {
                return ann.get().asSingleMemberAnnotationExpr().getMemberValue().toString().replace("\"", "");
            }
            if (ann.get().isNormalAnnotationExpr()) {
                for (var pair : ann.get().asNormalAnnotationExpr().getPairs()) {
                    if (pair.getNameAsString().equals("value") || pair.getNameAsString().equals("name")) {
                        return pair.getValue().toString().replace("\"", "");
                    }
                }
            }
        }

        return param.getNameAsString();
    }

    /**
     * Get the compilation unit for the current class
     *
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
     *
     * @param cu the compilation unit
     * @return the public class, enum or interface that is held in the compilation unit if any.
     * when no public type is found, null is returned.
     */
    public static TypeDeclaration<?> getPublicType(CompilationUnit cu) {
        for (TypeDeclaration<?> type : cu.getTypes()) {
            if (type.isClassOrInterfaceDeclaration() && type.asClassOrInterfaceDeclaration().isPublic()) {
                return type;
            }
            if (type.isEnumDeclaration() && type.asEnumDeclaration().isPublic()) {
                return type;
            }
        }
        return null;
    }

    /**
     * Finds the class inside the compilation unit that matches the class name
     *
     * @param cu        compilation unit
     * @param className the name of the class to find
     * @return An optional of the type declaration
     */
    @SuppressWarnings("java:S1452")
    public static Optional<TypeDeclaration<?>> getMatchingType(CompilationUnit cu, String className) {
        for (TypeDeclaration<?> type : cu.findAll(TypeDeclaration.class)) {
            if (type.getNameAsString().equals(className)
                    || className.equals(type.getFullyQualifiedName().orElse(null))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Compares the list of argument types against the parameters of a callable declarations
     *
     * @param methodCall the wrapper method call we are dealing with
     * @param callable  the list of callable declarations. These maybe method declarations or
     *                  constructor declarations.
     * @return the callable declaration if the arguments match the parameters
     */
    private static Optional<CallableDeclaration<?>> matchCallable(MCEWrapper methodCall, CallableDeclaration<?> callable) {
        NodeList<Type> arguments = methodCall.getArgumentTypes();
        if (arguments != null &&
                (callable.getParameters().size() == arguments.size() ||
                        (callable.getParameters().size() > arguments.size() && callable.getParameter(arguments.size()).isVarArgs()))) {
            for (int i = 0; i < arguments.size(); i++) {
                Parameter param = callable.getParameter(i);
                Type argumentType = arguments.get(i);
                Type paramType = param.getType();
                if (matchParameterVsArgument(param, argumentType, methodCall)) {
                    continue;
                }
                if (!(paramType.equals(argumentType)
                        || paramType.toString().equals("java.lang.Object")
                        || argumentType.getElementType().isUnknownType()
                        || argumentType.toString().equals(Reflect.primitiveToWrapper(paramType.toString()).getName()))
                ) {
                    return Optional.empty();
                }
            }
            return Optional.of(callable);
        }
        return Optional.empty();
    }

    private static boolean matchParameterVsArgument(Parameter param, Type argumentType, MCEWrapper methodCall) {
        Type paramType = param.getType();
        if (paramType.equals(argumentType) || argumentType == null) {
            return true;
        }
        if (argumentType.isPrimitiveType() && argumentType.asString().equals(paramType.asString().toLowerCase())) {
            return true;
        }
        if (argumentType.isClassOrInterfaceType() && paramType.isClassOrInterfaceType())  {
            return parametersVsArgumentsDeepCompare(param, argumentType, methodCall, paramType);
        }
        return false;
    }

    private static boolean parametersVsArgumentsDeepCompare(Parameter param, Type argumentType, MCEWrapper methodCall, Type paramType) {
        Optional<MethodCallExpr> mce = methodCall.asMethodCallExpr();

        if (mce.isPresent() && mce.get().findCompilationUnit().isPresent()) {
            return parametersVsArgumentsDeepCompare(param, argumentType, paramType, mce.get());
        }

        ClassOrInterfaceType at = argumentType.asClassOrInterfaceType();
        ClassOrInterfaceType pt = paramType.asClassOrInterfaceType();

        if (pt.getNameAsString().equals(at.getNameAsString())) {
            Optional<NodeList<Type>> args1 = pt.getTypeArguments();
            Optional<NodeList<Type>> args2 = at.getTypeArguments();
            if (args1.isPresent()) {
                if (args2.isPresent()) {
                    return args1.get().size() == args2.get().size();
                }
            } else {
                return args2.isEmpty();
            }
            return true;
        }

        return false;
    }

    private static boolean parametersVsArgumentsDeepCompare(Parameter param, Type argumentType, Type paramType, MethodCallExpr mce) {
        CompilationUnit callerSource = mce.findCompilationUnit().orElseThrow();
        CompilationUnit declarationSource = param.findCompilationUnit().orElseThrow();

        List<TypeWrapper> callerTypes = findWrappedTypes(callerSource, argumentType);
        List<TypeWrapper> declarationTypes = findWrappedTypes(declarationSource, paramType);

        if (callerTypes.isEmpty()) {
            return false;
        }
        TypeWrapper wp = callerTypes.getLast();
        TypeWrapper ap = declarationTypes.getLast();
        if (wp.getType() != null && ap.getType() != null) {
            return (wp.getType().getFullyQualifiedName().orElseThrow().equals(ap.getType().getFullyQualifiedName().orElseThrow()));
        }
        if (wp.getClazz() != null && ap.getClazz() != null) {
            return wp.getClazz().isAssignableFrom(ap.getClazz()) || ap.getClazz().isAssignableFrom(wp.getClazz());
        }

        return false;
    }


    public static String findFullyQualifiedName(CompilationUnit cu, Type t) {
        if (t instanceof ClassOrInterfaceType ctype) {
            return findFullyQualifiedName(cu, ctype.getNameAsString());
        }
        return findFullyQualifiedName(cu, t.asString());
    }

    /**
     * Finds the fully qualified classname given the short name of a class.
     *
     * @param cu        Compilation unit where the classname name was discovered
     * @param className to find the fully qualified name for. If the class name is already a
     *                  fully qualified name, the same will be returned.
     * @return the fully qualified name of the class.
     */
    public static String findFullyQualifiedName(CompilationUnit cu, String className) {
        if (cu == null) {
            return null;
        }
        TypeWrapper wrapper = findType(cu, className);
        if (wrapper == null) {
            return null;
        }
        TypeDeclaration<?> p = wrapper.getType();
        if (p != null) {
            return p.getFullyQualifiedName().orElse(
                    cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("") + "." + p.getName());
        }
        Class<?> cls = wrapper.getClazz();
        if (cls != null) {
            return cls.getName();
        }
        return null;
    }

    public static TypeWrapper findType(CompilationUnit cu, Type type) {
        if (type instanceof ClassOrInterfaceType ctype) {
            TypeWrapper wrapper = findType(cu, ctype.getNameAsString());
            if (wrapper == null && ctype.getScope().isPresent()) {
                String fullName = ctype.getScope().orElseThrow().asString() + "." + ctype.getNameAsString();
                if (!fullName.equals(ctype.getNameAsString())) {
                    return findType(cu, fullName);
                }
            }
            return wrapper;
        }
        return findType(cu, type.asString());
    }

    public static TypeWrapper findType(CompilationUnit cu, String className) {
        /*
         * If the compilation unit is null, this may be part of the java.lang package.
         */
        if (cu == null) {
            try {
                Class<?> c = Class.forName("java.lang." + className);
                return new TypeWrapper(c);
            } catch (ClassNotFoundException e) {
                /*
                 * dirty hack to handle an extreme edge case
                 */
                if (className.equals("Optional")) {
                    return new TypeWrapper(Optional.class);
                }
            }
            return null;
        }

        /*
         * First, check if the compilation unit directly contains the name.
         * Then check if there exists an import that ends with the short class name as its last component.
         * Check if the package folder contains a java source file with the same name.
         * Lastly, we will try to invoke Class.forName to see if the class can be located in any jar file
         *    that we have loaded.
         */
        TypeDeclaration<?> p = getMatchingType(cu, className).orElse(null);
        if (p != null) {
            return new TypeWrapper(p);
        }
        if (AntikytheraRunTime.getTypeDeclaration(className).isPresent()) {
            return new TypeWrapper(AntikytheraRunTime.getTypeDeclaration(className).orElseThrow());
        }

        ImportWrapper imp = findImport(cu, className);
        if (imp != null) {
            if (imp.getType() != null) {
                return new TypeWrapper(imp.getType());
            }
            try {
                if (imp.getImport().isAsterisk()) {
                    return new TypeWrapper(Class.forName(imp.getNameAsString() + "." + className));
                }
                return new TypeWrapper(AbstractCompiler.loadClass(imp.getNameAsString()));
            } catch (ClassNotFoundException e) {
                // ignorable
            }
        }
        for (EnumDeclaration ed : cu.findAll(EnumDeclaration.class)) {
            for (EnumConstantDeclaration constant : ed.getEntries()) {
                if (constant.getNameAsString().equals(className)) {
                    return new TypeWrapper(constant);
                }
            }
        }

        return detectTypeWithClassLoaders(cu, className);
    }

    private static TypeWrapper detectTypeWithClassLoaders(CompilationUnit cu, String className) {
        String packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
        String tentativeName = packageName.isEmpty() ? className : packageName + "." + className;
        Optional<TypeDeclaration<?>> t = AntikytheraRunTime.getTypeDeclaration(tentativeName);
        if (t.isPresent()) {
            return new TypeWrapper(t.get());
        }

        try {
            Class<?> clazz = AbstractCompiler.loadClass(className);
            if (clazz != null) {
                return new TypeWrapper(clazz);
            }
        } catch (ClassNotFoundException e) {
            /*
             * It's ok to silently ignore this one. It just means that the class cannot be
             * located in a jar. That maybe because we don't still have a fully qualified name.
             */
        }

        try {
            return new TypeWrapper(Class.forName("java.lang." + className));

        } catch (ClassNotFoundException ex) {
            /*
             * Once again ignore the exception. We don't have the class in the lang package
             */
        }

        try {
            return new TypeWrapper(Class.forName(tentativeName));
        } catch (ClassNotFoundException ex) {
            /*
             * Once again ignore the exception. We don't have the class in the lang package.
             * But there's one last thing that we can do, check if the given name is actually a
             * fully qualified name!
             */
            if (className.contains(".")) {
                try {
                    return new TypeWrapper(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    return null;
                }
            }

            return null;
        }
    }

    public static List<ImportWrapper> findImport(CompilationUnit cu, Type t) {
        List<ImportWrapper> imports = new ArrayList<>();
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType ctype = t.asClassOrInterfaceType();
            Optional<NodeList<Type>> typeArguments = ctype.getTypeArguments();
            if (typeArguments.isPresent()) {
                for (Type type : typeArguments.get()) {
                    ImportWrapper imp = findImport(cu, type.asString());
                    if (imp != null) {
                        imports.add(imp);
                    }
                }
            }
            ImportWrapper imp = findImport(cu, ctype.getNameAsString());
            if (imp != null) {
                imports.add(imp);
            }
        } else {
            ImportWrapper imp = findImport(cu, t.asString());
            if (imp != null) {
                imports.add(imp);
            }
        }
        return imports;
    }

    /**
     * Finds an import statement corresponding to the class name in the compilation unit
     *
     * @param cu        The Compilation unit
     * @param className the class to search for
     * @return the import declaration or null if not found
     */
    public static ImportWrapper findImport(CompilationUnit cu, String className) {
        ImportWrapper imp = findNonWildcardImport(cu, className);
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
                return new ImportWrapper(new ImportDeclaration(e.toString(), false, false));
            }
        }
        return null;
    }

    private static ImportWrapper findNonWildcardImport(CompilationUnit cu, String className) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.getNameAsString().equals(className) && !imp.isAsterisk()) {
                /*
                 * Easy one straight-up match involving a fully qualified name as className
                 */
                return new ImportWrapper(imp);
            }

            Name importName = imp.getName();
            if (className.equals(importName.getIdentifier()) && !imp.isAsterisk()) {
                /*
                 * the last part of the import matches the class name
                 */
                final ImportWrapper wrapper = new ImportWrapper(imp);
                if (!imp.isStatic()) {
                    AntikytheraRunTime.getTypeDeclaration(imp.getNameAsString()).ifPresent(
                        p -> setTypeAndField(className, p, wrapper)
                    );
                } else if (importName.getQualifier().isPresent()) {
                    AntikytheraRunTime.getTypeDeclaration(importName.getQualifier().orElseThrow().toString()).ifPresent(
                            p -> setTypeAndField(className, p, wrapper)
                    );
                }
                return wrapper;
            }
        }

        return null;
    }

    private static void setTypeAndField(String className, TypeDeclaration<?> p, ImportWrapper wrapper) {
        wrapper.setType(p);
        p.findFirst(FieldDeclaration.class, f -> f.getVariable(0).getNameAsString().equals(className))
                .ifPresent(wrapper::setField);

        p.findFirst(MethodDeclaration.class, f -> f.getNameAsString().equals(className))
                .ifPresent(wrapper::setMethodDeclaration);
    }

    static ImportWrapper findWildcardImport(CompilationUnit cu, String className) {
        for (ImportDeclaration imp : cu.getImports()) {
            if (imp.isAsterisk() && !className.contains("\\.")) {
                String impName = imp.getNameAsString();

                String fullClassName = impName + "." + className;
                try {
                    Class<?> clazz = Class.forName(fullClassName);
                    /*
                     * Wild card import. Append the class name to the end and load the class,
                     * we are on this line because it has worked, so this is the correct import.
                     */
                    ImportWrapper wrapper = new ImportWrapper(imp, clazz);
                    ImportDeclaration decl = new ImportDeclaration(fullClassName, imp.isStatic(), false);
                    wrapper.setSimplified(decl);
                    return wrapper;
                } catch (ClassNotFoundException e) {
                    try {
                        Class<?> clazz = AbstractCompiler.loadClass(fullClassName);
                        /*
                         * We are here because the previous attempt at `class forname` was
                         * unsuccessful simply because the class had not been loaded.
                         * Here we have loaded it, which obviously means it's there
                         */
                        return new ImportWrapper(imp, clazz);
                    } catch (ClassNotFoundException ex) {
                        /*
                         * There's one more thing that we can try, append the class name to the
                         * end of the wildcard import and see if the corresponding file can be
                         * located on the base folder.
                         */
                        ImportWrapper wrapper = fakeImport(className, imp, fullClassName, impName);
                        if (wrapper != null) return wrapper;
                    }

                }
            }
        }
        return null;
    }

    private static ImportWrapper fakeImport(String className, ImportDeclaration imp, String fullClassName, String impName) {
        CompilationUnit target = AntikytheraRunTime.getCompilationUnit(fullClassName);
        if (target != null) {
            ImportWrapper wrapper = new ImportWrapper(imp);
            for (TypeDeclaration<?> type : target.getTypes()) {
                if (type.getNameAsString().equals(className)) {
                    wrapper.setType(type);
                }
            }
            return wrapper;
        }
        CompilationUnit cu2 = AntikytheraRunTime.getCompilationUnit(impName);
        if (cu2 != null && imp.isStatic()) {
            Optional<FieldDeclaration> field = cu2.findFirst(FieldDeclaration.class,
                    f -> f.getVariable(0).getNameAsString().equals(className)
            );
            if (field.isPresent()) {
                ImportWrapper wrapper = new ImportWrapper(imp);
                wrapper.setField(field.get());
                return wrapper;
            }

            Optional<EnumConstantDeclaration> ec = cu2.findFirst(EnumConstantDeclaration.class,
                    f -> f.getNameAsString().equals(className)
            );
            if (ec.isPresent()) {
                return new ImportWrapper(imp);
            }
        } else {
            String path = AbstractCompiler.classToPath(fullClassName);
            Path sourcePath = Paths.get(Settings.getBasePath(), path);
            if (sourcePath.toFile().exists()) {
                ImportDeclaration i = new ImportDeclaration(fullClassName, false, false);
                return new ImportWrapper(i);
            }
        }
        return null;
    }


    public static Optional<Callable> findConstructorDeclaration(MCEWrapper methodCall,
                                                                TypeDeclaration<?> decl) {
        int found = -1;
        int occurs = 0;
        List<ConstructorDeclaration> constructors = decl.getConstructors();
        for (int i = 0; i < constructors.size(); i++) {
            ConstructorDeclaration constructor = constructors.get(i);
            Optional<CallableDeclaration<?>> callable = matchCallable(methodCall, constructor);
            if (callable.isPresent() && callable.get() instanceof ConstructorDeclaration md) {
                return Optional.of(new Callable(md, methodCall));
            }
            if (methodCall.getArgumentTypes() != null &&
                    constructor.getParameters().size() == methodCall.getArgumentTypes().size()) {
                found = i;
                occurs++;
            }
        }

        Optional<Callable> c = findCallableInParent(methodCall, decl);
        if (c.isPresent()) {
            return c;
        }

        if (found != -1 && occurs == 1) {
            return Optional.of(new Callable(constructors.get(found), methodCall));
        }
        return Optional.empty();
    }


    public static Optional<Callable> findMethodDeclaration(MCEWrapper methodCall,
                                                           TypeDeclaration<?> decl) {
        return findMethodDeclaration(methodCall, decl, true);
    }

    public static Optional<Callable> findMethodDeclaration(MCEWrapper methodCall,
                                                           TypeDeclaration<?> decl, boolean overRides) {

        if (methodCall.getMethodCallExpr() instanceof MethodCallExpr mce) {
            int found = -1;
            int occurs = 0;
            List<MethodDeclaration> methodsByName = decl.getMethodsByName(methodCall.getMethodName());

            for (int i = 0; i < methodsByName.size(); i++) {
                MethodDeclaration method = methodsByName.get(i);
                if (methodCall.getArgumentTypes() != null) {
                    Optional<CallableDeclaration<?>> callable = matchCallable(methodCall, method);
                    if (callable.isPresent() && callable.get() instanceof MethodDeclaration md) {
                        return Optional.of(new Callable(md, methodCall));
                    }
                }
                if (method.getParameters().size() == mce.getArguments().size()) {
                    found = i;
                    occurs++;
                }
            }

            if (found != -1 && occurs == 1) {
                return Optional.of(new Callable(methodsByName.get(found), methodCall));
            }

            if (overRides) {
                Optional<Callable> method = findCallableInParent(methodCall, decl);
                if (method.isPresent()) {
                    return method;
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<Callable> findCallableInParent(MCEWrapper methodCall, TypeDeclaration<?> typeDeclaration) {
        Optional<CompilationUnit> compilationUnit = typeDeclaration.findCompilationUnit();
        if (compilationUnit.isEmpty()) {
            return Optional.empty();
        }
        if (typeDeclaration instanceof  ClassOrInterfaceDeclaration cdecl) {
            Optional<Callable> method = findCallableInParent(methodCall, cdecl, compilationUnit.get());
            if (method.isPresent()) return method;

            if (Reflect.getMethodsByName(Object.class, methodCall.getMethodName()).isEmpty()) {
                return Optional.empty();
            }
            return findCallableInBinaryCode(Object.class, methodCall);
        }
        if (typeDeclaration.isEnumDeclaration()) {
            if ("equals".equals(methodCall.getMethodName()) && typeDeclaration.getMethodsByName("equals").isEmpty()) {
                MethodDeclaration md =  StaticJavaParser.parseMethodDeclaration("""
                        public boolean equals(Object other) { return this == other; }
                        """);
                typeDeclaration.addMember(md);
                return Optional.of(new Callable(md, methodCall));
            }
            return findCallableInBinaryCode(Enum.class, methodCall);
        }
        return Optional.empty();
    }

    private static Optional<Callable> findCallableInParent(MCEWrapper methodCall, ClassOrInterfaceDeclaration cdecl, CompilationUnit compilationUnit) {
        for (ClassOrInterfaceType extended : cdecl.getExtendedTypes()) {
            TypeWrapper wrapper = findType(compilationUnit, extended);
            if (wrapper != null) {
                TypeDeclaration<?> p = wrapper.getType();
                Optional<Callable> method = (p != null)
                        ? findCallableDeclaration(methodCall, p)
                        : findCallableInBinaryCode(wrapper.getClazz(), methodCall);

                if (method.isPresent()) {
                    return method;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Callable> findCallableInBinaryCode(Class<?> clazz, MCEWrapper methodCall) {
        if (!Reflect.getMethodsByName(clazz, methodCall.getMethodName()).isEmpty()) {
            ReflectionArguments reflectionArguments = new ReflectionArguments(
                    methodCall.getMethodName(),
                    methodCall.getMethodCallExpr().getArguments().toArray(new Object[0]),
                    methodCall.getArgumentTypesAsClasses()
            );
            Method method = Reflect.findAccessibleMethod(clazz, reflectionArguments);
            if (method != null) {
                Callable callable = new Callable(method, methodCall);
                callable.setFoundInClass(clazz);
                return Optional.of(callable);
            }
        }
        return Optional.empty();
    }

    public static Optional<Callable> findCallableDeclaration(MCEWrapper methodCall,
                                                             TypeDeclaration<?> decl) {
        if (methodCall.getMethodCallExpr() instanceof MethodCallExpr) {
            return findMethodDeclaration(methodCall, decl);
        }

        return findConstructorDeclaration(methodCall, decl);
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

    public static TypeDeclaration<?> getEnclosingType(Node n) {
        if (n instanceof ClassOrInterfaceDeclaration cdecl) {
            return cdecl;
        }
        if (n instanceof EnumDeclaration ed) {
            return ed;
        }
        if (n instanceof AnnotationDeclaration ad) {
            return ad;
        }
        if (n != null) {
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent()) {
                return getEnclosingType(parent.get());
            }
        }
        return null;
    }

    public static Type convertLiteralToType(LiteralExpr literal) {
        if (literal.isBooleanLiteralExpr()) {
            return PrimitiveType.booleanType();
        } else if (literal.isCharLiteralExpr()) {
            return PrimitiveType.charType();
        } else if (literal.isDoubleLiteralExpr()) {
            return PrimitiveType.doubleType();
        } else if (literal.isIntegerLiteralExpr()) {
            return PrimitiveType.intType();
        } else if (literal.isLongLiteralExpr()) {
            return PrimitiveType.longType();
        } else if (literal.isStringLiteralExpr()) {
            return new ClassOrInterfaceType(null, "String");
        } else {
            return new UnknownType();
        }
    }


    /**
     * Recursively traverse parents to find a block statement.
     *
     * @param expr the expression to start from
     * @return the block statement that contains expr
     */
    public static BlockStmt findBlockStatement(Node expr) {
        Node currentNode = expr;
        while (currentNode != null) {
            if (currentNode instanceof BlockStmt blockStmt) {
                return blockStmt;
            }
            if (currentNode instanceof MethodDeclaration md) {
                return md.getBody().orElse(null);
            }
            currentNode = currentNode.getParentNode().orElse(null);
        }
        return null; // No block statement found
    }

    public static Optional<ExpressionStmt> findExpressionStatement(MethodCallExpr methodCall) {
        Node n = methodCall;
        while (n != null && !(n instanceof MethodDeclaration)) {
            if (n instanceof ExpressionStmt stmt) {
                /*
                 * We have found the expression statement corresponding to this query
                 */
                return Optional.of(stmt);
            }
            n = n.getParentNode().orElse(null);
        }
        return Optional.empty();
    }

    public static boolean isFinalClass(Type t, CompilationUnit compilationUnit) {
        String fullClassName = AbstractCompiler.findFullyQualifiedName(compilationUnit, t);

        if (fullClassName != null) {
            CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullClassName);
            if (cu != null) {
                TypeDeclaration<?> type = AbstractCompiler.getMatchingType(cu, t.asString()).orElse(null);
                return  (type != null && type.getModifiers().contains(com.github.javaparser.ast.Modifier.finalModifier()));
            } else {
                try {
                    Class<?> clazz = AbstractCompiler.loadClass(fullClassName);
                    if (clazz != null && Modifier.isFinal(clazz.getModifiers())) {
                        return true;
                    }
                } catch (ClassNotFoundException e) {
                    // safe to ignore
                }
            }
        }
        return false;
    }

    public static Type typeFromDeclaration(TypeDeclaration<?> typeDecl) {
        return new ClassOrInterfaceType()
            .setName(typeDecl.getNameAsString())
            .setScope(typeDecl.getFullyQualifiedName()
                .map(fqn -> new ClassOrInterfaceType().setName(
                    fqn.substring(0, fqn.lastIndexOf('.'))))
                .orElse(null));
    }

    /**
     * Converts a class name to an instance name.
     * The usual convention. If we want to create an instance of List that variable is usually
     * called 'list'
     * @param cdecl type declaration
     * @return a variable name as a string
     */
    public static String classToInstanceName(TypeDeclaration<?> cdecl) {
        return classToInstanceName(cdecl.getNameAsString());
    }

    /**
     * Converts a class name to an instance name.
     * @param className as a string
     * @return a variable name as a string
     */
    public static String classToInstanceName(String className) {
        String shortName = fullyQualifiedToShortName(className);
        String name = Character.toLowerCase(shortName.charAt(0)) + shortName.substring(1);
        if(name.equals("long") || name.equals("int")) {
            return "_" + name;
        }
        return name;
    }

    public static String fullyQualifiedToShortName(String name) {
        int index = name.lastIndexOf(".");
        if (index > 0) {
            return name.substring(index + 1);
        }
        return name;
    }

    public static ClassLoader getClassLoader() {
        return loader;
    }

    public static String instanceToClassName(String string) {
        return string.substring(0, 1).toUpperCase() + string.substring(1);
    }

}
