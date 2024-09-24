package com.cloud.api.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;


public class ClassProcessor extends AbstractCompiler {
    /*
     * The strategy followed is that we iterate through all the fields in the
     * class and add them to a queue. Then we iterate through the items in
     * the queue and call the copy method on each one. If an item has already
     * been copied, it will be in the resolved set that is defined in the
     * parent, so we will skip it.
     */
    protected final Set<String> dependencies = new HashSet<>();
    final Set<String> externalDependencies = new HashSet<>();
    static final Set<String> copied = new HashSet<>();

    protected ClassProcessor() throws IOException {
        super();
    }

    /**
     * Copy a dependency from the application under test.
     *
     * @param nameAsString a fully qualified class name
     */
    protected void copyDependencies(String nameAsString) throws IOException {
        if (nameAsString.endsWith("SUCCESS") || nameAsString.startsWith("org.springframework") || externalDependencies.contains(nameAsString)) {
            return;
        }
        if (!copied.contains(nameAsString) && nameAsString.startsWith(AbstractCompiler.basePackage)) {
            copied.add(nameAsString);

            DTOHandler handler = new DTOHandler();
            handler.copyDTO(classToPath(nameAsString));

            AbstractCompiler.resolved.put(nameAsString, handler.getCompilationUnit());

        }
    }

    /**
     * Find depedencies given a type
     *
     * For each type we encounter, we need to figure out if it's something from the java
     * packages, an external dependency or something from the application under test.
     *
     * If it's a DTO in the AUT, we may need to copy it as well. Those that are identified
     * as being local dependencies in the AUT are added to the dependencies set. Those are
     * destined to be copied once parsing the controller has been completed.
     *
     * Types that are found in external jars are added to the externalDependencies set.
     * These are not copied across with the generated tests.
     *
     * @param type the type to resolve
     * @param dependencyCu the compilation unit inside which the type was encountered.
     */
    void solveTypeDependencies(Type type, CompilationUnit dependencyCu)  {

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();

            String mainType = classType.getNameAsString();
            NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

            if("DateScheduleUtil".equals(mainType) || "Logger".equals(mainType)) {
                /*
                 * Absolutely no reason for a DTO to have DateScheduleUtil or Logger as a dependency.
                 */

                return;
            }
            if (secondaryType != null) {
                for (Type t : secondaryType) {
                    // todo find out the proper way to indentify Type parameters like List<T>
                    if(t.asString().length() != 1 ) {
                        solveTypeDependencies(t, dependencyCu);
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

    /**
     * Resolves an import.
     *
     * @param dependencyCu the compilation unit with the imports
     * @param mainType the data type to search for. This is not going to be a fully qualified class name.
     * @return true if a matching import was found.
     */
    protected boolean findImport(CompilationUnit dependencyCu, String mainType) {
        /*
         * Iterates through the imports declared in the compilation unit to see if any match.
         * if an import is found it's added to the dependency list but we also need to check
         * whether the import comes from an external jar file. In that case we do not need to
         * copy the DTO across with the generated tests
         */
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

    /**
     * Converts a class name to an instance name.
     * The usually convention. If we want to create an instance of List that variable is usually
     * called 'list'
     * @param cdecl type declaration
     * @return a variable name as a string
     */
    protected static String classToInstanceName(TypeDeclaration<?> cdecl) {
        return classToInstanceName(cdecl.getNameAsString());
    }

    /**
     * Converts a class name to an instance name.
     * @param className as a string
     * @return a variable name as a string
     */
    protected static String classToInstanceName(String className) {
        String name = Character.toLowerCase(className.charAt(0)) + className.substring(1);
        if(name.equals("long") || name.equals("int")) {
            return "_" + name;
        }
        return name;
    }

    /**
     * Finds all the classes in a package with in the application under test.
     * We do not search jars, external dependencies or the java standard library.
     *
     * @param packageName the package name
     * @return a set of fully qualified class names
     */
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

    /**
     * Expands wild card imports.
     * Which means we delete the asterisk import and add all the classes in the package as
     * individual imports.
     *
     * @param cu the compilation unit
     */
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
        imports.removeIf(importDeclaration -> {
        String nameAsString = importDeclaration.getNameAsString();
        return !(dependencies.contains(nameAsString) ||
                 externalDependencies.contains(nameAsString) ||
                 nameAsString.contains("lombok") ||
                 nameAsString.startsWith("java.") ||
                 nameAsString.startsWith("com.fasterxml.jackson") ||
                nameAsString.startsWith("org.springframework.util") ||
                 (importDeclaration.isStatic() && nameAsString.contains("constants.")));
        });
    }


    public CompilationUnit getCompilationUnit() {
        return cu;
    }

    public void setCompilationUnit(CompilationUnit cu) {
        this.cu = cu;
    }
}
