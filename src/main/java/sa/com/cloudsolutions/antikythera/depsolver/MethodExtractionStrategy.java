package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Modifier;
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
        // IMPORTANT: Find methods in the actual AST by name, not use collected references
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
            logger.info("Removing {} methods from {}", methodsToRemove.size(), entry.getKey());
            for (MethodDeclaration method : methodsToRemove) {
                logger.info("  Removing method: {}", method.getNameAsString());
                clazz.remove(method);
            }
            
            // Verify changes immediately after removal
            CompilationUnit cu = clazz.findCompilationUnit().orElse(null);
            if (cu != null) {
                int remainingMethods = clazz.getMethods().size();
                logger.info("After removal: {} methods remaining in {}", remainingMethods, entry.getKey());
                logger.info("Methods still present: {}", 
                    clazz.getMethods().stream().map(MethodDeclaration::getNameAsString).collect(Collectors.toList()));
                
                // Verify in AntikytheraRunTime
                String fqn = entry.getKey();
                logger.info("Checking AntikytheraRunTime for {}", fqn);
                CompilationUnit runtimeCu = AntikytheraRunTime.getCompilationUnit(fqn);
                if (runtimeCu != null) {
                    logger.info("Found runtime CU for {}", fqn);
                    ClassOrInterfaceDeclaration runtimeClazz = runtimeCu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
                    if (runtimeClazz != null) {
                        int runtimeMethods = runtimeClazz.getMethods().size();
                        logger.info("AntikytheraRunTime has {} methods for {}", runtimeMethods, fqn);
                        if (runtimeMethods != remainingMethods) {
                            logger.warn("MISMATCH: Local CU has {} methods, Runtime CU has {} methods for {}", 
                                remainingMethods, runtimeMethods, fqn);
                        } else {
                            logger.info("✓ Methods match between Local CU and Runtime CU for {}", fqn);
                        }
                    }
                }
            }
            
            // Get CompilationUnit - try multiple methods
            logger.info("Attempting to get CU for method removal in {}", entry.getKey());
            Optional<CompilationUnit> cuOpt = clazz.findCompilationUnit();
            CompilationUnit cuToAdd = cuOpt.orElse(null);
            logger.info("findCompilationUnit() result: {}", cuOpt.isPresent() ? "present" : "empty");
            
            if (cuToAdd == null) {
                // Fallback: get from AntikytheraRunTime
                String fqn = entry.getKey();
                logger.info("Trying AntikytheraRunTime.getCompilationUnit({})", fqn);
                cuToAdd = AntikytheraRunTime.getCompilationUnit(fqn);
                logger.info("AntikytheraRunTime result: {}", cuToAdd != null ? "found" : "null");
            }
            if (cuToAdd != null) {
                modifiedCUs.add(cuToAdd);
                logger.info("✓ Added CU to modifiedCUs: {} (CU primary type: {})", 
                    entry.getKey(), cuToAdd.getPrimaryTypeName().orElse("Unknown"));
            } else {
                logger.error("✗ Could not get CompilationUnit for {} - file will NOT be written!", entry.getKey());
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
            
            // Get CompilationUnit - try multiple methods
            logger.info("Attempting to get CU for field removal in {}", beanFqn);
            Optional<CompilationUnit> cuOpt = clazz.findCompilationUnit();
            CompilationUnit cuToAdd = cuOpt.orElse(null);
            logger.info("findCompilationUnit() result: {}", cuOpt.isPresent() ? "present" : "empty");
            
            if (cuToAdd == null) {
                // Fallback: get from AntikytheraRunTime
                String fqn = beanFqn;
                logger.info("Trying AntikytheraRunTime.getCompilationUnit({})", fqn);
                cuToAdd = AntikytheraRunTime.getCompilationUnit(fqn);
                logger.info("AntikytheraRunTime result: {}", cuToAdd != null ? "found" : "null");
            }
            if (cuToAdd != null) {
                modifiedCUs.add(cuToAdd);
                logger.info("✓ Added CU to modifiedCUs after field removal: {} (CU primary type: {})", 
                    beanFqn, cuToAdd.getPrimaryTypeName().orElse("Unknown"));
            } else {
                logger.error("✗ Could not get CompilationUnit for field removal in {} - file will NOT be written!", beanFqn);
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
     * Collect transitive dependencies using DependencyAnalyzer for accurate
     * dependency tracking.
     * Uses the clean collectDependencies() API for analysis-only mode.
     */
    private void collectTransitiveDependencies(ClassOrInterfaceDeclaration clazz,
            Set<MethodDeclaration> methods, Set<FieldDeclaration> fields, List<String> cycle) {

        // Create analyzer for analysis-only mode (no code generation)
        DependencyAnalyzer analyzer = new DependencyAnalyzer();

        Set<String> cycleFqns = new HashSet<>(cycle);
        Set<String> cycleSimpleNames = cycle.stream()
                .map(this::getSimpleName)
                .collect(Collectors.toSet());

        // Get compilation unit for type resolution
        CompilationUnit cu = clazz.findCompilationUnit().orElse(null);
        if (cu == null && clazz.getFullyQualifiedName().isPresent()) {
            cu = AntikytheraRunTime.getCompilationUnit(clazz.getFullyQualifiedName().get());
        }

        // Collect all dependencies with filtering to exclude cycle types
        Set<GraphNode> deps = analyzer.collectDependencies(methods, node -> {
            TypeDeclaration<?> type = node.getEnclosingType();
            if (type == null) {
                return true; // Include nodes without enclosing type
            }
            
            // Check if this node's type is in the cycle
            if (type.getFullyQualifiedName().isPresent()) {
                String fqn = type.getFullyQualifiedName().get();
                if (cycleFqns.contains(fqn)) {
                    return false; // Exclude cycle types
                }
            }
            
            return true; // Include non-cycle types
        });

        // Use DependencyQuery to extract methods and fields from discovered
        // dependencies
        Set<MethodDeclaration> discoveredMethods = DependencyQuery.getMethods(deps);
        Set<FieldDeclaration> discoveredFields = DependencyQuery.getFields(deps);

        Set<String> methodNames = methods.stream()
                .map(MethodDeclaration::getNameAsString)
                .collect(Collectors.toSet());

        // Add discovered transitive methods (excluding methods from cycle classes)
        for (MethodDeclaration md : discoveredMethods) {
            // Skip methods from cycle classes
            if (md.findCompilationUnit().isPresent()) {
                TypeDeclaration<?> enclosingType = md.findAncestor(TypeDeclaration.class).orElse(null);
                if (enclosingType != null && enclosingType.getFullyQualifiedName().isPresent()) {
                    String fqn = enclosingType.getFullyQualifiedName().get();
                    if (cycleFqns.contains(fqn)) {
                        continue; // Skip methods from cycle classes
                    }
                }
            }
            
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

        // Add discovered transitive fields (excluding cycle types)
        final ClassOrInterfaceDeclaration finalClazz = clazz;
        final CompilationUnit finalCu = cu;
        for (FieldDeclaration fd : discoveredFields) {
            String fieldName = fd.getVariable(0).getNameAsString();
            finalClazz.getFieldByName(fieldName).ifPresent(f -> {
                Type fieldType = f.getVariables().get(0).getType();
                String fieldTypeFqn = resolveFieldTypeFqn(fieldType, finalClazz, finalCu);
                
                // Check both FQN and simple name
                boolean isCycleType = false;
                if (fieldTypeFqn != null) {
                    isCycleType = cycleFqns.contains(fieldTypeFqn) ||
                                 cycleSimpleNames.contains(getSimpleName(fieldTypeFqn));
                } else {
                    // Fallback to simple name matching
                    String typeAsString = f.getVariables().get(0).getTypeAsString();
                    isCycleType = cycleSimpleNames.contains(typeAsString) ||
                                 cycleSimpleNames.contains(getSimpleName(typeAsString));
                }
                
                if (!isCycleType) {
                    fields.add(f);
                }
            });
        }

        // Create a copy to include newly added transitive methods
        Set<MethodDeclaration> allMethods = new HashSet<>(methods);

        // Also check for fields used directly in method bodies
        for (MethodDeclaration method : allMethods) {
            Set<FieldDeclaration> usedFields = DependencyQuery.getFieldsUsedBy(method, deps);
            for (FieldDeclaration f : usedFields) {
                Type fieldType = f.getVariables().get(0).getType();
                String fieldTypeFqn = resolveFieldTypeFqn(fieldType, clazz, cu);
                
                boolean isCycleType = false;
                if (fieldTypeFqn != null) {
                    isCycleType = cycleFqns.contains(fieldTypeFqn) ||
                                 cycleSimpleNames.contains(getSimpleName(fieldTypeFqn));
                } else {
                    String typeAsString = f.getVariables().get(0).getTypeAsString();
                    isCycleType = cycleSimpleNames.contains(typeAsString) ||
                                 cycleSimpleNames.contains(getSimpleName(typeAsString));
                }
                
                if (!isCycleType) {
                    fields.add(f);
                }
            }
            // Also check fields by name in case not discovered by DependencyQuery
            final ClassOrInterfaceDeclaration finalClazz2 = clazz;
            final CompilationUnit finalCu2 = cu;
            method.findAll(NameExpr.class).forEach(ne -> {
                String name = ne.getNameAsString();
                finalClazz2.getFieldByName(name).ifPresent(f -> {
                    Type fieldType = f.getVariables().get(0).getType();
                    String fieldTypeFqn = resolveFieldTypeFqn(fieldType, finalClazz2, finalCu2);
                    
                    boolean isCycleType = false;
                    if (fieldTypeFqn != null) {
                        isCycleType = cycleFqns.contains(fieldTypeFqn) ||
                                     cycleSimpleNames.contains(getSimpleName(fieldTypeFqn));
                    } else {
                        String typeAsString = f.getVariables().get(0).getTypeAsString();
                        isCycleType = cycleSimpleNames.contains(typeAsString) ||
                                     cycleSimpleNames.contains(getSimpleName(typeAsString));
                    }
                    
                    if (!isCycleType) {
                        fields.add(f);
                    }
                });
            });
        }
    }

    /**
     * Generate mediator class with moved methods and fields.
     * The mediator needs cycle class fields (injected via constructor) so extracted methods can call them.
     */
    private CompilationUnit generateMediatorClass(String name, String pkg,
            Map<String, Set<MethodDeclaration>> methods, Map<String, Set<FieldDeclaration>> fields,
            List<String> cycle) {

        CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(pkg);

        ClassOrInterfaceDeclaration mediator = cu.addClass(name, Modifier.Keyword.PUBLIC);
        mediator.addAnnotation("org.springframework.stereotype.Service");

        // Add helper fields (excluding cycle types)
        for (Set<FieldDeclaration> fieldSet : fields.values()) {
            for (FieldDeclaration field : fieldSet) {
                FieldDeclaration clone = field.clone();
                mediator.addMember(clone);
            }
        }

        // Add cycle class fields with @Autowired and @Lazy
        // These are needed so extracted methods can call methods on cycle classes
        // The mediator is a Spring bean, so it can inject cycle classes
        // @Lazy is needed to break the cycle: Mediator → CycleClass → Mediator
        logger.debug("Adding cycle class fields to mediator {}", name);
        for (String cycleFqn : cycle) {
            String cycleSimpleName = getSimpleName(cycleFqn);
            String fieldName = Character.toLowerCase(cycleSimpleName.charAt(0)) + cycleSimpleName.substring(1);
            
            logger.debug("  Creating field: {} of type {}", fieldName, cycleSimpleName);
            
            // Manually create field declaration (addPrivateField may not work correctly)
            com.github.javaparser.ast.type.ClassOrInterfaceType fieldType = 
                new com.github.javaparser.ast.type.ClassOrInterfaceType(cycleSimpleName);
            VariableDeclarator variable = new VariableDeclarator(fieldType, fieldName);
            FieldDeclaration cycleField = new FieldDeclaration();
            cycleField.setModifiers(Modifier.Keyword.PRIVATE);
            cycleField.addVariable(variable);
            cycleField.addAnnotation("org.springframework.beans.factory.annotation.Autowired");
            cycleField.addAnnotation("org.springframework.context.annotation.Lazy");
            // Explicitly add as member - insert after helper fields but before methods
            int insertIndex = mediator.getFields().size();
            mediator.getMembers().add(insertIndex, cycleField);
            
            logger.debug("  Added field at index {}, mediator now has {} fields", insertIndex, mediator.getFields().size());
            
            // Verify field was added
            if (mediator.getFieldByName(fieldName).isPresent()) {
                logger.debug("  ✓ Field {} verified in mediator", fieldName);
            } else {
                logger.error("  ✗ Field {} NOT found in mediator after adding!", fieldName);
            }
            
            // Add imports for the cycle class and @Lazy
            cu.addImport(new ImportDeclaration(cycleFqn, false, false));
        }
        
        // Final verification of all fields in mediator
        logger.debug("Mediator {} final field count: {}", name, mediator.getFields().size());
        logger.debug("Mediator fields: {}", 
            mediator.getFields().stream()
                .map(f -> f.getVariable(0).getNameAsString())
                .collect(Collectors.toList()));
        
        // Add @Lazy import if not already present
        boolean hasLazyImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals("org.springframework.context.annotation.Lazy"));
        if (!hasLazyImport) {
            cu.addImport(new ImportDeclaration("org.springframework.context.annotation.Lazy", false, false));
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
     * Resolves field types to FQNs for accurate comparison with cycle FQNs.
     */
    private void removeCycleField(ClassOrInterfaceDeclaration clazz, List<String> cycle) {
        Set<String> cycleFqns = new HashSet<>(cycle);
        Set<String> cycleSimpleNames = new HashSet<>();
        for (String c : cycle) {
            cycleSimpleNames.add(getSimpleName(c));
        }

        // Get compilation unit for type resolution
        CompilationUnit cuForResolution = clazz.findCompilationUnit().orElse(null);
        if (cuForResolution == null) {
            // Try to get from AntikytheraRunTime
            if (clazz.getFullyQualifiedName().isPresent()) {
                cuForResolution = AntikytheraRunTime.getCompilationUnit(clazz.getFullyQualifiedName().get());
            }
        }

        // Collect fields to remove (can't modify during iteration)
        List<FieldDeclaration> toRemove = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            // Only remove injected fields (fields that participate in DI cycles)
            if (!isInjectedField(field)) {
                continue;
            }

            VariableDeclarator variable = field.getVariables().get(0);
            Type fieldType = variable.getType();
            
            // Resolve field type to FQN using the same logic as BeanDependencyGraph
            String fieldTypeFqn = resolveFieldTypeFqn(fieldType, clazz, cuForResolution);
            
            // Also check the typeAsString for simple name matching
            String typeAsString = variable.getTypeAsString();
            String typeSimpleName = getSimpleName(typeAsString);

            // Check if field type matches any cycle type
            // Compare FQN first (most reliable), then fall back to simple name matching
            boolean matchesCycle = false;
            if (fieldTypeFqn != null) {
                // Direct FQN match
                matchesCycle = cycleFqns.contains(fieldTypeFqn);
                
                // Also check if resolved FQN ends with any cycle simple name
                if (!matchesCycle) {
                    String resolvedSimpleName = getSimpleName(fieldTypeFqn);
                    matchesCycle = cycleSimpleNames.contains(resolvedSimpleName);
                }
            }
            
            // Fallback: simple name matching (for cases where resolution fails)
            if (!matchesCycle) {
                matchesCycle = cycleSimpleNames.contains(typeAsString) ||
                               cycleSimpleNames.contains(typeSimpleName);
            }

            if (matchesCycle) {
                toRemove.add(field);
            }
        }
        
        // Remove collected fields - ensure we're removing from the actual AST
        // Collect field names first to avoid concurrent modification
        Set<String> fieldNamesToRemove = new HashSet<>();
        for (FieldDeclaration field : toRemove) {
            VariableDeclarator var = field.getVariable(0);
            if (var != null) {
                fieldNamesToRemove.add(var.getNameAsString());
            }
        }
        
        // Find and remove actual fields from AST by name
        // Remove from parent's member list (more reliable than field.remove())
        List<FieldDeclaration> fieldsToRemove = new ArrayList<>();
        for (FieldDeclaration field : clazz.getFields()) {
            VariableDeclarator var = field.getVariable(0);
            if (var != null && fieldNamesToRemove.contains(var.getNameAsString())) {
                fieldsToRemove.add(field);
            }
        }
        String className = clazz.getFullyQualifiedName().orElse("unknown");
        logger.info("Removing {} fields from {}", fieldsToRemove.size(), className);
        for (FieldDeclaration field : fieldsToRemove) {
            VariableDeclarator var = field.getVariable(0);
            String fieldName = var != null ? var.getNameAsString() : "unknown";
            logger.info("  Removing field: {}", fieldName);
            clazz.remove(field);
        }
        
        // Verify changes immediately after removal
        CompilationUnit cuAfterRemoval = clazz.findCompilationUnit().orElse(null);
        if (cuAfterRemoval != null) {
            int remainingFields = clazz.getFields().size();
            logger.info("After field removal: {} fields remaining", remainingFields);
            logger.info("Fields still present: {}", 
                clazz.getFields().stream()
                    .map(f -> f.getVariable(0).getNameAsString())
                    .collect(Collectors.toList()));
            
            // Verify in AntikytheraRunTime
            String fqn = clazz.getFullyQualifiedName().orElse(null);
            if (fqn != null) {
                logger.info("Checking AntikytheraRunTime for field removal in {}", fqn);
                CompilationUnit runtimeCu = AntikytheraRunTime.getCompilationUnit(fqn);
                if (runtimeCu != null) {
                    logger.info("Found runtime CU for field removal in {}", fqn);
                    ClassOrInterfaceDeclaration runtimeClazz = runtimeCu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
                    if (runtimeClazz != null) {
                        int runtimeFields = runtimeClazz.getFields().size();
                        logger.info("AntikytheraRunTime has {} fields for {}", runtimeFields, fqn);
                        if (runtimeFields != remainingFields) {
                            logger.warn("MISMATCH: Local CU has {} fields, Runtime CU has {} fields for {}", 
                                remainingFields, runtimeFields, fqn);
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolve a field type to its fully qualified name.
     * Uses the same logic as BeanDependencyGraph.resolveTypeFqn() for consistency.
     */
    private String resolveFieldTypeFqn(Type fieldType, ClassOrInterfaceDeclaration context, CompilationUnit cu) {
        try {
            String typeName;
            
            // For ClassOrInterfaceType (including parameterized types), extract the raw type name
            if (fieldType instanceof com.github.javaparser.ast.type.ClassOrInterfaceType classOrInterfaceType) {
                // Get the name without type parameters
                typeName = classOrInterfaceType.getNameAsString();
                
                // If the type has a scope (e.g., com.example.TypedService), use it directly
                if (classOrInterfaceType.getScope().isPresent()) {
                    String scopedName = classOrInterfaceType.getScope().orElseThrow().asString() + "." + typeName;
                    // Check if this FQN exists in AntikytheraRunTime
                    if (AntikytheraRunTime.getTypeDeclaration(scopedName).isPresent()) {
                        return scopedName;
                    }
                }
            } else {
                // For other types, use asString() and extract raw type name if parameterized
                typeName = fieldType.asString();
                
                // If it's a parameterized type, extract just the raw type name
                if (typeName.contains("<")) {
                    typeName = typeName.substring(0, typeName.indexOf('<')).trim();
                }
                
                // Also handle array types (e.g., "String[]" -> "String")
                if (typeName.contains("[")) {
                    typeName = typeName.substring(0, typeName.indexOf('[')).trim();
                }
            }
            
            // Get package name from the compilation unit (prefer passed cu, fallback to finding it)
            if (cu == null) {
                cu = context.findCompilationUnit().orElse(null);
                // If still null, try getting it from AntikytheraRunTime using the FQN
                if (cu == null && context.getFullyQualifiedName().isPresent()) {
                    cu = AntikytheraRunTime.getCompilationUnit(context.getFullyQualifiedName().get());
                }
            }
            String packageName = "";
            if (cu != null) {
                packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString())
                        .orElse("");
            } else if (context.getFullyQualifiedName().isPresent()) {
                // Fallback: extract package from FQN
                String fqn = context.getFullyQualifiedName().get();
                int lastDot = fqn.lastIndexOf('.');
                if (lastDot > 0) {
                    packageName = fqn.substring(0, lastDot);
                }
            }
            
            // Try exact match first
            if (AntikytheraRunTime.getTypeDeclaration(typeName).isPresent()) {
                return typeName;
            }
            
            // Try package + typeName (same package)
            if (!packageName.isEmpty()) {
                String samePackageFqn = packageName + "." + typeName;
                if (AntikytheraRunTime.getTypeDeclaration(samePackageFqn).isPresent()) {
                    return samePackageFqn;
                }
            }
            
            // Search all resolved types for a match (ends with pattern)
            for (String resolvedFqn : AntikytheraRunTime.getResolvedTypes().keySet()) {
                if (resolvedFqn.equals(typeName) || resolvedFqn.endsWith("." + typeName)) {
                    return resolvedFqn;
                }
            }
            
            // Strategy 2: Use AbstractCompiler.findFullyQualifiedName (existing logic)
            // This handles imports, java.lang types, etc.
            String fqn = null;
            if (cu != null) {
                fqn = sa.com.cloudsolutions.antikythera.parser.AbstractCompiler.findFullyQualifiedName(cu, typeName);
            }
            
            // Strategy 3: Final fallback - search AntikytheraRunTime again (in case it was added)
            if (fqn == null) {
                for (String resolvedFqn : AntikytheraRunTime.getResolvedTypes().keySet()) {
                    if (resolvedFqn.endsWith("." + typeName) || resolvedFqn.equals(typeName)) {
                        fqn = resolvedFqn;
                        break;
                    }
                }
            }
            
            return fqn;
        } catch (Exception e) {
            // On exception, try one more fallback search
            try {
                String typeName = fieldType instanceof com.github.javaparser.ast.type.ClassOrInterfaceType 
                    ? ((com.github.javaparser.ast.type.ClassOrInterfaceType) fieldType).getNameAsString()
                    : fieldType.asString();
                
                // Extract raw type name if parameterized
                if (typeName.contains("<")) {
                    typeName = typeName.substring(0, typeName.indexOf('<')).trim();
                }
                
                for (String resolvedFqn : AntikytheraRunTime.getResolvedTypes().keySet()) {
                    if (resolvedFqn.endsWith("." + typeName) || resolvedFqn.equals(typeName)) {
                        return resolvedFqn;
                    }
                }
            } catch (Exception e2) {
                // Ignore
            }
            return null;
        }
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
        logger.info("=== writeChanges() called with basePath: {} ===", basePath);
        logger.info("Modified CUs count: {}", modifiedCUs.size());
        logger.info("Generated classes count: {}", generatedClasses.size());
        if (dryRun) {
            logger.info("Dry run mode - not writing files");
            return;
        }

        // Write modified CUs
        logger.info("Writing {} modified compilation units", modifiedCUs.size());
        for (CompilationUnit cu : modifiedCUs) {
            String pkg = cu.getPackageDeclaration()
                    .map(NodeWithName::getNameAsString)
                    .orElse("");
            String name = cu.getPrimaryTypeName().orElse("Unknown");
            String fqn = pkg.isEmpty() ? name : pkg + "." + name;
            
            // Use forward slash for Java package paths (platform-independent)
            String relativePath = pkg.replace('.', '/') + "/" + name + ".java";
            String absolutePath = basePath + File.separator + relativePath;
            logger.info("Writing {} to: {}", fqn, absolutePath);
            
            // Verify CompilationUnit state before writing
            ClassOrInterfaceDeclaration clazz = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
            if (clazz != null) {
                logger.info("Before write - {}: {} methods, {} fields", fqn, 
                    clazz.getMethods().size(), clazz.getFields().size());
                
                // Fetch from AntikytheraRunTime and compare
                CompilationUnit runtimeCu = AntikytheraRunTime.getCompilationUnit(fqn);
                if (runtimeCu != null) {
                    ClassOrInterfaceDeclaration runtimeClazz = runtimeCu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
                    if (runtimeClazz != null) {
                        logger.info("Runtime CU - {}: {} methods, {} fields", fqn,
                            runtimeClazz.getMethods().size(), runtimeClazz.getFields().size());
                        if (runtimeClazz.getMethods().size() != clazz.getMethods().size() ||
                            runtimeClazz.getFields().size() != clazz.getFields().size()) {
                            logger.warn("MISMATCH before write for {}: Local({}m,{}f) vs Runtime({}m,{}f)",
                                fqn, clazz.getMethods().size(), clazz.getFields().size(),
                                runtimeClazz.getMethods().size(), runtimeClazz.getFields().size());
                        }
                    }
                }
            }
            
            // Use toString() for structural changes (field/method removal)
            // LexicalPreservingPrinter requires setup before modifications
            String content = cu.toString();
            logger.info("Content length: {} chars", content.length());
            CopyUtils.writeFileAbsolute(absolutePath, content);
            logger.info("✓ Written {}", absolutePath);
        }

        // Write generated classes
        logger.debug("Writing {} generated classes", generatedClasses.size());
        for (Map.Entry<String, CompilationUnit> entry : generatedClasses.entrySet()) {
            String fqn = entry.getKey();
            CompilationUnit cu = entry.getValue();
            
            // Verify mediator fields before writing
            ClassOrInterfaceDeclaration mediator = cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
            if (mediator != null) {
                logger.info("Before write - {}: {} methods, {} fields", fqn,
                    mediator.getMethods().size(), mediator.getFields().size());
                logger.debug("Mediator fields: {}", 
                    mediator.getFields().stream()
                        .map(f -> f.getVariable(0).getNameAsString())
                        .collect(Collectors.toList()));
            }
            
            // Use forward slash for Java package paths (platform-independent)
            String relativePath = fqn.replace('.', '/') + ".java";
            String absolutePath = basePath + File.separator + relativePath;
            logger.debug("Writing generated class to: {}", absolutePath);
            
            // Use toString() for generated classes
            String content = cu.toString();
            logger.debug("Generated content length: {} chars", content.length());
            CopyUtils.writeFileAbsolute(absolutePath, content);
            
            logger.debug("✓ Written generated class {}", absolutePath);
        }
    }
}
