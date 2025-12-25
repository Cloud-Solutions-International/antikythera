package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a dependency graph of Spring beans for cycle detection.
 *
 * <p>
 * Analyzes field injection, constructor injection, setter injection,
 * and @Configuration/@Bean method dependencies.
 * </p>
 */
public class BeanDependencyGraph {

    private final Map<String, Set<BeanDependency>> adjacencyList = new HashMap<>();
    private final Map<String, Set<String>> simpleGraph = new HashMap<>();

    /**
     * Build the dependency graph from all parsed compilation units.
     * Call AbstractCompiler.preProcess() before invoking this method.
     */
    public void build() {
        Map<String, TypeWrapper> resolvedTypes = AntikytheraRunTime.getResolvedTypes();

        for (Map.Entry<String, TypeWrapper> entry : resolvedTypes.entrySet()) {
            TypeWrapper wrapper = entry.getValue();

            // Skip entities - they don't participate in DI cycles
            if (wrapper.isEntity()) {
                continue;
            }

            // Only process Spring beans
            if (!isSpringBean(wrapper)) {
                continue;
            }

            String beanFqn = entry.getKey();
            TypeDeclaration<?> type = wrapper.getType();

            if (type instanceof ClassOrInterfaceDeclaration cid) {
                analyzeFieldInjection(beanFqn, cid);
                analyzeConstructorInjection(beanFqn, cid);
                analyzeSetterInjection(beanFqn, cid);

                // Check for @Configuration class for @Bean methods
                if (isConfiguration(cid)) {
                    analyzeBeanMethods(beanFqn, cid);
                }
                
                // Ensure the bean is in the graph even if it has no outgoing dependencies
                // (it might be a target of dependencies from other beans)
                simpleGraph.putIfAbsent(beanFqn, new HashSet<>());
            }
        }
    }

    /**
     * Get the adjacency list for cycle detection algorithms.
     */
    public Map<String, Set<String>> getSimpleGraph() {
        return simpleGraph;
    }

    /**
     * Get detailed dependency information.
     */
    public Map<String, Set<BeanDependency>> getDependencies() {
        return adjacencyList;
    }

    /**
     * Check if a node (field, parameter, or method) has @Lazy annotation.
     * Checks both simple name and fully qualified name.
     */
    private boolean hasLazyAnnotation(com.github.javaparser.ast.Node node) {
        if (node instanceof FieldDeclaration field) {
            return field.getAnnotationByName("Lazy").isPresent() ||
                   field.getAnnotationByName("org.springframework.context.annotation.Lazy").isPresent() ||
                   field.getAnnotations().stream().anyMatch(a -> 
                       a.getNameAsString().equals("Lazy") || 
                       a.getNameAsString().equals("org.springframework.context.annotation.Lazy"));
        } else if (node instanceof com.github.javaparser.ast.body.Parameter param) {
            return param.getAnnotationByName("Lazy").isPresent() ||
                   param.getAnnotationByName("org.springframework.context.annotation.Lazy").isPresent() ||
                   param.getAnnotations().stream().anyMatch(a -> 
                       a.getNameAsString().equals("Lazy") || 
                       a.getNameAsString().equals("org.springframework.context.annotation.Lazy"));
        } else if (node instanceof MethodDeclaration method) {
            return method.getAnnotationByName("Lazy").isPresent() ||
                   method.getAnnotationByName("org.springframework.context.annotation.Lazy").isPresent() ||
                   method.getAnnotations().stream().anyMatch(a -> 
                       a.getNameAsString().equals("Lazy") || 
                       a.getNameAsString().equals("org.springframework.context.annotation.Lazy"));
        }
        return false;
    }

    /**
     * Check if a TypeWrapper represents a Spring bean.
     */
    private boolean isSpringBean(TypeWrapper wrapper) {
        return wrapper.isService() || wrapper.isController()
                || wrapper.isComponent() || isConfiguration(wrapper);
    }

    /**
     * Check if the class is a @Configuration class.
     */
    private boolean isConfiguration(TypeWrapper wrapper) {
        TypeDeclaration<?> type = wrapper.getType();
        if (type != null) {
            return type.getAnnotationByName("Configuration").isPresent();
        }
        return false;
    }

    private boolean isConfiguration(ClassOrInterfaceDeclaration cid) {
        return cid.getAnnotationByName("Configuration").isPresent();
    }

    /**
     * Analyze fields with @Autowired, @Inject, or @Resource.
     * Skip fields with @Lazy annotation as they don't participate in cycles.
     */
    private void analyzeFieldInjection(String beanFqn, ClassOrInterfaceDeclaration cid) {
        for (FieldDeclaration field : cid.getFields()) {
            if (!isInjectedField(field)) {
                continue;
            }

            // Skip @Lazy fields - they don't participate in instantiation cycles
            if (hasLazyAnnotation(field)) {
                continue;
            }

            field.getVariables().forEach(var -> {
                String fieldName = var.getNameAsString();
                Type fieldType = var.getType();
                String targetFqn = resolveTypeFqn(fieldType, cid);

                if (targetFqn != null && !targetFqn.equals(beanFqn)) {
                    List<String> qualifiers = extractQualifiers(field);
                    addDependency(beanFqn, targetFqn, InjectionType.FIELD, field, fieldName, qualifiers);
                }
            });
        }
    }

    /**
     * Analyze constructors for injection (single constructor or @Autowired marked).
     * Skip parameters with @Lazy annotation as they don't participate in cycles.
     */
    private void analyzeConstructorInjection(String beanFqn, ClassOrInterfaceDeclaration cid) {
        List<ConstructorDeclaration> constructors = cid.getConstructors();

        ConstructorDeclaration injectedConstructor = null;

        if (constructors.size() == 1) {
            // Single constructor = implicitly injected
            injectedConstructor = constructors.get(0);
        } else {
            // Multiple constructors - look for @Autowired
            for (ConstructorDeclaration ctor : constructors) {
                if (ctor.getAnnotationByName("Autowired").isPresent()) {
                    injectedConstructor = ctor;
                    break;
                }
            }
        }

        if (injectedConstructor == null) {
            return;
        }

        for (Parameter param : injectedConstructor.getParameters()) {
            // Skip @Lazy parameters - they don't participate in instantiation cycles
            if (hasLazyAnnotation(param)) {
                continue;
            }

            String paramName = param.getNameAsString();
            Type paramType = param.getType();
            String targetFqn = resolveTypeFqn(paramType, cid);

            if (targetFqn != null && !targetFqn.equals(beanFqn)) {
                List<String> qualifiers = extractQualifiers(param);
                addDependency(beanFqn, targetFqn, InjectionType.CONSTRUCTOR, injectedConstructor, paramName,
                        qualifiers);
            }
        }
    }

    /**
     * Analyze setter methods with @Autowired.
     * Skip setters with @Lazy annotation as they don't participate in cycles.
     */
    private void analyzeSetterInjection(String beanFqn, ClassOrInterfaceDeclaration cid) {
        for (MethodDeclaration method : cid.getMethods()) {
            if (!method.getAnnotationByName("Autowired").isPresent()) {
                continue;
            }

            // Skip @Lazy setters - they don't participate in instantiation cycles
            if (hasLazyAnnotation(method)) {
                continue;
            }

            // Setter typically has one parameter
            if (method.getParameters().size() != 1) {
                continue;
            }

            Parameter param = method.getParameter(0);
            String paramName = param.getNameAsString();
            Type paramType = param.getType();
            String targetFqn = resolveTypeFqn(paramType, cid);

            if (targetFqn != null && !targetFqn.equals(beanFqn)) {
                List<String> qualifiers = extractQualifiers(method);
                addDependency(beanFqn, targetFqn, InjectionType.SETTER, method, paramName, qualifiers);
            }
        }
    }

    /**
     * Analyze @Bean methods in @Configuration classes.
     */
    private void analyzeBeanMethods(String configFqn, ClassOrInterfaceDeclaration cid) {
        for (MethodDeclaration method : cid.getMethods()) {
            if (!method.getAnnotationByName("Bean").isPresent()) {
                continue;
            }

            String beanMethodName = method.getNameAsString();

            for (Parameter param : method.getParameters()) {
                String paramName = param.getNameAsString();
                Type paramType = param.getType();
                String targetFqn = resolveTypeFqn(paramType, cid);

                if (targetFqn != null) {
                    List<String> qualifiers = extractQualifiers(param);
                    // The bean produced by this method depends on the parameter
                    addDependency(configFqn + "#" + beanMethodName, targetFqn,
                            InjectionType.BEAN_METHOD, method, paramName, qualifiers);
                }
            }
        }
    }

    private boolean isInjectedField(FieldDeclaration field) {
        return field.getAnnotationByName("Autowired").isPresent()
                || field.getAnnotationByName("Inject").isPresent()
                || field.getAnnotationByName("Resource").isPresent();
    }

    private List<String> extractQualifiers(FieldDeclaration field) {
        List<String> qualifiers = new ArrayList<>();
        extractQualifierValue(field.getAnnotationByName("Qualifier")).ifPresent(qualifiers::add);
        extractQualifierValue(field.getAnnotationByName("Resource")).ifPresent(qualifiers::add);
        return qualifiers;
    }

    private List<String> extractQualifiers(Parameter param) {
        List<String> qualifiers = new ArrayList<>();
        extractQualifierValue(param.getAnnotationByName("Qualifier")).ifPresent(qualifiers::add);
        extractQualifierValue(param.getAnnotationByName("Resource")).ifPresent(qualifiers::add);
        return qualifiers;
    }

    private List<String> extractQualifiers(MethodDeclaration method) {
        List<String> qualifiers = new ArrayList<>();
        extractQualifierValue(method.getAnnotationByName("Qualifier")).ifPresent(qualifiers::add);
        return qualifiers;
    }

    private Optional<String> extractQualifierValue(Optional<AnnotationExpr> annotation) {
        if (annotation.isEmpty()) {
            return Optional.empty();
        }
        AnnotationExpr ann = annotation.get();
        if (ann instanceof SingleMemberAnnotationExpr sma) {
            return Optional.of(sma.getMemberValue().toString().replace("\"", ""));
        }
        if (ann instanceof NormalAnnotationExpr na) {
            for (MemberValuePair pair : na.getPairs()) {
                if ("value".equals(pair.getNameAsString()) || "name".equals(pair.getNameAsString())) {
                    return Optional.of(pair.getValue().toString().replace("\"", ""));
                }
            }
        }
        return Optional.empty();
    }

    private String resolveTypeFqn(Type type, ClassOrInterfaceDeclaration context) {
        try {
            String typeName;
            
            // For ClassOrInterfaceType (including parameterized types), extract the raw type name
            if (type instanceof ClassOrInterfaceType classOrInterfaceType) {
                // Get the name without type parameters
                typeName = classOrInterfaceType.getNameAsString();
            } else {
                // For other types, use asString() and extract raw type name if parameterized
                typeName = type.asString();
                
                // If it's a parameterized type, extract just the raw type name
                if (typeName.contains("<")) {
                    typeName = typeName.substring(0, typeName.indexOf('<')).trim();
                }
                
                // Also handle array types (e.g., "String[]" -> "String")
                if (typeName.contains("[")) {
                    typeName = typeName.substring(0, typeName.indexOf('[')).trim();
                }
            }
            
            // First try to find in the current compilation unit
            String fqn = AbstractCompiler.findFullyQualifiedName(context.findCompilationUnit().orElse(null),
                    typeName);
            
            // If not found, check if it's already in AntikytheraRunTime (might be in a different package)
            if (fqn == null) {
                // Check all resolved types for a match
                for (String resolvedFqn : AntikytheraRunTime.getResolvedTypes().keySet()) {
                    if (resolvedFqn.endsWith("." + typeName) || resolvedFqn.equals(typeName)) {
                        fqn = resolvedFqn;
                        break;
                    }
                }
            }
            
            return fqn;
        } catch (Exception e) {
            return null;
        }
    }

    private void addDependency(String from, String to, InjectionType type,
            com.github.javaparser.ast.Node node, String fieldName,
            List<String> qualifiers) {
        BeanDependency dep = new BeanDependency(from, to, type, node, fieldName, qualifiers);
        adjacencyList.computeIfAbsent(from, k -> new HashSet<>()).add(dep);
        simpleGraph.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }
}
