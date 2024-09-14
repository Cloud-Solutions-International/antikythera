package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import java.io.IOException;
import java.util.Optional;
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

    /**
     * This is the set of imports.
     */
    protected final Set<String> dependencies = new TreeSet<>();

    protected static void removeUnwantedImports(NodeList<ImportDeclaration> imports) {
        imports.removeIf(
                importDeclaration -> ! (importDeclaration.getNameAsString().startsWith(basePackage) ||
                        importDeclaration.getNameAsString().startsWith("java."))
        );
    }

    protected ClassProcessor() {
        if(basePath == null) {
            basePath = Settings.getProperty("BASE_PATH");
        }
        if(basePackage == null) {
            basePackage = Settings.getProperty("BASE_PACKAGE");
        }
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

    protected void extractComplexType(Type type) {

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();

            String mainType = classType.getNameAsString();
            Optional<NodeList<Type>> types = classType.getTypeArguments();

            if(mainType != null &&
                    (mainType.equals("DateScheduleUtil") || mainType.equals("Logger"))) {
                /*
                 * Absolutely no reason for a DTO to have DateScheduleUtil or Logger as a dependency.
                 */

                return;
            }

            if(types.isPresent()) {
                try {
                    String[] parts = type.resolve().describe().split("<");
                    for (String part : parts) {
                        for (String s : part.split(",")) {
                            dependencies.add(s.replace(">", ""));
                        }
                    }
                } catch (UnsolvedSymbolException e) {
                    for(Type t : types.get()) {
                        extractComplexType(t);
                    }
                }
            }
            else {
                dependencies.add(type.resolve().describe());
            }
        }
    }

    /**
     * Find an import by iterating through the imports in the compilation unit.
     * Instead of using this method try to use the SymbolSolver to resolve the type.
     *
     * @param dependencyCu
     * @param mainType
     * @return
     */
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

    /**
     * ClassNames are PascalCase. We need to convert them to camelCase for variables
     * @param cdecl
     * @return
     */
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

    protected void removeUnusedImports(NodeList<ImportDeclaration> imports) {

    }
}
