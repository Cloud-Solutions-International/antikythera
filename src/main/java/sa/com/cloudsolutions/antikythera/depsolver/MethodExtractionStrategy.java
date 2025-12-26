package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.depsolver.GraphNode;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Scope;
import sa.com.cloudsolutions.antikythera.evaluator.ScopeChain;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strategy for breaking circular dependencies by extracting cycle-causing
 * methods
 * into a self-contained mediator class.
 * 
 * The mediator class has NO references to the original cycle classes.
 * Methods and their transitive dependencies are moved together.
 */
public class MethodExtractionStrategy {

    private final Set<CompilationUnit> modifiedCUs = new HashSet<>();
    private final Map<String, CompilationUnit> generatedClasses = new HashMap<>();
    private boolean dryRun;

    public MethodExtractionStrategy() {
        this.dryRun = false;
    }

    public MethodExtractionStrategy(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Apply method extraction to break a cycle.
     * 
     * @param cycle List of bean FQNs forming the cycle (e.g., [A, B] or [A, B, C])
     * @return true if extraction was successful
     */
    public boolean apply(List<String> cycle) {
        if (cycle == null || cycle.size() < 2) {
            return false;
        }

        // Step 1: Find cycle-causing methods in each class
        Map<String, Set<MethodDeclaration>> cycleCausingMethods = new HashMap<>();
        for (int i = 0; i < cycle.size(); i++) {
            String beanFqn = cycle.get(i);
            String dependsOn = cycle.get((i + 1) % cycle.size());

            ClassOrInterfaceDeclaration clazz = findClass(beanFqn);
            if (clazz == null)
                continue;

            Set<MethodDeclaration> methods = findMethodsUsing(clazz, getSimpleName(dependsOn));
            if (!methods.isEmpty()) {
                cycleCausingMethods.put(beanFqn, methods);
            }
        }

        if (cycleCausingMethods.isEmpty()) {
            return false;
        }

        // Step 2: Collect transitive dependencies of those methods
        Map<String, Set<MethodDeclaration>> allMethodsToMove = new HashMap<>();
        Map<String, Set<FieldDeclaration>> allFieldsToMove = new HashMap<>();

        for (Map.Entry<String, Set<MethodDeclaration>> entry : cycleCausingMethods.entrySet()) {
            String beanFqn = entry.getKey();
            ClassOrInterfaceDeclaration clazz = findClass(beanFqn);
            if (clazz == null)
                continue;

            Set<MethodDeclaration> methods = new HashSet<>(entry.getValue());
            Set<FieldDeclaration> fields = new HashSet<>();

            // Collect helper methods and fields
            collectTransitiveDependencies(clazz, methods, fields, cycle);

            allMethodsToMove.put(beanFqn, methods);
            allFieldsToMove.put(beanFqn, fields);
        }

        // Step 3: Generate self-contained mediator class
        String mediatorName = generateMediatorName(cycle);
        String mediatorPackage = getPackage(cycle.get(0));
        CompilationUnit mediatorCU = generateMediatorClass(mediatorName, mediatorPackage,
                allMethodsToMove, allFieldsToMove);
        generatedClasses.put(mediatorPackage + "." + mediatorName, mediatorCU);

        // Collect all moved method names for caller redirection
        Set<String> movedMethodNames = new HashSet<>();
        for (Set<MethodDeclaration> methods : allMethodsToMove.values()) {
            for (MethodDeclaration m : methods) {
                movedMethodNames.add(m.getNameAsString());
            }
        }

        // Step 4: Remove methods and fields from original classes
        for (String beanFqn : allMethodsToMove.keySet()) {
            ClassOrInterfaceDeclaration clazz = findClass(beanFqn);
            if (clazz == null)
                continue;

            // Remove moved methods
            for (MethodDeclaration method : allMethodsToMove.get(beanFqn)) {
                method.remove();
            }

            // Remove cycle-causing field
            removeCycleField(clazz, cycle);

            clazz.findCompilationUnit().ifPresent(modifiedCUs::add);
        }

        // Step 5: Redirect callers to use mediator (add mediator field + update calls)
        redirectCallers(cycle, mediatorName, mediatorPackage, movedMethodNames);

        return true;
    }

    /**
     * Find methods in a class that use a specific dependency.
     */
    private Set<MethodDeclaration> findMethodsUsing(ClassOrInterfaceDeclaration clazz, String dependencyName) {
        Set<MethodDeclaration> result = new HashSet<>();
        String fieldName = findFieldNameForType(clazz, dependencyName);

        if (fieldName == null)
            return result;

        for (MethodDeclaration method : clazz.getMethods()) {
            if (methodUsesField(method, fieldName)) {
                result.add(method);
            }
        }
        return result;
    }

    /**
     * Check if a method uses a specific field using ScopeChain for accurate
     * detection.
     */
    private boolean methodUsesField(MethodDeclaration method, String fieldName) {
        // Check for direct field access
        boolean usesField = method.findAll(NameExpr.class).stream()
                .anyMatch(n -> n.getNameAsString().equals(fieldName));

        if (usesField) {
            return true;
        }

        // Check for method calls on the field using ScopeChain
        return method.findAll(MethodCallExpr.class).stream()
                .anyMatch(mce -> {
                    ScopeChain chain = ScopeChain.findScopeChain(mce);
                    if (!chain.isEmpty()) {
                        List<Scope> scopes = chain.getChain();
                        if (!scopes.isEmpty()) {
                            Scope firstScope = scopes.get(0);
                            com.github.javaparser.ast.expr.Expression expr = firstScope.getExpression();

                            if (expr.isNameExpr()) {
                                return expr.asNameExpr().getNameAsString().equals(fieldName);
                            } else if (expr.isFieldAccessExpr()) {
                                FieldAccessExpr fae = expr.asFieldAccessExpr();
                                return fae.getNameAsString().equals(fieldName);
                            }
                        }
                    }
                    return false;
                });
    }

    /**
     * Find the field name for a given type in a class.
     */
    private String findFieldNameForType(ClassOrInterfaceDeclaration clazz, String typeName) {
        for (FieldDeclaration field : clazz.getFields()) {
            for (VariableDeclarator var : field.getVariables()) {
                if (var.getTypeAsString().equals(typeName)) {
                    return var.getNameAsString();
                }
            }
        }
        return null;
    }

    /**
     * Collect transitive dependencies using DependencyAnalyzer for accurate
     * dependency tracking.
     * Uses the clean collectDependencies() API for analysis-only mode.
     */
    private void collectTransitiveDependencies(ClassOrInterfaceDeclaration clazz,
            Set<MethodDeclaration> methods, Set<FieldDeclaration> fields, List<String> cycle) {

        // Create analyzer for analysis-only mode (no code generation)
        DependencyAnalyzer analyzer = new DependencyAnalyzer();

        Set<String> cycleTypes = cycle.stream()
                .map(this::getSimpleName)
                .collect(Collectors.toSet());

        // Collect all dependencies without filtering
        Set<GraphNode> deps = analyzer.collectDependencies(methods);

        // Extract helper methods and fields from discovered dependencies
        Set<String> methodNames = methods.stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toSet());

        for (GraphNode node : deps) {
            if (node.getNode() instanceof MethodDeclaration md) {
                String methodName = md.getNameAsString();
                if (!methodNames.contains(methodName)) {
                    clazz.getMethodsByName(methodName).stream()
                            .filter(m -> m.getParameters().size() == md.getParameters().size())
                            .filter(m -> !m.isStatic())
                            .forEach(m -> {
                                methods.add(m);
                                methodNames.add(methodName);
                            });
                }
            } else if (node.getNode() instanceof FieldDeclaration fd) {
                String fieldName = fd.getVariable(0).getNameAsString();
                clazz.getFieldByName(fieldName).ifPresent(f -> {
                    String type = f.getVariables().get(0).getTypeAsString();
                    // Only exclude fields of cycle types (to break the dependency)
                    if (!cycleTypes.contains(type)) {
                        fields.add(f);
                    }
                });
            }
        }

        // Create a copy to include newly added transitive methods
        Set<MethodDeclaration> allMethods = new HashSet<>(methods);

        // Also check for fields used directly in method bodies
        for (MethodDeclaration method : allMethods) {
            method.findAll(NameExpr.class).forEach(ne -> {
                String name = ne.getNameAsString();
                clazz.getFieldByName(name).ifPresent(f -> {
                    String type = f.getVariables().get(0).getTypeAsString();
                    if (!cycleTypes.contains(type)) {
                        fields.add(f);
                    }
                });
            });
        }
    }

    /**
     * Generate mediator class with moved methods and fields.
     */
    private CompilationUnit generateMediatorClass(String name, String pkg,
            Map<String, Set<MethodDeclaration>> methods, Map<String, Set<FieldDeclaration>> fields) {

        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(pkg);

        ClassOrInterfaceDeclaration mediator = cu.addClass(name, Modifier.Keyword.PUBLIC);
        mediator.addAnnotation("org.springframework.stereotype.Service");

        // Add fields (excluding cycle types)
        for (Set<FieldDeclaration> fieldSet : fields.values()) {
            for (FieldDeclaration field : fieldSet) {
                FieldDeclaration clone = field.clone();
                mediator.addMember(clone);
            }
        }

        // Add methods
        for (Set<MethodDeclaration> methodSet : methods.values()) {
            for (MethodDeclaration method : methodSet) {
                MethodDeclaration clone = method.clone();
                clone.setModifiers(Modifier.Keyword.PUBLIC);
                mediator.addMember(clone);
            }
        }

        return cu;
    }

    /**
     * Remove the field that causes the cycle.
     * Compares both FQN and simple names to handle cases where typeAsString
     * might return either format.
     */
    private void removeCycleField(ClassOrInterfaceDeclaration clazz, List<String> cycle) {
        Set<String> cycleFqns = new HashSet<>(cycle);
        Set<String> cycleSimpleNames = new HashSet<>();
        for (String c : cycle) {
            cycleSimpleNames.add(getSimpleName(c));
        }

        // Collect fields to remove (can't modify during iteration)
        List<FieldDeclaration> toRemove = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            String typeAsString = field.getVariables().get(0).getTypeAsString();
            String typeSimpleName = getSimpleName(typeAsString);

            // Check if field type matches any cycle type (FQN or simple name)
            if (cycleFqns.contains(typeAsString) ||
                    cycleSimpleNames.contains(typeAsString) ||
                    cycleSimpleNames.contains(typeSimpleName)) {
                toRemove.add(field);
            }
        }
        // Remove collected fields
        for (FieldDeclaration field : toRemove) {
            field.remove();
        }
    }

    /**
     * Redirect callers of moved methods to use the mediator class.
     * For each class in the cycle, add a mediator field and update internal calls.
     */
    private void redirectCallers(List<String> cycle, String mediatorName,
            String mediatorPackage, Set<String> movedMethodNames) {

        String mediatorFieldName = Character.toLowerCase(mediatorName.charAt(0)) +
                mediatorName.substring(1);
        String mediatorFqn = mediatorPackage + "." + mediatorName;

        // For each class that had methods moved, add mediator field
        for (String beanFqn : cycle) {
            ClassOrInterfaceDeclaration clazz = findClass(beanFqn);
            if (clazz == null)
                continue;

            // Add mediator field with @Autowired
            boolean hasMovedMethods = false;
            for (MethodDeclaration m : clazz.getMethods()) {
                for (String moved : movedMethodNames) {
                    if (methodCallsMethod(m, moved)) {
                        hasMovedMethods = true;
                        break;
                    }
                }
                if (hasMovedMethods)
                    break;
            }

            if (hasMovedMethods) {
                addMediatorField(clazz, mediatorName, mediatorFieldName);
                updateMethodCalls(clazz, movedMethodNames, mediatorFieldName);
                clazz.findCompilationUnit().ifPresent(modifiedCUs::add);
            }
        }
    }

    /**
     * Check if a method calls another method by name.
     */
    private boolean methodCallsMethod(MethodDeclaration method, String calledMethodName) {
        boolean[] calls = { false };
        method.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                if (n.getNameAsString().equals(calledMethodName)) {
                    calls[0] = true;
                }
                super.visit(n, arg);
            }
        }, null);
        return calls[0];
    }

    /**
     * Add mediator field with @Autowired annotation.
     */
    private void addMediatorField(ClassOrInterfaceDeclaration clazz,
            String mediatorType, String fieldName) {
        // Check if field already exists
        if (clazz.getFieldByName(fieldName).isPresent()) {
            return;
        }

        FieldDeclaration field = clazz.addPrivateField(mediatorType, fieldName);
        field.addAnnotation("org.springframework.beans.factory.annotation.Autowired");
    }

    /**
     * Update method calls within a class to call mediator instead.
     */
    private void updateMethodCalls(ClassOrInterfaceDeclaration clazz,
            Set<String> movedMethodNames, String mediatorFieldName) {

        clazz.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr n, Void arg) {
                // If this is a call to a moved method without scope (or with 'this')
                if (movedMethodNames.contains(n.getNameAsString())) {
                    if (n.getScope().isEmpty() ||
                            n.getScope().get().toString().equals("this")) {
                        // Change to mediator.methodName()
                        n.setScope(new NameExpr(mediatorFieldName));
                    }
                }
                super.visit(n, arg);
            }
        }, null);
    }

    /**
     * Generate a name for the mediator class.
     */
    private String generateMediatorName(List<String> cycle) {
        StringBuilder sb = new StringBuilder();
        for (String c : cycle) {
            sb.append(getSimpleName(c));
        }
        sb.append("Operations");
        return sb.toString();
    }

    private ClassOrInterfaceDeclaration findClass(String fqn) {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqn);
        if (cu == null)
            return null;
        return cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
    }

    private String getSimpleName(String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }

    private String getPackage(String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(0, idx) : "";
    }

    public Set<CompilationUnit> getModifiedCUs() {
        return modifiedCUs;
    }

    public Map<String, CompilationUnit> getGeneratedClasses() {
        return generatedClasses;
    }

    /**
     * Write changes to disk.
     */
    public void writeChanges(String basePath) throws IOException {
        if (dryRun) {
            return;
        }

        // Write modified CUs
        for (CompilationUnit cu : modifiedCUs) {
            String pkg = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");
            String name = cu.getPrimaryTypeName().orElse("Unknown");
            String relativePath = pkg.replace('.', '/') + "/" + name + ".java";
            String absolutePath = basePath + "/" + relativePath;
            CopyUtils.writeFileAbsolute(absolutePath, cu.toString());
        }

        // Write generated classes
        for (Map.Entry<String, CompilationUnit> entry : generatedClasses.entrySet()) {
            String fqn = entry.getKey();
            CompilationUnit cu = entry.getValue();
            String relativePath = fqn.replace('.', '/') + ".java";
            String absolutePath = basePath + "/" + relativePath;
            CopyUtils.writeFileAbsolute(absolutePath, cu.toString());
        }
    }
}
