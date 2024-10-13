package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

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

    private final JavaParser javaParser;
    protected JavaSymbolSolver symbolResolver;
    protected CombinedTypeSolver combinedTypeSolver;
    protected ArrayList<JarTypeSolver> jarSolvers;

    protected CompilationUnit cu;

    protected AbstractCompiler() throws IOException {
        combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(Settings.getBasePath()));

        jarSolvers = new ArrayList<>();
        for(String jarFile : Settings.getJarFiles()) {
            JarTypeSolver jarSolver = new JarTypeSolver(jarFile);
            jarSolvers.add(jarSolver);
            combinedTypeSolver.add(jarSolver);
        }

        Object f = Settings.getProperty("finch");
        if(f != null) {
            List<String> finch = (List<String>) f;
            for(String path : finch) {
                combinedTypeSolver.add(new JavaParserTypeSolver(path));
            }
        }
        symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        this.javaParser = new JavaParser(parserConfiguration);
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


    /**
     * Creates a compilation unit from the source code at the relative path.
     *
     * If this file has previously been resolved, it will not be recompiled rather, it will be
     * fetched from the resolved map.
     * @param relativePath a path name relative to the base path of the application.
     * @throws FileNotFoundException when the source code cannot be found
     */
    public boolean compile(String relativePath) throws FileNotFoundException {
        String className = pathToClass(relativePath);

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
            if (type.isClassOrInterfaceDeclaration()) {
                if (type.asClassOrInterfaceDeclaration().isPublic()) {
                    return type;
                }
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

}
