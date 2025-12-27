package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Scope;
import sa.com.cloudsolutions.antikythera.evaluator.ScopeChain;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Strategy for breaking circular dependencies by extracting an interface.
 * 
 * <p>
 * This strategy works by:
 * 1. Finding all methods called on the dependency field
 * 2. Generating an interface with those method signatures
 * 3. Making the target class implement the interface
 * 4. Changing the caller's field type to the interface
 * </p>
 * 
 * <p>
 * Best for: @Bean method cycles and complex tight coupling.
 * </p>
 */
public class InterfaceExtractionStrategy {

    private static final String INTERFACE_PREFIX = "I";

    private final Set<CompilationUnit> modifiedCUs = new HashSet<>();
    private final Map<String, CompilationUnit> generatedInterfaces = new HashMap<>();
    private boolean dryRun = false;

    public InterfaceExtractionStrategy() {
    }

    public InterfaceExtractionStrategy(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Apply interface extraction to break a cycle edge.
     * 
     * @param edge The dependency edge to break
     * @return true if interface was successfully extracted
     */
    public boolean apply(BeanDependency edge) {
        Node astNode = edge.astNode();
        if (astNode == null) {
            System.out.println("❌ No AST node for edge: " + edge);
            return false;
        }

        // Get the caller class
        Optional<ClassOrInterfaceDeclaration> callerOpt = astNode.findAncestor(ClassOrInterfaceDeclaration.class);
        if (callerOpt.isEmpty()) {
            System.out.println("❌ Cannot find caller class for edge: " + edge);
            return false;
        }
        ClassOrInterfaceDeclaration callerClass = callerOpt.get();

        // Get the target class
        ClassOrInterfaceDeclaration targetClass = findTargetClass(edge.targetBean());
        if (targetClass == null) {
            System.out.println("❌ Cannot find target class: " + edge.targetBean());
            return false;
        }

        // Only handle field/setter/constructor injection
        // @Bean method cycles should use MethodExtractionStrategy instead
        if (edge.injectionType() == InjectionType.BEAN_METHOD) {
            System.out
                    .println("⚠️  Interface extraction not applicable to @Bean methods. Use MethodExtractionStrategy.");
            return false;
        }

        String fieldName = edge.fieldName();

        // Find methods called on the dependency field
        Set<String> calledMethodNames = findCalledMethods(callerClass, fieldName);

        if (calledMethodNames.isEmpty()) {
            System.out.println("⚠️  No methods to extract for '" + fieldName + "'");
            return false;
        }

        System.out.println("📊 Methods for interface from " + fieldName + ": " + calledMethodNames);

        // Step 2: Resolve method signatures from target class
        Set<MethodDeclaration> usedMethods = resolveMethodSignatures(targetClass, calledMethodNames);
        if (usedMethods.isEmpty()) {
            System.out.println("❌ Could not resolve any method signatures");
            return false;
        }

        // Step 3: Generate interface
        String interfaceName = INTERFACE_PREFIX + getSimpleClassName(edge.targetBean());
        String targetPackage = getPackageName(edge.targetBean());
        CompilationUnit interfaceCU = generateInterface(interfaceName, targetPackage, usedMethods, targetClass);
        generatedInterfaces.put(edge.targetBean(), interfaceCU);

        // Step 4: Modify target class to implement interface
        addImplementsClause(targetClass, interfaceName);

        // Step 5: Modify caller to use interface type
        changeFieldType(callerClass, fieldName, interfaceName);

        // Track modified CUs
        callerClass.findCompilationUnit().ifPresent(modifiedCUs::add);
        targetClass.findCompilationUnit().ifPresent(modifiedCUs::add);

        System.out.println("✅ Extracted interface " + interfaceName + " for " + edge);
        return true;
    }

    /**
     * Find all methods called on a field in the class using ScopeChain.
     * This leverages existing infrastructure for more accurate scope resolution.
     */
    private Set<String> findCalledMethods(ClassOrInterfaceDeclaration classDecl, String fieldName) {
        Set<String> calledMethods = new HashSet<>();

        for (MethodDeclaration method : classDecl.getMethods()) {
            method.findAll(MethodCallExpr.class).forEach(mce -> {
                ScopeChain chain = ScopeChain.findScopeChain(mce);
                if (!chain.isEmpty()) {
                    // Get the first scope element (the field name)
                    List<Scope> scopes = chain.getChain();
                    if (!scopes.isEmpty()) {
                        Scope firstScope = scopes.get(0);
                        com.github.javaparser.ast.expr.Expression expr = firstScope.getExpression();

                        // Check if it's a NameExpr matching our field
                        if (expr.isNameExpr()) {
                            String name = expr.asNameExpr().getNameAsString();
                            if (name.equals(fieldName)) {
                                calledMethods.add(mce.getNameAsString());
                            }
                        }
                        // Also handle FieldAccessExpr (this.fieldName)
                        else if (expr.isFieldAccessExpr()) {
                            FieldAccessExpr fae = expr.asFieldAccessExpr();
                            if (fae.getNameAsString().equals(fieldName)) {
                                calledMethods.add(mce.getNameAsString());
                            }
                        }
                    }
                }
            });
        }

        return calledMethods;
    }

    /**
     * Find the target class by fully qualified name.
     */
    private ClassOrInterfaceDeclaration findTargetClass(String fqn) {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqn);
        if (cu != null) {
            return cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
        }
        return null;
    }

    /**
     * Resolve method signatures from the target class.
     */
    private Set<MethodDeclaration> resolveMethodSignatures(ClassOrInterfaceDeclaration targetClass,
            Set<String> methodNames) {
        Set<MethodDeclaration> methods = new HashSet<>();

        for (String methodName : methodNames) {
            targetClass.getMethodsByName(methodName).stream()
                    .filter(m -> m.isPublic())
                    .findFirst()
                    .ifPresent(methods::add);
        }

        return methods;
    }

    /**
     * Generate an interface with the given method signatures.
     */
    private CompilationUnit generateInterface(String interfaceName, String packageName,
            Set<MethodDeclaration> methods,
            ClassOrInterfaceDeclaration targetClass) {
        CompilationUnit cu = new CompilationUnit();

        if (packageName != null && !packageName.isEmpty()) {
            cu.setPackageDeclaration(packageName);
        }

        ClassOrInterfaceDeclaration iface = cu.addInterface(interfaceName);
        iface.setModifier(Modifier.Keyword.PUBLIC, true);

        // Copy type parameters from target class (for generics)
        if (!targetClass.getTypeParameters().isEmpty()) {
            NodeList<TypeParameter> typeParams = new NodeList<>();
            for (TypeParameter tp : targetClass.getTypeParameters()) {
                typeParams.add(tp.clone());
            }
            iface.setTypeParameters(typeParams);
        }

        // Add method signatures
        for (MethodDeclaration method : methods) {
            MethodDeclaration sig = new MethodDeclaration();
            sig.setName(method.getNameAsString());
            sig.setType(method.getType().clone());
            sig.setModifier(Modifier.Keyword.PUBLIC, true);

            // Clone parameters
            NodeList<Parameter> params = new NodeList<>();
            for (Parameter p : method.getParameters()) {
                params.add(p.clone());
            }
            sig.setParameters(params);

            // Clone thrown exceptions
            NodeList<com.github.javaparser.ast.type.ReferenceType> thrownExceptions = new NodeList<>();
            for (com.github.javaparser.ast.type.ReferenceType thrown : method.getThrownExceptions()) {
                thrownExceptions.add(thrown.clone());
            }
            sig.setThrownExceptions(thrownExceptions);

            // Interface methods have no body
            sig.removeBody();

            iface.addMember(sig);
        }

        System.out.println("📝 Generated interface " + interfaceName + " with " + methods.size() + " method(s)");
        return cu;
    }

    /**
     * Add implements clause to target class.
     */
    private void addImplementsClause(ClassOrInterfaceDeclaration targetClass, String interfaceName) {
        // Check if already implements
        boolean alreadyImplements = targetClass.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals(interfaceName));

        if (!alreadyImplements) {
            // If target class has generics, interface should too
            if (!targetClass.getTypeParameters().isEmpty()) {
                NodeList<Type> typeArgs = new NodeList<>();
                for (TypeParameter tp : targetClass.getTypeParameters()) {
                    typeArgs.add(new ClassOrInterfaceType(null, tp.getNameAsString()));
                }
                ClassOrInterfaceType ifaceType = new ClassOrInterfaceType(null, interfaceName);
                ifaceType.setTypeArguments(typeArgs);
                targetClass.addImplementedType(ifaceType);
            } else {
                targetClass.addImplementedType(interfaceName);
            }
            System.out.println("   Added 'implements " + interfaceName + "' to " + targetClass.getNameAsString());
        }
    }

    /**
     * Change the field type to the interface.
     * Preserves generic type arguments if present.
     */
    private void changeFieldType(ClassOrInterfaceDeclaration callerClass, String fieldName,
            String interfaceName) {
        for (FieldDeclaration field : callerClass.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                if (var.getNameAsString().equals(fieldName)) {
                    Type originalType = var.getType();
                    ClassOrInterfaceType newType = new ClassOrInterfaceType(null, interfaceName);

                    // Preserve type arguments if the original type is generic
                    if (originalType.isClassOrInterfaceType()) {
                        originalType.asClassOrInterfaceType().getTypeArguments()
                                .ifPresent(newType::setTypeArguments);
                    }

                    var.setType(newType);
                    System.out.println("   Changed field type to " + newType + " in " +
                            callerClass.getNameAsString());
                    return;
                }
            }
        }
    }

    /**
     * Get simple class name from fully qualified name.
     */
    private String getSimpleClassName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }

    /**
     * Get package name from fully qualified name.
     */
    private String getPackageName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }

    /**
     * Write all modified files to disk.
     */
    public void writeChanges(String basePath) throws IOException {
        if (dryRun) {
            System.out.println("\n🔍 Dry run - " + modifiedCUs.size() + " file(s) would be modified, " +
                    generatedInterfaces.size() + " interface(s) would be generated");
            return;
        }

        System.out.println("\n📝 Writing " + modifiedCUs.size() + " modified file(s)...");
        for (CompilationUnit cu : modifiedCUs) {
            if (cu.getStorage().isPresent()) {
                Path filePath = cu.getStorage().get().getPath();
                CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
                System.out.println("   ✓ " + filePath);
            } else {
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString().replace('.', '/'))
                        .orElse("");
                String className = cu.findFirst(ClassOrInterfaceDeclaration.class)
                        .map(c -> c.getNameAsString())
                        .orElse("Unknown");
                Path filePath = Path.of(basePath, packageName, className + ".java");
                CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
                System.out.println("   ✓ " + filePath);
            }
        }

        System.out.println("\n📝 Writing " + generatedInterfaces.size() + " generated interface(s)...");
        for (Map.Entry<String, CompilationUnit> entry : generatedInterfaces.entrySet()) {
            CompilationUnit cu = entry.getValue();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString().replace('.', '/'))
                    .orElse("");
            String interfaceName = cu.findFirst(ClassOrInterfaceDeclaration.class)
                    .map(c -> c.getNameAsString())
                    .orElse("Unknown");
            Path filePath = Path.of(basePath, packageName, interfaceName + ".java");
            CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
            System.out.println("   ✓ " + filePath + " (NEW)");
        }
    }

    public Set<CompilationUnit> getModifiedCUs() {
        return modifiedCUs;
    }

    public Map<String, CompilationUnit> getGeneratedInterfaces() {
        return generatedInterfaces;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
