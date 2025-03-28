package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
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
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import sa.com.cloudsolutions.antikythera.depsolver.InterfaceSolver;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Reflect;
import sa.com.cloudsolutions.antikythera.evaluator.ReflectionArguments;
import sa.com.cloudsolutions.antikythera.generator.Antikythera;

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
        for (String s : Antikythera.getInstance().getJarPaths()) {
            jarFiles.add(s);
            urls.add(Paths.get(s).toUri().toURL());
        }

        for(String jarFile : jarFiles) {
            JarTypeSolver jarSolver = new JarTypeSolver(jarFile);
            jarSolvers.add(jarSolver);
            combinedTypeSolver.add(jarSolver);
        }

        loader = new URLClassLoader(urls.toArray(new URL[0]), AbstractCompiler.class.getClassLoader());

        Collection<String> finch = Settings.getPropertyList("finch", String.class);

        for(String path : finch) {
            combinedTypeSolver.add(new JavaParserTypeSolver(path));
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
            CompilationUnit other = AntikytheraRunTime.getCompilationUnit(fileName.replace(SUFFIX,""));
            if (other != null) {
                return getMatchingType(other, name);

            }
        }
        return Optional.empty();
    }


    public static String findFullyQualifiedTypeName(VariableDeclarator variable) {
        Optional<CompilationUnit> cu = variable.findCompilationUnit();
        if (cu.isPresent()) {
            return findFullyQualifiedName(cu.get(), variable.getType().asString());
        }
        return null;
    }

    /**
     * <p>Creates a compilation unit from the source code at the relative path.</p>
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

        Path sourcePath = Paths.get(Settings.getBasePath(), relativePath);

        File file = sourcePath.toFile();

        // Proceed with parsing the controller file
        FileInputStream in = new FileInputStream(file);
        cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        cache(cu);
        return false;
    }

    private void cache(CompilationUnit cu) {
        for (TypeDeclaration<?> type : findContainedTypes(cu)) {
            type.getFullyQualifiedName().ifPresent(
                    cname -> AntikytheraRunTime.addClass(cname, cu)
            );
        }
    }

    private static List<TypeDeclaration<?>> findContainedTypes(CompilationUnit cu) {
        List<TypeDeclaration<?>> types = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.getTypes()) {
            types.add(type);
            findInners(type, types);
        }
        return types;
    }

    private static void findInners(TypeDeclaration<?> cdecl, List<TypeDeclaration<?>> inners) {
        for (Node child : cdecl.getChildNodes()) {
            if (child instanceof ClassOrInterfaceDeclaration cid) {
                inners.add(cid);
                findInners(cid, inners);
            }
        }
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

    public static Optional<TypeDeclaration<?>> resolveTypeSafely(ClassOrInterfaceType type, Node context) {

        Optional<CompilationUnit> compilationUnit = context.findCompilationUnit();
        if (compilationUnit.isPresent()) {
            CompilationUnit cu = compilationUnit.get();

            for (TypeDeclaration<?> t : cu.getTypes()) {
                if (t.getNameAsString().equals(type.getNameAsString())) {
                    return Optional.of(t);
                }
                Optional<ClassOrInterfaceDeclaration> cid = t.findFirst(
                        ClassOrInterfaceDeclaration.class,
                        c -> c.getNameAsString().equals(type.getNameAsString())
                );
                if (cid.isPresent()) {
                    return Optional.of(cid.get());
                }
            }

            ImportWrapper wrapper = findImport(cu, type.getNameAsString());
            if (wrapper != null && wrapper.getType() != null) {
                return Optional.of(wrapper.getType());
            }

            return findInSamePackage(cu, type);
        }

        return Optional.empty();
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
     * @return the public class, enum or interface that is held in the compilation unit if any.
     *      when no public type is found null is returned.
     */
    public static TypeDeclaration<?> getPublicType(CompilationUnit cu) {
        for (var type : cu.getTypes()) {
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
     * @param cu compilation unit
     * @param className the name of the class to find
     * @return An optional of the type declaration
     */
    public static Optional<TypeDeclaration<?>> getMatchingType(CompilationUnit cu, String className) {
        for (TypeDeclaration<?> type : findContainedTypes(cu)) {
            if (type.getNameAsString().equals(className)
                    || className.equals(type.getFullyQualifiedName().orElse(null))) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }

    /**
     * Compares the list of argument types against the parameters of a callable declarations
     * @param arguments the types of the arguments that need to be matched
     * @param callable the list of callable declarations. These maybe method declarations or
     *                  constructor declarations.
     * @return the callable declaration if the arguments match the parameters
     */
    private static Optional<CallableDeclaration<?>> matchCallable(NodeList<Type> arguments, CallableDeclaration<?> callable) {
        if (arguments != null &&
                (callable.getParameters().size() == arguments.size() ||
                        (callable.getParameters().size() > arguments.size() && callable.getParameter(arguments.size()).isVarArgs() ) )) {
            for (int i = 0; i < arguments.size(); i++) {
                Parameter param = callable.getParameter(i);
                Type argumentType = arguments.get(i);
                Type paramType = param.getType();
                if (paramType.equals(argumentType) || argumentType == null) {
                    continue;
                }
                if (argumentType.isPrimitiveType() && argumentType.asString().equals(paramType.asString().toLowerCase())) {
                    continue;
                }
                if(argumentType.isClassOrInterfaceType() && paramType.isClassOrInterfaceType() && classMatch(argumentType, paramType))
                {
                    continue;
                }
                if (! (paramType.equals(argumentType)
                        || paramType.toString().equals("java.lang.Object")
                        || argumentType.getElementType().isUnknownType()
                        || argumentType.toString().equals(Reflect.primitiveToWrapper(paramType.toString())))
                ) {
                    return Optional.empty();
                }
            }
            return Optional.of(callable);
        }
        return Optional.empty();
    }

    private static boolean classMatch(Type argumentType, Type paramType) {
        ClassOrInterfaceType at = argumentType.asClassOrInterfaceType();
        ClassOrInterfaceType pt = paramType.asClassOrInterfaceType();

        if (pt.getNameAsString().equals(at.getNameAsString())) {
            Optional<NodeList<Type>> args1 = pt.getTypeArguments();
            Optional<NodeList<Type>> args2 = at.getTypeArguments();
            if (args1.isPresent()) {
                if (args2.isPresent()) {
                    return args1.get().size()  == args2.get().size();
                }
            }
            else {
                return args2.isEmpty();
            }
            return true;
        }
        return false;
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
         * First check if the compilation unit directly contains it.
         * Then check if there exists an import that ends with the short class name as it's last component.
         * Check if the package folder contains a java source file with the same name
         * Lastly, we will try to invoke Class.forName to see if the class can be located in any jar file
         *    that we have loaded.
         */

        TypeDeclaration<?> p = getMatchingType(cu, className).orElse(null);
        if (p != null) {
            return p.getFullyQualifiedName().orElse(
                    cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("") + "." + p.getName());
        }
        ImportWrapper imp = findImport(cu, className);
        if (imp != null) {
            if (imp.getImport().isAsterisk()) {
                return imp.getNameAsString() + "." + className;
            }
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

    public static List<ImportWrapper> findImport(CompilationUnit cu, Type t) {
        List<ImportWrapper> imports = new ArrayList<>();
        if (t.isClassOrInterfaceType()) {
            ClassOrInterfaceType ctype = t.asClassOrInterfaceType();
            Optional<NodeList<Type>> typeArguments = ctype.getTypeArguments();
            if (typeArguments.isPresent()) {
                for (Type type : typeArguments.get()) {
                    ImportWrapper imp = findImport(cu, type.asString());
                    if(imp != null) {
                        imports.add(imp);
                    }
                }
            }
            ImportWrapper imp = findImport(cu, ctype.getNameAsString());
            if (imp != null) {
                imports.add(imp);
            }
        }
        else {
            ImportWrapper imp = findImport(cu, t.asString());
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
                return new ImportWrapper(new ImportDeclaration(e.toString(), false, false), true);
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
                 * last part of the import matches the class name
                 */
                ImportWrapper wrapper = new ImportWrapper(imp);
                if (!imp.isStatic()) {
                    CompilationUnit target = AntikytheraRunTime.getCompilationUnit(imp.getNameAsString());
                    if (target != null) {
                        TypeDeclaration<?> p = getMatchingType(target, importName.getIdentifier()).orElse(null);
                        wrapper.setExternal(false);
                        setTypeAndField(className, p, wrapper, target);
                    }

                }
                else if (importName.getQualifier().isPresent()){
                    CompilationUnit target = AntikytheraRunTime.getCompilationUnit(importName.getQualifier().get().toString());
                    if (target != null) {
                        TypeDeclaration<?> p = getPublicType(target);
                        setTypeAndField(className, p, wrapper, target);
                        wrapper.setExternal(false);
                    }
                }
                return wrapper;
            }
        }

        return null;
    }

    private static void setTypeAndField(String className, TypeDeclaration<?> p, ImportWrapper wrapper, CompilationUnit target) {
        if (p != null) {
            wrapper.setType(p);
        }

        target.findFirst(FieldDeclaration.class, f -> f.getVariable(0).getNameAsString().equals(className))
              .ifPresent(wrapper::setField);

        target.findFirst(MethodDeclaration.class, f -> f.getNameAsString().equals(className))
              .ifPresent(wrapper::setMethodDeclaration);
    }

     static ImportWrapper findWildcardImport(CompilationUnit cu, String className) {
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
                    ImportWrapper wrapper = new ImportWrapper(imp, true);
                    ImportDeclaration decl = new ImportDeclaration(fullClassName, imp.isStatic(), false);
                    wrapper.setSimplified(decl);
                    return wrapper;
                } catch (ClassNotFoundException e) {
                    try {
                        AbstractCompiler.loadClass(fullClassName);
                        /*
                         * We are here because the previous attempt at class forname was
                         * unsuccessfully simply because the class had not been loaded.
                         * Here we have loaded it, which obviously means it's there
                         */
                        return new ImportWrapper(imp, true);
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
            ImportWrapper wrapper =  new ImportWrapper(imp, false);
            for(TypeDeclaration<?> type : target.getTypes()) {
                if (type.getNameAsString().equals(className)) {
                    wrapper.setType(type);
                }
            }
            return wrapper;
        }
        CompilationUnit cu2 = AntikytheraRunTime.getCompilationUnit(impName);
        if (cu2 != null && imp.isStatic()) {
            Optional<FieldDeclaration> field =  cu2.findFirst(FieldDeclaration.class,
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
        }
        else {
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
        for (int i =0 ; i < constructors.size() ; i++) {
            ConstructorDeclaration constructor = constructors.get(i);
            Optional<CallableDeclaration<?>> callable = matchCallable(methodCall.getArgumentTypes(), constructor);
            if (callable.isPresent() && callable.get() instanceof ConstructorDeclaration md) {
                return Optional.of(new Callable(md));
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
            return Optional.of(new Callable(constructors.get(found)));
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
                    Optional<CallableDeclaration<?>> callable = matchCallable(methodCall.getArgumentTypes(), method);
                    if (callable.isPresent() && callable.get() instanceof MethodDeclaration md) {
                        return Optional.of(new Callable(md));
                    }
                }
                if (method.getParameters().size() == mce.getArguments().size()) {
                    found = i;
                    occurs++;
                }
            }

            if (overRides) {
                Optional<Callable> method = findCallableInParent(methodCall, decl);
                if (method.isPresent()) {
                    return method;
                }
            }

            if (found != -1 && occurs == 1) {
                return Optional.of(new Callable(methodsByName.get(found)));
            }
        }

        return Optional.empty();
    }

    private static Optional<Callable> findCallableInParent(MCEWrapper methodCall, TypeDeclaration<?> decl) {
        if (decl.isClassOrInterfaceDeclaration()) {
            ClassOrInterfaceDeclaration cdecl = decl.asClassOrInterfaceDeclaration();

            for (ClassOrInterfaceType extended : cdecl.getExtendedTypes()) {
                Optional<CompilationUnit> compilationUnit = cdecl.findCompilationUnit();
                if (compilationUnit.isPresent()) {
                    String fullName = findFullyQualifiedName(compilationUnit.get(), extended.getNameAsString());
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullName);
                    if (cu != null) {
                        TypeDeclaration<?> p = getMatchingType(cu, extended.getNameAsString()).orElse(null);
                        Optional<Callable> method = findCallableDeclaration(methodCall, p);
                        if (method.isPresent()) {
                            return method;
                        }
                    } else {
                        /*
                         * the extended type is not in the same compilation unit, we will have to
                         * load the class and try to find the method in it.
                         */
                        ImportWrapper wrapper = findImport(decl.findCompilationUnit().get(), extended.getNameAsString());
                        if (wrapper != null && wrapper.isExternal()) {
                            try {
                                Class<?> clazz = AbstractCompiler.loadClass(wrapper.getNameAsString());
                                ReflectionArguments reflectionArguments = new ReflectionArguments(
                                        methodCall.getMethodName(), new Object[] {}, methodCall.getArgumentTypesAsClasses()
                                );
                                Method method = Reflect.findMethod(clazz, reflectionArguments);
                                if (method != null) {
                                    return Optional.of(new Callable(method));
                                }
                            } catch (ClassNotFoundException e) {
                                return Optional.empty();
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static Optional<Callable> findCallableDeclaration(MCEWrapper methodCall,
                                                                      TypeDeclaration<?> decl) {
        if(methodCall.getMethodCallExpr() instanceof MethodCallExpr) {
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

    public static TypeDeclaration<?> getEnclosingClassOrInterface(Node n) {
        if (n instanceof ClassOrInterfaceDeclaration cdecl) {
            return cdecl;
        }
        if (n instanceof AnnotationDeclaration ad) {
            return ad;
        }
        if (n != null) {
            Optional<Node> parent = n.getParentNode();
            if (parent.isPresent()) {
                return getEnclosingClassOrInterface(parent.get());
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
}
