package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Scope;
import sa.com.cloudsolutions.antikythera.evaluator.ScopeChain;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Logger logger = LoggerFactory.getLogger(MethodExtractionStrategy.class);

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
        logger.info("=== MethodExtractionStrategy.apply() CALLED ===");
        logger.info("Cycle: {}", cycle);
        if (cycle == null || cycle.size() < 2) {
            logger.info("Cycle is null or too small, returning false");
            return false;
        }
        logger.info("Cycle size: {}, proceeding with extraction", cycle.size());

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
            logger.info("No cycle-causing methods found, returning false");
            return false;
        }
        logger.info("Found cycle-causing methods in {} classes", cycleCausingMethods.size());

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
                allMethodsToMove, allFieldsToMove, cycle);
        generatedClasses.put(mediatorPackage + "." + mediatorName, mediatorCU);

        // Collect all moved method names for caller redirection
        Set<String> movedMethodNames = new HashSet<>();
        for (Set<MethodDeclaration> methods : allMethodsToMove.values()) {
            for (MethodDeclaration m : methods) {
                movedMethodNames.add(m.getNameAsString());
            }
        }

        // Step 4: Remove methods and fields from original classes
        // First, remove methods from classes that had methods moved
        // IMPORTANT: Find methods in the actual AST by name, not use collected
        // references
        // This ensures we're removing the actual AST nodes, not clones
        for (Map.Entry<String, Set<MethodDeclaration>> entry : allMethodsToMove.entrySet()) {
            ClassOrInterfaceDeclaration clazz = findClass(entry.getKey());
            if (clazz == null) {
                continue;
            }

            // Find and remove actual methods from AST by name
            Set<String> methodNamesToRemove = new HashSet<>();
            for (MethodDeclaration method : allMethodsToMove.get(entry.getKey())) {
                methodNamesToRemove.add(method.getNameAsString());
            }

            // Remove methods from the actual AST by removing from parent's member list
            List<MethodDeclaration> methodsToRemove = new ArrayList<>();
            for (MethodDeclaration method : clazz.getMethods()) {
                if (methodNamesToRemove.contains(method.getNameAsString())) {
                    methodsToRemove.add(method);
                }
            }
            // Remove from parent's member list (more reliable than method.remove())
            for (MethodDeclaration method : methodsToRemove) {
                clazz.remove(method);
            }

            Optional<CompilationUnit> cuOpt = clazz.findCompilationUnit();
            CompilationUnit cuToAdd = cuOpt.orElse(null);

            if (cuToAdd == null) {
                String fqn = entry.getKey();
                cuToAdd = AntikytheraRunTime.getCompilationUnit(fqn);
            }
            if (cuToAdd != null) {
                modifiedCUs.add(cuToAdd);
            }
        }

        // Remove cycle-causing fields from ALL classes in the cycle
        // (not just those that had methods moved)
        for (String beanFqn : cycle) {
            ClassOrInterfaceDeclaration clazz = findClass(beanFqn);
            if (clazz == null) {
                continue;
            }
            removeCycleField(clazz, cycle);

            Optional<CompilationUnit> cuOpt = clazz.findCompilationUnit();
            CompilationUnit cuToAdd = cuOpt.orElse(null);

            if (cuToAdd == null) {
                cuToAdd = AntikytheraRunTime.getCompilationUnit(beanFqn);
            }
            if (cuToAdd != null) {
                modifiedCUs.add(cuToAdd);
            }
        }

        // Step 5: Redirect callers to use mediator (add mediator field + update calls)
        redirectCallers(cycle, mediatorName, movedMethodNames);

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
            for (VariableDeclarator variable : field.getVariables()) {
                if (variable.getTypeAsString().equals(typeName)) {
                    return variable.getNameAsString();
                }
            }
        }
        return null;
    }

    /**
     * Collect transitive dependencies using DependencyAnalyzer and new utility
     * methods.
     * Simplified implementation using collectDependenciesExcluding() and batch
     * queries.
     */
    private void collectTransitiveDependencies(ClassOrInterfaceDeclaration clazz,
            Set<MethodDeclaration> methods, Set<FieldDeclaration> fields, List<String> cycle) {

        // Create analyzer for analysis-only mode (no code generation)
        DependencyAnalyzer analyzer = new DependencyAnalyzer();
        Set<String> cycleFqns = new HashSet<>(cycle);

        // Get compilation unit for type resolution
        CompilationUnit cu = clazz.findCompilationUnit().orElse(null);
        if (cu == null && clazz.getFullyQualifiedName().isPresent()) {
            cu = AntikytheraRunTime.getCompilationUnit(clazz.getFullyQualifiedName().get());
        }

        // DIRECT APPROACH: Discover helper methods called from within the class
        // This is a simple worklist algorithm that finds all same-class method calls
        // without relying on DependencyAnalyzer which may miss these
        Set<String> discoveredMethodNames = methods.stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toCollection(HashSet::new));
        Set<MethodDeclaration> workList = new HashSet<>(methods);
        Set<MethodDeclaration> processed = new HashSet<>();

        while (!workList.isEmpty()) {
            MethodDeclaration current = workList.iterator().next();
            workList.remove(current);
            if (processed.contains(current)) {
                continue;
            }
            processed.add(current);

            // Find all method calls in this method that have no scope or "this" scope
            // These are same-class method calls
            current.findAll(MethodCallExpr.class).forEach(mce -> {
                String calledName = mce.getNameAsString();
                if ((mce.getScope().isEmpty() ||
                        (mce.getScope().isPresent() && mce.getScope().get().toString().equals("this")))
                        && !discoveredMethodNames.contains(calledName)) {
                    // Find the method in the same class
                    for (MethodDeclaration md : clazz.getMethodsByName(calledName)) {
                        if (!processed.contains(md)) {
                            workList.add(md);
                            methods.add(md);
                            discoveredMethodNames.add(calledName);
                        }
                    }
                }
            });
        }

        // Collect all dependencies excluding cycle types using convenience method
        Set<GraphNode> deps = analyzer.collectDependenciesExcluding(methods, cycleFqns);

        // Extract discovered methods and fields using DependencyQuery
        Set<MethodDeclaration> discoveredMethods = DependencyQuery.getMethods(deps);
        Set<String> methodNames = methods.stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toSet());

        // Add discovered transitive methods from the same class (excluding cycle
        // classes)
        for (MethodDeclaration md : discoveredMethods) {
            TypeDeclaration<?> enclosingType = md.findAncestor(TypeDeclaration.class).orElse(null);
            if (enclosingType != null && enclosingType.getFullyQualifiedName().isPresent()) {
                String fqn = enclosingType.getFullyQualifiedName().get();
                // Skip methods from cycle classes
                if (cycleFqns.contains(fqn)) {
                    continue;
                }
                // Only add methods from the same class
                if (fqn.equals(clazz.getFullyQualifiedName().orElse(null))) {
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
                }
            }
        }

        // Collect fields used by all methods (including discovered helper methods)
        // Check ALL methods - both in methods set and discovered from deps
        final CompilationUnit finalCu = cu;
        Set<String> collectedFieldNames = new HashSet<>();
        Set<MethodDeclaration> allMethodsToCheck = new HashSet<>(methods);

        // Also include methods discovered from deps (in case they weren't added to
        // methods set)
        String clazzFqn = clazz.getFullyQualifiedName().orElse(null);
        for (GraphNode node : deps) {
            if (node.getNode() instanceof MethodDeclaration md) {
                TypeDeclaration<?> enclosingType = md.findAncestor(TypeDeclaration.class).orElse(null);
                if (enclosingType != null && enclosingType.getFullyQualifiedName().isPresent()) {
                    String fqn = enclosingType.getFullyQualifiedName().get();
                    if (fqn.equals(clazzFqn) && !cycleFqns.contains(fqn)) {
                        allMethodsToCheck.add(md);
                    }
                }
            }
        }

        // Check all methods for field usage - use clazz directly for field lookup
        for (MethodDeclaration md : allMethodsToCheck) {
            // Find all NameExpr in method body - these could be field references
            md.findAll(com.github.javaparser.ast.expr.NameExpr.class).forEach(ne -> {
                String name = ne.getNameAsString();
                // Check if this name refers to a field in the class
                // Use clazz directly since all methods should be from this class
                clazz.getFieldByName(name).ifPresent(f -> {
                    // Avoid duplicates
                    String fieldName = f.getVariable(0).getNameAsString();
                    if (!collectedFieldNames.contains(fieldName)) {
                        // Check if field type is not a cycle type
                        // Include field even if type resolution fails (fieldTypeFqn == null)
                        Type fieldType = f.getVariables().get(0).getType();
                        String fieldTypeFqn = AbstractCompiler.resolveTypeFqn(fieldType, clazz, finalCu);
                        if (fieldTypeFqn == null || !cycleFqns.contains(fieldTypeFqn)) {
                            fields.add(f);
                            collectedFieldNames.add(fieldName);
                        }
                    }
                });
            });
        }
    }

    /**
     * Custom DepSolver for mediator class generation.
     * Routes discovered methods/fields to mediator's destination CU instead of
     * original class.
     */
    private static class MediatorDepSolver extends DepSolver {
        private final ClassOrInterfaceDeclaration mediator;
        private final CompilationUnit mediatorCU;

        public MediatorDepSolver(ClassOrInterfaceDeclaration mediator, CompilationUnit mediatorCU) {
            this.mediator = mediator;
            this.mediatorCU = mediatorCU;
        }

        @Override
        protected void onCallableDiscovered(GraphNode node, CallableDeclaration<?> cd) {
            // Add method to mediator instead of original class
            if (cd.isMethodDeclaration()) {
                MethodDeclaration md = cd.asMethodDeclaration();
                if (mediator.getMethodsByName(md.getNameAsString()).isEmpty()) {
                    MethodDeclaration clone = md.clone();
                    clone.setModifiers(Modifier.Keyword.PUBLIC);
                    mediator.addMember(clone);
                }
            }
        }

        @Override
        protected GraphNode createAnalysisNode(Node node) {
            GraphNode g = Graph.createGraphNode(node);
            // For methods, ensure destination CU is mediator CU
            if (node instanceof MethodDeclaration) {
                g.setDestination(mediatorCU);
                g.setTypeDeclaration(mediator);
            }
            return g;
        }
    }

    /**
     * Generate mediator class using Graph/GraphNode infrastructure.
     * Leverages automatic dependency discovery and import management.
     */
    private CompilationUnit generateMediatorClass(String name, String pkg,
            Map<String, Set<MethodDeclaration>> methods, Map<String, Set<FieldDeclaration>> fields,
            List<String> cycle) {

        // Create base mediator class
        CompilationUnit mediatorCU = new CompilationUnit();
        mediatorCU.setPackageDeclaration(pkg);
        ClassOrInterfaceDeclaration mediator = mediatorCU.addClass(name, Modifier.Keyword.PUBLIC);
        mediator.addAnnotation("org.springframework.stereotype.Service");

        // Register mediator in Graph for dependency tracking
        String mediatorFqn = pkg + "." + name;
        Graph.getDependencies().put(mediatorFqn, mediatorCU);

        // Create GraphNode for mediator to enable addField() usage
        GraphNode mediatorNode = Graph.createGraphNode(mediator);
        mediatorNode.setDestination(mediatorCU);
        mediatorNode.setTypeDeclaration(mediator);

        // Use GraphNode.addField() for helper fields - automatically handles
        // imports/type args
        for (Set<FieldDeclaration> fieldSet : fields.values()) {
            for (FieldDeclaration field : fieldSet) {
                mediatorNode.addField(field);
            }
        }

        // Add cycle class fields with @Autowired @Lazy
        for (String cycleFqn : cycle) {
            String simpleName = getSimpleName(cycleFqn);
            String fieldName = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);

            // Create field declaration
            com.github.javaparser.ast.type.ClassOrInterfaceType fieldType = new com.github.javaparser.ast.type.ClassOrInterfaceType(
                    simpleName);
            VariableDeclarator variable = new VariableDeclarator(fieldType, fieldName);
            FieldDeclaration cycleField = new FieldDeclaration();
            cycleField.setModifiers(Modifier.Keyword.PRIVATE);
            cycleField.addVariable(variable);
            cycleField.addAnnotation("org.springframework.beans.factory.annotation.Autowired");
            cycleField.addAnnotation("org.springframework.context.annotation.Lazy");

            // Use GraphNode.addField() to handle imports automatically
            mediatorNode.addField(cycleField);
        }

        // Add methods directly to mediator, then use Graph infrastructure for
        // dependency discovery
        MediatorDepSolver solver = new MediatorDepSolver(mediator, mediatorCU);
        for (Set<MethodDeclaration> methodSet : methods.values()) {
            for (MethodDeclaration method : methodSet) {
                // Add method directly to mediator (clone to avoid modifying original)
                MethodDeclaration clone = method.clone();
                clone.setModifiers(Modifier.Keyword.PUBLIC);
                mediator.addMember(clone);

                // Now use Graph infrastructure to discover transitive dependencies
                // Create graph node for the cloned method in mediator context
                GraphNode methodNode = Graph.createGraphNode(clone);
                methodNode.setDestination(mediatorCU);
                methodNode.setTypeDeclaration(mediator);
                // Run DFS to discover all transitive dependencies (calls onCallableDiscovered
                // for dependencies)
                solver.dfs();
            }
        }

        return mediatorCU;
    }

    /**
     * Remove the field that causes the cycle.
     * Resolves field types to FQNs for accurate comparison with cycle FQNs.
     */
    private void removeCycleField(ClassOrInterfaceDeclaration clazz, List<String> cycle) {
        Set<String> cycleFqns = new HashSet<>(cycle);

        // Get compilation unit for type resolution
        CompilationUnit cuForResolution = clazz.findCompilationUnit()
                .orElseGet(() -> {
                    String fqn = clazz.getFullyQualifiedName().orElse(null);
                    return fqn != null ? AntikytheraRunTime.getCompilationUnit(fqn) : null;
                });

        // Collect and remove cycle fields
        List<FieldDeclaration> toRemove = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            // Only remove injected fields (fields that participate in DI cycles)
            if (!isInjectedField(field)) {
                continue;
            }

            Type fieldType = field.getVariables().get(0).getType();
            String fieldTypeFqn = resolveFieldTypeFqn(fieldType, clazz, cuForResolution);

            // Check if field type matches any cycle type (FQN match only)
            if (fieldTypeFqn != null && cycleFqns.contains(fieldTypeFqn)) {
                toRemove.add(field);
            }
        }

        // Remove fields from AST
        for (FieldDeclaration field : toRemove) {
            clazz.remove(field);
        }
    }

    /**
     * Resolve field type FQN using AbstractCompiler utility.
     * Delegates to AbstractCompiler.resolveTypeFqn() for consistent type
     * resolution.
     */
    private String resolveFieldTypeFqn(Type fieldType, ClassOrInterfaceDeclaration context, CompilationUnit cu) {
        return AbstractCompiler.resolveTypeFqn(fieldType, context, cu);
    }

    /**
     * Check if a field is injected (has @Autowired, @Inject, or @Resource).
     */
    private boolean isInjectedField(FieldDeclaration field) {
        return field.getAnnotationByName("Autowired").isPresent() ||
                field.getAnnotationByName("Inject").isPresent() ||
                field.getAnnotationByName("Resource").isPresent() ||
                field.getAnnotationByName("javax.inject.Inject").isPresent() ||
                field.getAnnotationByName("javax.annotation.Resource").isPresent() ||
                field.getAnnotationByName("jakarta.inject.Inject").isPresent() ||
                field.getAnnotationByName("jakarta.annotation.Resource").isPresent();
    }

    /**
     * Redirect callers of moved methods to use the mediator class.
     * For each class in the cycle, add a mediator field and update internal calls.
     */
    private void redirectCallers(List<String> cycle, String mediatorName,
            Set<String> movedMethodNames) {

        String mediatorFieldName = Character.toLowerCase(mediatorName.charAt(0)) +
                mediatorName.substring(1);

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
                        addMediatorField(clazz, mediatorName, mediatorFieldName);
                        updateMethodCalls(clazz, movedMethodNames, mediatorFieldName);
                        clazz.findCompilationUnit().ifPresent(modifiedCUs::add);
                        break;
                    }
                }
                if (hasMovedMethods)
                    break;
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
     * Add mediator field with @Autowired and @Lazy annotations.
     * 
     * @Lazy breaks the instantiation cycle by deferring mediator creation.
     */
    private void addMediatorField(ClassOrInterfaceDeclaration clazz,
            String mediatorType, String fieldName) {
        // Check if field already exists
        if (clazz.getFieldByName(fieldName).isPresent()) {
            return;
        }

        FieldDeclaration field = clazz.addPrivateField(mediatorType, fieldName);
        field.addAnnotation("org.springframework.beans.factory.annotation.Autowired");
        field.addAnnotation("org.springframework.context.annotation.Lazy");
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
            logger.info("Dry run mode - not writing files");
            return;
        }

        for (CompilationUnit cu : modifiedCUs) {
            String pkg = cu.getPackageDeclaration()
                    .map(NodeWithName::getNameAsString)
                    .orElse("");
            String name = AbstractCompiler.getPublicType(cu).getNameAsString();

            // Use forward slash for Java package paths (platform-independent)
            String relativePath = pkg.replace('.', '/') + File.separator + name + ".java";
            String absolutePath = basePath + File.separator + relativePath;

            String content = cu.toString();
            CopyUtils.writeFileAbsolute(absolutePath, content);
        }

        // Use Graph.getDependencies() as single source of truth for generated classes
        // This includes mediator classes registered via Graph.createGraphNode()
        for (Map.Entry<String, CompilationUnit> entry : Graph.getDependencies().entrySet()) {
            String fqn = entry.getKey();
            CompilationUnit cu = entry.getValue();

            // Only write if this is a generated class (not an original class)
            // Check if it's in our generatedClasses map or if it's a mediator (contains
            // "Mediator" in name)
            if (generatedClasses.containsKey(fqn) || fqn.contains("Mediator")) {
                // Use forward slash for Java package paths (platform-independent)
                String relativePath = fqn.replace('.', '/') + ".java";
                String absolutePath = basePath + File.separator + relativePath;

                // Use toString() for generated classes
                String content = cu.toString();
                CopyUtils.writeFileAbsolute(absolutePath, content);
            }
        }
    }
}
