package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.constants.Constants;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class ClassProcessor {

    /*
     * this is made static because multiple classes may have the same dependency
     * and we don't want to spend time copying them multiple times.
     */
    protected static final Set<String> resolved = new TreeSet<>();
    protected static String basePackage;
    protected static String basePath;
    public static final String SUFFIX = ".java";

    protected final Set<String> dependencies = new TreeSet<>();

    /*
     * The strategy followed is that we iterate through all the fields in the
     * class and add them to a queue. Then we iterate through the items in
     * the queue and call the copy method on each one. If an item has already
     * been copied, it will be in the resolved set that is defined in the
     * parent, so we will skip it.
     */
    protected JavaParser javaParser;
    protected JavaSymbolSolver symbolResolver;
    protected CombinedTypeSolver combinedTypeSolver;

    protected static void removeUnwantedImports(NodeList<ImportDeclaration> imports) {
        imports.removeIf(
                importDeclaration -> ! (importDeclaration.getNameAsString().startsWith(basePackage) ||
                        importDeclaration.getNameAsString().startsWith("java."))
        );
    }

    protected ClassProcessor() throws IOException {
        if(basePackage == null) {
            basePackage = Settings.getProperty(Constants.BASE_PACKAGE).toString();
            basePath = Settings.getProperty(Constants.BASE_PATH).toString();
        }
        combinedTypeSolver = new CombinedTypeSolver();
        combinedTypeSolver.add(new ReflectionTypeSolver());
        combinedTypeSolver.add(new JavaParserTypeSolver(basePath));

        for(String jarFile : Settings.getJarFiles()) {
            combinedTypeSolver.add(new JarTypeSolver(jarFile));
        }
        symbolResolver = new JavaSymbolSolver(combinedTypeSolver);
        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(symbolResolver);
        this.javaParser = new JavaParser(parserConfiguration);
    }

    /**
     * Copy a dependency from the application under test.
     *
     * @param nameAsString
     */
    protected void copyDependencies(String nameAsString) throws IOException {
        if(nameAsString.startsWith("org.springframework")) {
            return;
        }
        if (!ClassProcessor.resolved.contains(nameAsString) && nameAsString.startsWith(ClassProcessor.basePackage)) {
            ClassProcessor.resolved.add(nameAsString);
            DTOHandler handler = new DTOHandler();
            handler.copyDTO(nameAsString.replace(".", "/") + ClassProcessor.SUFFIX);
        }
    }

    protected void extractComplexType(Type type, CompilationUnit dependencyCu) throws IOException {

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();

            String mainType = classType.getNameAsString();
            NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

            if(mainType != null &&
                    (mainType.equals("DateScheduleUtil") || mainType.equals("Logger"))) {
                /*
                 * Absolutely no reason for a DTO to have DateScheduleUtil or Logger as a dependency.
                 */

                return;
            }
            if (secondaryType != null) {
                for (Type t : secondaryType) {
                    // todo find out the proper way to indentify Type parameters like List<T>
                    if(t.asString().length() != 1 ) {
                        extractComplexType(t, dependencyCu);
                    }
                }
            }

            boolean found = findImport(dependencyCu, mainType);

            if (!found ) {
                try {
                    if (!classType.resolve().describe().startsWith("java.")) {
                        dependencies.add(classType.resolve().describe());
                    }
                } catch (UnsolvedSymbolException e) {
                    // ignore for now
                }
            }
        }
    }

    protected boolean findImport(CompilationUnit dependencyCu, String mainType) {
        for (var ref2 : dependencyCu.getImports()) {
            String[] parts = ref2.getNameAsString().split("\\.");

            if (parts[parts.length - 1].equals(mainType)) {
                dependencies.add(ref2.getNameAsString());
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
        for (File f : p.toFile().listFiles()) {
            String fname = f.getName();
            if (fname.endsWith(ClassProcessor.SUFFIX)) {
                String imp = packageName + "." + fname.substring(0, fname.length() - 5);
                matchingClasses.add(imp);
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
            // setting asterisk as true and then switching it off is to overcome a bug in javaparser
            ImportDeclaration impl = new ImportDeclaration(s, false, true);
            cu.addImport(impl);
            impl.setAsterisk(false);
        }
    }

    protected void removeUnusedImports(NodeList<ImportDeclaration> imports) {
        imports.removeIf(
                importDeclaration -> !(importDeclaration.isAsterisk() || importDeclaration.isStatic()
                        || dependencies.contains(importDeclaration.getNameAsString())
                        || importDeclaration.getNameAsString().startsWith("java.")
                        || (importDeclaration.getNameAsString().startsWith(basePackage) && dependencies.contains(importDeclaration.getNameAsString()))
                        || importDeclaration.getNameAsString().startsWith("lombok."))
        );
    }
}
