package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.types.ResolvedType;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.constants.Constants;
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
 * Sets up the Java Parser and maintains a cache of the clases that have been compiled.
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
     *
     * Many of the fields are static, naturally indicating that they should be shared
     * amongst all instances of the class. Others like the ComppilationUnit property
     * are specific to each instance.
     */

    private static final Logger logger = LoggerFactory.getLogger(AbstractCompiler.class);

    /**
     * The base package for the AUT.
     * It helps to identify if a class we are looking at is something we should
     * try to compile or not.
     */
    protected static String basePackage;
    /**
     * the top level folder for the AUT source code.
     * If there is a java class without a package it should be in this folder.
     */
    protected static String basePath;

    public static final String SUFFIX = ".java";

    protected JavaParser javaParser;
    protected JavaSymbolSolver symbolResolver;
    protected CombinedTypeSolver combinedTypeSolver;
    protected ArrayList<JarTypeSolver> jarSolvers;

    protected CompilationUnit cu;

    protected AbstractCompiler() throws IOException {
        if(basePackage == null || basePath == null) {
            basePackage = Settings.getProperty(Constants.BASE_PACKAGE).toString();
            basePath = Settings.getProperty(Constants.BASE_PATH).toString();
        }
        combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(basePath));

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

    public static String classToPath(String className) {
        if(className.endsWith(SUFFIX)) {
            className = className.replace(SUFFIX, "");
        }

        String path = className.replace(".", "/");
        return path + SUFFIX;
    }

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
    public void compile(String relativePath) throws FileNotFoundException {
        String className = pathToClass(relativePath);

        cu = AntikytheraRunTime.getCompilationUnit(className);
        if (cu != null) {
            return;
        }

        logger.debug("\t{}", relativePath);
        Path sourcePath = Paths.get(basePath, relativePath);

        File file = sourcePath.toFile();

        // Proceed with parsing the controller file
        FileInputStream in = new FileInputStream(file);
        cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        AntikytheraRunTime.addClass(className, cu);

    }

    /**
     * Build a map of all the fields in a class by field name.
     * Warning: Most of the time you should use a Visitor instead of this method
     *
     * @param cu Compilation unit
     * @String className
     * @return
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

    public static String getParamName(Parameter param) {
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

    public static Optional<ResolvedType> resolveTypeSafely(ClassOrInterfaceType node) {
        Optional<CompilationUnit> compilationUnit = node.findCompilationUnit();
        if (compilationUnit.isPresent()) {
            try {
                return Optional.of(node.resolve());
            } catch (Exception e) {
                // Handle the exception or log it
                logger.info("Error resolving type: {}", node.toString());
            }
        }
        return Optional.empty();
    }

    public CompilationUnit getCompilationUnit() {
        return cu;
    }

}
