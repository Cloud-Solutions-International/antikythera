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
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.TypeParameter;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

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
public class InterfaceExtractionStrategy extends AbstractExtractionStrategy {

    private static final String INTERFACE_PREFIX = "I";

    private final Map<String, CompilationUnit> generatedInterfaces = new HashMap<>();

    public InterfaceExtractionStrategy() {
        super();
    }

    public InterfaceExtractionStrategy(boolean dryRun) {
        super(dryRun);
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
            return false;
        }

        // Get the caller class
        Optional<ClassOrInterfaceDeclaration> callerOpt = astNode.findAncestor(ClassOrInterfaceDeclaration.class);
        if (callerOpt.isEmpty()) {
            return false;
        }
        ClassOrInterfaceDeclaration callerClass = callerOpt.get();

        // Get the target class
        ClassOrInterfaceDeclaration targetClass = findClassDeclaration(edge.targetBean());
        if (targetClass == null) {
            return false;
        }

        // Only handle field/setter/constructor injection
        // @Bean method cycles should use MethodExtractionStrategy instead
        if (edge.injectionType() == InjectionType.BEAN_METHOD) {
            return false;
        }

        String fieldName = edge.fieldName();

        // Find methods called on the dependency field
        Set<String> calledMethodNames = findCalledMethods(callerClass, fieldName);

        if (calledMethodNames.isEmpty()) {
            return false;
        }

        // Step 2: Resolve method signatures from target class
        Set<MethodDeclaration> usedMethods = resolveMethodSignatures(targetClass, calledMethodNames);
        if (usedMethods.isEmpty()) {
            return false;
        }

        // Step 3: Generate interface or update existing one
        String interfaceName = INTERFACE_PREFIX + AbstractCompiler.fullyQualifiedToShortName(edge.targetBean());
        String targetPackage = getPackageName(edge.targetBean());
        
        CompilationUnit interfaceCU;
        if (generatedInterfaces.containsKey(edge.targetBean())) {
            interfaceCU = generatedInterfaces.get(edge.targetBean());
            updateInterface(interfaceCU, usedMethods, targetClass);
        } else {
            interfaceCU = generateInterface(interfaceName, targetPackage, usedMethods, targetClass);
            generatedInterfaces.put(edge.targetBean(), interfaceCU);
        }

        // Step 4: Modify target class to implement interface
        addImplementsClause(targetClass, interfaceName);

        // Step 5: Modify caller to use interface type
        changeFieldType(callerClass, fieldName, interfaceName);

        // Track modified CUs
        callerClass.findCompilationUnit().ifPresent(modifiedCUs::add);
        targetClass.findCompilationUnit().ifPresent(modifiedCUs::add);

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
                if (isMethodCallOnField(mce, fieldName)) {
                    calledMethods.add(mce.getNameAsString());
                }
            });
        }

        return calledMethods;
    }

    /**
     * Resolve method signatures from the target class.
     */
    private Set<MethodDeclaration> resolveMethodSignatures(ClassOrInterfaceDeclaration targetClass,
            Set<String> methodNames) {
        Set<MethodDeclaration> methods = new HashSet<>();

        for (String methodName : methodNames) {
            // Include ALL public methods with the matching name to handle overloads
            // This ensures we capture the specific version used by the caller,
            // and maybe others, but that's safe for an interface.
            targetClass.getMethodsByName(methodName).stream()
                    .filter(MethodDeclaration::isPublic)
                    .forEach(methods::add);
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

        // Copy imports from the target class to ensure types in method signatures resolve
        targetClass.findCompilationUnit().ifPresent(sourceCu ->
                sourceCu.getImports().forEach(cu::addImport)
        );

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

        addMethodsToInterface(iface, methods, targetClass);

        return cu;
    }

    private void updateInterface(CompilationUnit cu, Set<MethodDeclaration> methods, ClassOrInterfaceDeclaration targetClass) {
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(iface ->
            addMethodsToInterface(iface, methods, targetClass)
        );
        
        // Also merge imports? imports are set on CU.
        // We already copied imports from targetClass when creating.
        // Since targetClass is the same, imports should be same.
        // But if we had multiple target classes (not possible, targetBean is unique key), it would matter.
        // So imports are fine.
    }

    private void addMethodsToInterface(ClassOrInterfaceDeclaration iface, Set<MethodDeclaration> methods, ClassOrInterfaceDeclaration targetClass) {
        for (MethodDeclaration method : methods) {
            // Check if method already exists in interface
            boolean exists = iface.getMethods().stream().anyMatch(m -> 
                m.getNameAsString().equals(method.getNameAsString()) &&
                m.getParameters().toString().equals(method.getParameters().toString()) // Simple signature check
            );
            
            if (exists) continue;

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
            
            // Qualify inner class types in signatures
            qualifyInnerTypes(sig, targetClass);

            iface.addMember(sig);
        }
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
        }
    }

    /**
     * Change the field type to the interface.
     * Preserves generic type arguments if present.
     */
    private void changeFieldType(ClassOrInterfaceDeclaration callerClass, String fieldName,
            String interfaceName) {
        for (FieldDeclaration field : callerClass.getFields()) {
            for (VariableDeclarator variable : field.getVariables()) {
                if (variable.getNameAsString().equals(fieldName)) {
                    Type originalType = variable.getType();
                    ClassOrInterfaceType newType = new ClassOrInterfaceType(null, interfaceName);

                    // Preserve type arguments if the original type is generic
                    if (originalType.isClassOrInterfaceType()) {
                        originalType.asClassOrInterfaceType().getTypeArguments()
                                .ifPresent(newType::setTypeArguments);
                    }

                    variable.setType(newType);
                    return;
                }
            }
        }
    }

    /**
     * Get package name from fully qualified name.
     */
    private String getPackageName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }

    @Override
    public void writeChanges(String basePath) throws IOException {
        super.writeChanges(basePath);

        if (dryRun) {
            return;
        }

        for (Map.Entry<String, CompilationUnit> entry : generatedInterfaces.entrySet()) {
            CompilationUnit cu = entry.getValue();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString().replace('.', '/'))
                    .orElse("");
            String interfaceName = cu.findFirst(ClassOrInterfaceDeclaration.class)
                    .map(NodeWithSimpleName::getNameAsString)
                    .orElse("Unknown");
            Path filePath = Path.of(basePath, packageName, interfaceName + ".java");
            CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
        }
    }

    public Map<String, CompilationUnit> getGeneratedInterfaces() {
        return generatedInterfaces;
    }
}
