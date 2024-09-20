package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
import java.util.Map;


public class AbstractClassProcessor {
    /*
     * Let's define some terms.
     * A fully qualified class name is something that looks like java.util.List or
     * org.apache.commons.lang3.StringUtils.
     *
     * A simple class name is just the class name without the package name. Which
     * means we have List and StringUtils.
     *
     * A relative path is a path that's relative to the base path of the project
     */
    /*
     * this is made static because multiple classes may have the same dependency
     * and we don't want to spend time copying them multiple times.
     */
    private static final Logger logger = LoggerFactory.getLogger(AbstractClassProcessor.class);

    /**
     * Keeps track of all the classes that we have compiled
     */
    protected static final Map<String, CompilationUnit> resolved = new HashMap();
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

    protected AbstractClassProcessor() throws IOException {
        if(basePackage == null) {
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
        return  path.replace(SUFFIX, "").replace("/", ".");
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

        cu = resolved.get(className);
        if (cu != null) {
            return;
        }

        logger.info("\t{}", relativePath);
        Path sourcePath = Paths.get(basePath, relativePath);

        // Check if the file exists
        File file = sourcePath.toFile();
        if (!file.exists()) {
            // The file may not exist if the DTO is an inner class in a controller
            logger.warn("File not found: {}. Checking if it's an inner class DTO.", sourcePath);
            // Extract the controller's name from the path and assume the DTO is an inner class in the controller
            String controllerPath = relativePath.replaceAll("/[^/]+\\.java$", ".java");  // Replaces DTO file with controller file
            sourcePath = Paths.get(basePath, controllerPath);
        }

        // Check again for the controller file
        file = sourcePath.toFile();
        if (!file.exists()) {
            logger.error("Controller file not found: {}", sourcePath);
            throw new FileNotFoundException(sourcePath.toString());
        }

        // Proceed with parsing the controller file
        FileInputStream in = new FileInputStream(file);
        cu = javaParser.parse(in).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        resolved.put(className, cu);

        // Search for any inner class that ends with "Dto"
        boolean hasInnerDTO = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .anyMatch(cls -> cls.getNameAsString().endsWith("Dto"));

        if (hasInnerDTO) {
            logger.info("Found inner DTO class in controller: {}", relativePath);
        }
    }

}
