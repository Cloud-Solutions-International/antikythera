package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Strategy for breaking circular dependencies by extracting methods into a new
 * bean.
 * 
 * <p>
 * This strategy identifies methods in the 'from' bean that use the 'to' bean,
 * and extracts them into a new Mediator class. The original bean and the
 * dependency cycle participants will then depend on this new Mediator.
 * </p>
 */
public class MethodExtractionStrategy extends AbstractExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(MethodExtractionStrategy.class);
    private final Map<String, CompilationUnit> generatedMediators = new HashMap<>();

    public MethodExtractionStrategy() {
        super();
    }

    public MethodExtractionStrategy(boolean dryRun) {
        super(dryRun);
    }

    public Map<String, CompilationUnit> getGeneratedClasses() {
        return generatedMediators;
    }

    private static class ExtractionCandidate {
        ClassOrInterfaceDeclaration sourceClass;
        Set<MethodDeclaration> methods;
        String dependencyBeanName;
        String dependencyFieldName;
    }

    /**
     * Apply the extraction strategy to a cycle.
     * 
     * @param cycle The list of bean names in the cycle
     * @return true if the cycle was successfully broken
     */
    public boolean apply(List<String> cycle) {
        if (cycle == null || cycle.isEmpty()) {
            return false;
        }

        logger.info("Attempting to break cycle by extracting methods. Cycle: {}", cycle);
        List<ExtractionCandidate> candidates = findCandidates(cycle);

        if (candidates.isEmpty()) {
            logger.info("No methods found to extract in cycle {}", cycle);
            return false;
        }

        // Create ONE Mediator for the cycle
        // Name: Concatenate all bean simple names + "Operations"? Or just the first two
        // (OrderServicePaymentServiceOperations match test)?
        // The test matches "OrderServicePaymentServiceOperations".
        // Since candidates might be in any order, we should probably stick to the order
        // in processing or cycle list.
        // Let's use the first candidate and its dependency to name it, or if multiple,
        // concatenate unique names.

        String mediatorName = generateMediatorName(cycle);
        String packageName = candidates.getFirst().sourceClass.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(NodeWithName::getNameAsString).orElse("");

        createMediatorClass(packageName, mediatorName, candidates);

        // Refactor source classes
        Map<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> methodsByClass = new HashMap<>();
        Map<ClassOrInterfaceDeclaration, Set<String>> fieldsToRemove = new HashMap<>();

        for (ExtractionCandidate cand : candidates) {
            methodsByClass.computeIfAbsent(cand.sourceClass, k -> new HashSet<>()).addAll(cand.methods);
            fieldsToRemove.computeIfAbsent(cand.sourceClass, k -> new HashSet<>()).add(cand.dependencyFieldName);
        }

        for (Map.Entry<ClassOrInterfaceDeclaration, Set<MethodDeclaration>> entry : methodsByClass.entrySet()) {
            refactorOriginalClass(entry.getKey(), entry.getValue(), mediatorName,
                    fieldsToRemove.get(entry.getKey()));
            entry.getKey().findCompilationUnit()
                    .ifPresent(modifiedCUs::add);
        }

        return true;
    }

    private List<ExtractionCandidate> findCandidates(List<String> cycle) {
        List<ExtractionCandidate> candidates = new ArrayList<>();
        for (String beanName : cycle) {
            ClassOrInterfaceDeclaration beanClass = findClassDeclaration(beanName);
            if (beanClass == null) {
                logger.warn("Could not find class declaration for bean: {}", beanName);
                continue;
            }

            for (String otherBean : cycle) {
                if (beanName.equals(otherBean))
                    continue;

                Optional<FieldDeclaration> depField = findDependencyField(beanClass, otherBean);
                if (depField.isPresent()) {
                    String fieldName = depField.get().getVariable(0).getNameAsString();
                    Set<MethodDeclaration> methods = findMethodsUsing(beanClass, fieldName);

                    if (!methods.isEmpty()) {
                        collectTransitiveDependencies(beanClass, methods);

                        ExtractionCandidate candidate = new ExtractionCandidate();
                        candidate.sourceClass = beanClass;
                        candidate.methods = methods;
                        candidate.dependencyBeanName = otherBean;
                        candidate.dependencyFieldName = fieldName;
                        candidates.add(candidate);
                    }
                }
            }
        }
        return candidates;
    }

    private String generateMediatorName(List<String> cycle) {
        // Try to match test expectation: OrderServicePaymentServiceOperations
        // Cycle is OrderService, PaymentService.
        // Candidates will be (OrderService->PaymentService) and
        // (PaymentService->OrderService).
        // If we concat non-duplicate simple names from cycle in order:
        StringBuilder sb = new StringBuilder();
        for (String bean : cycle) {
            sb.append(getSimpleClassName(bean));
        }
        sb.append("Operations");
        return sb.toString();
    }

    private void collectTransitiveDependencies(ClassOrInterfaceDeclaration clazz,
                                               Set<MethodDeclaration> methods) {

        Set<MethodDeclaration> workingSet = new HashSet<>(methods);
        Set<MethodDeclaration> processed = new HashSet<>();

        while (!workingSet.isEmpty()) {
            MethodDeclaration current = workingSet.iterator().next();
            workingSet.remove(current);
            processed.add(current);

            current.findAll(MethodCallExpr.class).forEach(mce -> {
                if (isLocalMethodCall(mce)) {
                    clazz.getMethodsByName(mce.getNameAsString()).stream()
                            .filter(m -> m.getParameters().size() == mce.getArguments().size())
                            .findFirst()
                            .ifPresent(target -> {
                                if (!processed.contains(target) && !methods.contains(target)) {
                                    methods.add(target);
                                    workingSet.add(target);
                                }
                            });
                }
            });
        }
    }

    private boolean isLocalMethodCall(MethodCallExpr mce) {
        Optional<com.github.javaparser.ast.expr.Expression> scope = mce.getScope();
        return scope.isEmpty() || scope.get() instanceof ThisExpr;
    }

    private Optional<FieldDeclaration> findDependencyField(ClassOrInterfaceDeclaration clazz,
            String dependencyBeanType) {
        for (FieldDeclaration field : clazz.getFields()) {
            if (field.getVariable(0).getType().asString().endsWith(getSimpleClassName(dependencyBeanType))) {
                return Optional.of(field);
            }
        }
        return Optional.empty();
    }

    private Set<MethodDeclaration> findMethodsUsing(ClassOrInterfaceDeclaration clazz, String fieldName) {
        return clazz.getMethods().stream()
                .filter(m -> methodUsesField(m, fieldName))
                .collect(Collectors.toSet());
    }

    private boolean methodUsesField(MethodDeclaration method, String fieldName) {
        if (method.getBody().isEmpty())
            return false;

        List<MethodCallExpr> methodCalls = method.findAll(MethodCallExpr.class);
        for (MethodCallExpr mce : methodCalls) {
            if (isMethodCallOnField(mce, fieldName)) {
                return true;
            }
        }
        return method.findAll(NameExpr.class).stream()
                .anyMatch(n -> n.getNameAsString().equals(fieldName));
    }

    private void createMediatorClass(String packageName, String mediatorName, List<ExtractionCandidate> candidates) {

        CompilationUnit cu = new CompilationUnit();
        if (!packageName.isEmpty()) {
            cu.setPackageDeclaration(packageName);
        }

        ClassOrInterfaceDeclaration mediator = cu.addClass(mediatorName);
        mediator.addModifier(Modifier.Keyword.PUBLIC);
        mediator.addAnnotation(new MarkerAnnotationExpr(new Name("Service")));
        cu.addImport("org.springframework.stereotype.Service");
        cu.addImport("org.springframework.beans.factory.annotation.Autowired");

        cu.addImport("org.springframework.context.annotation.Lazy");

        Set<String> addedFields = new HashSet<>();

        for (ExtractionCandidate cand : candidates) {
            // Add imports from source
            cand.sourceClass.findCompilationUnit().ifPresent(sourceCu ->
                sourceCu.getImports().forEach(cu::addImport)
            );

            // Add dependency field if not exists
            String depStruct = getSimpleClassName(cand.dependencyBeanName); // Type

            // We need to add fields for dependencies needed by extracted methods.
            // But also, we need to add fields for the source classes themselves?
            // If Method M from A uses B. M is moved to Mediator. Mediator needs B.
            // We add field B to Mediator.

            if (!addedFields.contains(cand.dependencyFieldName)) {
                FieldDeclaration fd = mediator.addField(depStruct, cand.dependencyFieldName, Modifier.Keyword.PRIVATE);
                fd.addAnnotation("Autowired");
                fd.addAnnotation("Lazy");
                addedFields.add(cand.dependencyFieldName);
            }

            // Move methods
            for (MethodDeclaration method : cand.methods) {
                // Avoid duplicates (if two classes have same method name? Unlikely or
                // overloading)
                // Just add them.
                MethodDeclaration newMethod = method.clone();
                mediator.addMember(newMethod);

                if (newMethod.isPrivate()) {
                    newMethod.setPrivate(false);
                    newMethod.setPublic(true);
                }
            }
        }

        String fqn = packageName.isEmpty() ? mediatorName : packageName + "." + mediatorName;
        generatedMediators.put(fqn, cu);
        modifiedCUs.add(cu);
        Graph.getDependencies().put(fqn, cu);
    }

    private void refactorOriginalClass(ClassOrInterfaceDeclaration clazz, Set<MethodDeclaration> extractedMethods,
                                       String mediatorName, Set<String> fieldsToRemove) {

        for (MethodDeclaration m : extractedMethods) {
            m.remove();
        }

        if (fieldsToRemove != null) {
            for (String fieldName : fieldsToRemove) {
                clazz.getFieldByName(fieldName).ifPresent(FieldDeclaration::remove);
            }
        }

        String fieldName = Character.toLowerCase(mediatorName.charAt(0)) + mediatorName.substring(1);
        FieldDeclaration fd = clazz.addField(mediatorName, fieldName, Modifier.Keyword.PRIVATE);
        fd.addAnnotation("Autowired");

        for (MethodDeclaration method : clazz.getMethods()) {
            method.findAll(MethodCallExpr.class).forEach(mce -> {
                String calledName = mce.getNameAsString();
                boolean isExtracted = extractedMethods.stream().anyMatch(em -> em.getNameAsString().equals(calledName));

                if (isExtracted && isLocalMethodCall(mce)) {
                    mce.setScope(new NameExpr(fieldName));
                }
            });
        }
    }

    private String getSimpleClassName(String fqn) {
        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
    }
}
