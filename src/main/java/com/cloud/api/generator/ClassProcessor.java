package com.cloud.api.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
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
    protected static Properties props = new Properties();
    protected final Set<String> dependencies = new TreeSet<>();

    private static final Set<String> exclude = new HashSet<>();
    static {
        exclude.add("List");
        exclude.add("Set");
    }

    protected static void removeUnwantedImports(NodeList<ImportDeclaration> imports) {
        imports.removeIf(
                importDeclaration -> ! (importDeclaration.getNameAsString().startsWith(basePackage) ||
                        importDeclaration.getNameAsString().startsWith("java.") ||
                        importDeclaration.getNameAsString().startsWith("org.springframework.data.domain"))
        );
    }


    /**
     * Copy a dependency from the application under test.
     *
     * @param nameAsString
     */
    protected void copyDependencies(String nameAsString) throws IOException {
        if(nameAsString.startsWith("org.springframework.data.domain")) {
            return;
        }
        if (!ClassProcessor.resolved.contains(nameAsString) && nameAsString.startsWith(ClassProcessor.basePackage)) {
            ClassProcessor.resolved.add(nameAsString);
            DTOHandler handler = new DTOHandler();
            handler.copyDTO(nameAsString.replace(".", "/") + ClassProcessor.SUFFIX);
        }
    }

    protected static void loadConfigMap() throws IOException {
        try (FileInputStream fis = new FileInputStream("src/main/resources/generator.cfg")) {
            props.load(fis);
            String userDir = System.getProperty("user.home");
            basePath = props.getProperty("BASE_PATH");
            if (basePath != null) {
                basePath = basePath.replace("{$USERDIR}", userDir);
                props.setProperty("BASE_PATH", basePath);
            }
            basePackage = props.getProperty("BASE_PACKAGE");
            if (props.getProperty("OUTPUT_PATH") != null) {
                props.setProperty("OUTPUT_PATH",
                        props.getProperty("OUTPUT_PATH").replace("{$USERDIR}", userDir));

            }
        }
    }

    protected void extractComplexType(Type type, CompilationUnit dependencyCu) throws IOException {

        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType classType = type.asClassOrInterfaceType();
            if(classType.toString().equals("Sort.Direction")) {
                // todo remove thsi hack.
                dependencies.add("org.springframework.data.domain.Sort");
                return;
            }
            String mainType = classType.getNameAsString();
            NodeList<Type> secondaryType = classType.getTypeArguments().orElse(null);

            if(mainType != null && mainType.equals("DateScheduleUtil")) {
                /*
                 * Absolutely no reason for a DTO to have DateScheduleUtil as a dependency.
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
                PackageDeclaration pd = dependencyCu.getPackageDeclaration().orElseGet(null);
                String packageName = pd.getNameAsString();
                try {
                    if (!classType.resolve().describe().startsWith("java.")) {
                        dependencies.add(classType.resolve().describe());
                    }
                } catch (UnsolvedSymbolException e) {
                    if(!exclude.contains(mainType)) {
                        dependencies.add(packageName + "." + mainType);
                    }
                }
            }
        }
    }

    protected boolean findImport(CompilationUnit dependencyCu, String mainType) {
        for (var ref2 : dependencyCu.getImports()) {
            String[] parts = ref2.getNameAsString().split("\\.");

            if (parts[parts.length - 1].equals(mainType)) {
                dependencies.add(ref2.getNameAsString() );
                return true;
            }
        }
        return false;
    }

    protected static String classToInstanceName(TypeDeclaration<?> cdecl) {
        return classToInstanceName(cdecl.getNameAsString());
    }

    protected static String classToInstanceName(String className) {
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
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
