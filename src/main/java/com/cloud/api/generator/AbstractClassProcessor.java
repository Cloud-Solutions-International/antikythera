package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class AbstractClassProcessor {

    /*
     * this is made static because multiple classes may have the same dependency
     * and we don't want to spend time copying them multiple times.
     */
    protected static final Map<String, CompilationUnit> resolved = new HashMap();
    protected static String basePackage;
    protected static String basePath;
    public static final String SUFFIX = ".java";

    protected JavaParser javaParser;
    protected JavaSymbolSolver symbolResolver;
    protected CombinedTypeSolver combinedTypeSolver;
    protected ArrayList<JarTypeSolver> jarSolvers;

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
}
