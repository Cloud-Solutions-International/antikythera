package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ClassProcessor {

    protected static final Set<String> resolved = new HashSet<>();
    protected static String basePackage;
    protected static String basePath;
    public static final String SUFFIX = ".java";

    protected final Set<String> dependencies = new HashSet<>();
    protected final Set<String> externalDependencies = new HashSet<>();

    protected JavaParser javaParser;
    protected JavaSymbolSolver symbolResolver;
    protected CombinedTypeSolver combinedTypeSolver;
    protected ArrayList<JarTypeSolver> jarSolvers;

    protected ClassProcessor() throws IOException {
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
        // Log to check symbol resolution
        Log.setAdapter(new Log.StandardOutStandardErrorAdapter());
    }

    protected void copyDependencies(String nameAsString) throws IOException {
        if (nameAsString.endsWith("SUCCESS") || nameAsString.startsWith("org.springframework") || externalDependencies.contains(nameAsString)) {
            return;
        }
        if (!ClassProcessor.resolved.contains(nameAsString) && nameAsString.startsWith(ClassProcessor.basePackage)) {
            ClassProcessor.resolved.add(nameAsString);
            DTOHandler handler = new DTOHandler();
            handler.copyDTO(nameAsString.replace(".", "/") + ClassProcessor.SUFFIX);
        }
    }

    protected void extractComplexType(Type type, CompilationUnit dependencyCu)  {
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();

            String mainType = classType.getNameAsString();
            NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

            if("DateScheduleUtil".equals(mainType) || "Logger".equals(mainType)) {
                return;
            }
            if (secondaryType != null) {
                for (Type t : secondaryType) {
                    if(t.asString().length() != 1 ) {
                        extractComplexType(t, dependencyCu);
                    }
                }
            }
            else {
                try {
                    String description = classType.resolve().describe();
                    if (!description.startsWith("java.")) {
                        for (var jarSolver : jarSolvers) {
                            if(jarSolver.getKnownClasses().contains(description)) {
                                externalDependencies.add(description);
                                return;
                            }
                        }
                        dependencies.add(description);
                    }
                } catch (UnsolvedSymbolException e) {
                    findImport(dependencyCu, mainType);
                }
            }
        }
    }

    protected boolean findImport(CompilationUnit dependencyCu, String mainType) {
        for (var ref2 : dependencyCu.getImports()) {
            String[] parts = ref2.getNameAsString().split("\\.");
            if (parts[parts.length - 1].equals(mainType)) {
                dependencies.add(ref2.getNameAsString());
                for (var jarSolver : jarSolvers) {
                    if(jarSolver.getKnownClasses().contains(ref2.getNameAsString())) {
                        externalDependencies.add(ref2.getNameAsString());
                    }
                }
                return true;
            }
        }
        return false;
    }

    protected static String classToInstanceName(TypeDeclaration<?> cdecl) {
        return classToInstanceName(cdecl.getNameAsString());
    }

    protected static String classToInstanceName(String className) {
        String name = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        if(name.equals("long") || name.equals("int")) {
            return "_" + name;
        }
        return name;
    }

    protected Set<String> findMatchingClasses(String packageName) {
        Set<String> matchingClasses = new HashSet<>();
        Path p = Paths.get(basePath, packageName.replace(".", "/"));
        File directory = p.toFile();

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    String fname = f.getName();
                    if (fname.endsWith(ClassProcessor.SUFFIX)) {
                        String imp = packageName + "." + fname.substring(0, fname.length() - 5);
                        matchingClasses.add(imp);
                    }
                }
            }
        }
        return matchingClasses;
    }

    protected void expandWildCards(CompilationUnit cu) {
        Set<String> wildCards = new HashSet<>();
        for(var imp : cu.getImports()) {
            if(imp.isAsterisk() && !imp.isStatic()) {
                String packageName = imp.getNameAsString();
                if (packageName.startsWith(basePackage)) {
                    wildCards.addAll(findMatchingClasses(packageName));
                }
            }
        }
        for(String s : wildCards) {
            ImportDeclaration impl = new ImportDeclaration(s, false, true);
            cu.addImport(impl);
            impl.setAsterisk(false);
        }
    }

    protected void removeUnusedImports(NodeList<ImportDeclaration> imports) {
        imports.removeIf(
                importDeclaration -> {
                    String nameAsString = importDeclaration.getNameAsString();
                    return (
                            !dependencies.contains(nameAsString) &&
                                    !externalDependencies.contains(nameAsString) &&
                                    !nameAsString.contains("lombok") &&
                                    !nameAsString.startsWith("java.") &&
                                    !(importDeclaration.isStatic() && nameAsString.contains("constants."))
                    );
                }
        );
    }
}
