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
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
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

        String paramOrFieldName = edge.fieldName();
        Set<String> calledMethodNames;
        MethodDeclaration beanMethod = null;
        Parameter beanParam = null;

        // Handle @Bean method edges differently
        if (edge.injectionType() == InjectionType.BEAN_METHOD) {
            // For @Bean methods, the AST node IS the MethodDeclaration
            if (!(astNode instanceof MethodDeclaration)) {
                System.out.println("❌ Expected MethodDeclaration for @Bean edge: " + edge);
                return false;
            }
            beanMethod = (MethodDeclaration) astNode;

            // Find the specific parameter
            beanParam = beanMethod.getParameters().stream()
                    .filter(p -> p.getNameAsString().equals(paramOrFieldName))
                    .findFirst()
                    .orElse(null);

            if (beanParam == null) {
                System.out.println("❌ Cannot find parameter '" + paramOrFieldName + "' in @Bean method: " + edge);
                return false;
            }

            // Find methods called on the parameter in the @Bean method body
            calledMethodNames = findCalledMethodsInMethod(beanMethod, paramOrFieldName);

            if (calledMethodNames.isEmpty()) {
                // For @Bean methods, the parameter is often just passed to constructor
                // In this case, we create an interface with all public methods
                System.out.println(
                        "ℹ️  Parameter '" + paramOrFieldName + "' passed to constructor, using all public methods");
                calledMethodNames = getAllPublicMethodNames(targetClass);
            }
        } else {
            // For field/setter/constructor edges, find methods called on the field
            calledMethodNames = findCalledMethods(callerClass, paramOrFieldName);
        }

        if (calledMethodNames.isEmpty()) {
            System.out.println("⚠️  No methods to extract for '" + paramOrFieldName + "'");
            return false;
        }

        System.out.println("📊 Methods for interface from " + paramOrFieldName + ": " + calledMethodNames);

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
        if (edge.injectionType() == InjectionType.BEAN_METHOD && beanParam != null) {
            // For @Bean method, change the parameter type
            changeParameterType(beanParam, interfaceName);
        } else {
            // For field, change the field type
            changeFieldType(callerClass, paramOrFieldName, interfaceName);
        }

        // Step 6: Find all classes that use targetBean as dependency and update their
        // types
        // This is crucial for @Bean cycles to truly break the compile-time dependency
        updateDependencyUsages(edge.targetBean(), interfaceName);

        // Track modified CUs
        callerClass.findCompilationUnit().ifPresent(modifiedCUs::add);
        targetClass.findCompilationUnit().ifPresent(modifiedCUs::add);

        System.out.println("✅ Extracted interface " + interfaceName + " for " + edge);
        return true;
    }

    /**
     * Find all classes that have a dependency on the target bean and update their
     * field/constructor types to use the interface instead of concrete class.
     */
    private void updateDependencyUsages(String targetBeanFqn, String interfaceName) {
        String simpleClassName = getSimpleClassName(targetBeanFqn);

        // Search all compilation units for usages
        for (CompilationUnit cu : AntikytheraRunTime.getResolvedCompilationUnits().values()) {
            for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                boolean modified = false;

                // Update field types
                for (FieldDeclaration field : clazz.getFields()) {
                    for (VariableDeclarator var : field.getVariables()) {
                        if (var.getTypeAsString().equals(simpleClassName)) {
                            var.setType(new ClassOrInterfaceType(null, interfaceName));
                            System.out.println("   Updated field type in " + clazz.getNameAsString() + "."
                                    + var.getNameAsString());
                            modified = true;
                        }
                    }
                }

                // Update constructor parameters
                clazz.getConstructors().forEach(ctor -> {
                    ctor.getParameters().forEach(param -> {
                        if (param.getTypeAsString().equals(simpleClassName)) {
                            param.setType(new ClassOrInterfaceType(null, interfaceName));
                            System.out.println("   Updated constructor param in " + clazz.getNameAsString());
                        }
                    });
                });

                if (modified) {
                    modifiedCUs.add(cu);
                }
            }
        }
    }

    /**
     * Find methods called on a parameter within a method body.
     */
    private Set<String> findCalledMethodsInMethod(MethodDeclaration method, String paramName) {
        Set<String> calledMethods = new HashSet<>();

        method.getBody().ifPresent(body -> {
            body.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr mce, Void arg) {
                    super.visit(mce, arg);
                    if (mce.getScope().isPresent()) {
                        String scopeStr = mce.getScope().get().toString();
                        if (scopeStr.equals(paramName)) {
                            calledMethods.add(mce.getNameAsString());
                        }
                    }
                }
            }, null);
        });

        return calledMethods;
    }

    /**
     * Get all public method names from a class.
     */
    private Set<String> getAllPublicMethodNames(ClassOrInterfaceDeclaration classDecl) {
        return classDecl.getMethods().stream()
                .filter(MethodDeclaration::isPublic)
                .map(m -> m.getNameAsString())
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Change the type of a parameter (for @Bean method edges).
     */
    private void changeParameterType(Node paramNode, String interfaceName) {
        if (paramNode instanceof Parameter param) {
            param.setType(new ClassOrInterfaceType(null, interfaceName));
            System.out.println("   Changed parameter type to " + interfaceName);
        }
    }

    /**
     * Find all methods called on a field in the class.
     */
    private Set<String> findCalledMethods(ClassOrInterfaceDeclaration classDecl, String fieldName) {
        Set<String> calledMethods = new HashSet<>();

        classDecl.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr mce, Void arg) {
                super.visit(mce, arg);

                // Check if call is on the field: fieldName.methodName()
                if (mce.getScope().isPresent()) {
                    String scopeStr = mce.getScope().get().toString();
                    if (scopeStr.equals(fieldName)) {
                        calledMethods.add(mce.getNameAsString());
                    }
                }
            }
        }, null);

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
     */
    private void changeFieldType(ClassOrInterfaceDeclaration callerClass, String fieldName,
            String interfaceName) {
        for (FieldDeclaration field : callerClass.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                if (var.getNameAsString().equals(fieldName)) {
                    var.setType(new ClassOrInterfaceType(null, interfaceName));
                    System.out.println("   Changed field type to " + interfaceName + " in " +
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
