package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
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

    public static final String LAZY_ANNOTATION = "org.springframework.context.annotation.Lazy";
    public static final String LAZY = "Lazy";
    public static final String QUALIFIER = "Qualifier";
    public static final String RESOURCE = "Resource";
    public static final String AUTOWIRED = "Autowired";
    private final Map<String, Set<BeanDependency>> adjacencyList = new HashMap<>();
    private final Map<String, Set<String>> simpleGraph = new HashMap<>();
    private final List<PostConstructWarning> postConstructWarnings = new ArrayList<>();

    /**
     * Warning for @PostConstruct methods that use cycle-causing dependencies.
     * These require method extraction, not @Lazy annotation.
     */
    public record PostConstructWarning(String beanFqn, String methodName, String usedField, String usedFieldType) {
        @Override
        public String toString() {
            return String.format("%s.%s() uses %s (%s) - @Lazy won't work, needs method extraction",
                    beanFqn.substring(beanFqn.lastIndexOf('.') + 1), methodName, usedField,
                    usedFieldType.substring(usedFieldType.lastIndexOf('.') + 1));
        }
    }

    /**
     * Build the dependency graph from all parsed compilation units.
     * Call AbstractCompiler.preProcess() before invoking this method.
     */
    public void build() {
        Map<String, TypeWrapper> resolvedTypes = AntikytheraRunTime.getResolvedTypes();

        for (Map.Entry<String, TypeWrapper> entry : resolvedTypes.entrySet()) {
            TypeWrapper wrapper = entry.getValue();

            if (wrapper.isEntity() || !isSpringBean(wrapper)) {
                continue;
            }

            String beanFqn = entry.getKey();
            TypeDeclaration<?> type = wrapper.getType();

            if (type instanceof ClassOrInterfaceDeclaration cid) {
                // Get the compilation unit from AntikytheraRunTime for better reliability
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(beanFqn);
                if (cu == null) {
                    // Fallback to finding it from the type
                    cu = cid.findCompilationUnit().orElse(null);
                }

                analyzeFieldInjection(beanFqn, cid, cu);
                analyzeConstructorInjection(beanFqn, cid, cu);
                analyzeSetterInjection(beanFqn, cid, cu);

                // Check for @Configuration class for @Bean methods
                if (isConfiguration(cid)) {
                    analyzeBeanMethods(beanFqn, cid, cu);
                }

                // Analyze @PostConstruct methods for cycle warnings
                analyzePostConstructMethods(beanFqn, cid, cu);

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
     * Get warnings for @PostConstruct methods that use cycle dependencies.
     * These require method extraction strategy, not @Lazy.
     */
    public List<PostConstructWarning> getPostConstructWarnings() {
        return postConstructWarnings;
    }

    /**
     * Check if a node (field, parameter, or method) has @Lazy annotation.
     * Checks both simple name and fully qualified name.
     */
    private boolean hasLazyAnnotation(com.github.javaparser.ast.Node node) {
        if (node instanceof FieldDeclaration field) {
            return field.getAnnotationByName(LAZY).isPresent() ||
                    field.getAnnotationByName(LAZY_ANNOTATION).isPresent() ||
                    field.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(LAZY) ||
                            a.getNameAsString().equals(LAZY_ANNOTATION));
        } else if (node instanceof com.github.javaparser.ast.body.Parameter param) {
            return param.getAnnotationByName(LAZY).isPresent() ||
                    param.getAnnotationByName(LAZY_ANNOTATION).isPresent() ||
                    param.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(LAZY) ||
                            a.getNameAsString().equals(LAZY_ANNOTATION));
        } else if (node instanceof MethodDeclaration method) {
            return method.getAnnotationByName(LAZY).isPresent() ||
                    method.getAnnotationByName(LAZY_ANNOTATION).isPresent() ||
                    method.getAnnotations().stream().anyMatch(a -> a.getNameAsString().equals(LAZY) ||
                            a.getNameAsString().equals(LAZY_ANNOTATION));
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
    private void analyzeFieldInjection(String beanFqn, ClassOrInterfaceDeclaration cid, CompilationUnit cu) {
        for (FieldDeclaration field : cid.getFields()) {
            if (hasLazyAnnotation(field) || !isInjectedField(field)) {
                continue;
            }

            field.getVariables().forEach(variable -> {
                String fieldName = variable.getNameAsString();
                Type fieldType = variable.getType();

                String targetFqn = resolveTypeFqn(fieldType, cid, cu);

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
    private void analyzeConstructorInjection(String beanFqn, ClassOrInterfaceDeclaration cid, CompilationUnit cu) {
        List<ConstructorDeclaration> constructors = cid.getConstructors();

        ConstructorDeclaration injectedConstructor = null;

        if (constructors.size() == 1) {
            // Single constructor = implicitly injected
            injectedConstructor = constructors.get(0);
        } else {
            // Multiple constructors - look for @Autowired
            for (ConstructorDeclaration ctor : constructors) {
                if (ctor.getAnnotationByName(AUTOWIRED).isPresent()) {
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
            String targetFqn = resolveTypeFqn(paramType, cid, cu);

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
    private void analyzeSetterInjection(String beanFqn, ClassOrInterfaceDeclaration cid, CompilationUnit cu) {
        for (MethodDeclaration method : cid.getMethods()) {
            if (method.getAnnotationByName(AUTOWIRED).isEmpty() || hasLazyAnnotation(method) || method.getParameters().size() != 1) {
                continue;
            }

            Parameter param = method.getParameter(0);
            String paramName = param.getNameAsString();
            Type paramType = param.getType();
            String targetFqn = resolveTypeFqn(paramType, cid, cu);

            if (targetFqn != null && !targetFqn.equals(beanFqn)) {
                List<String> qualifiers = extractQualifiers(method);
                addDependency(beanFqn, targetFqn, InjectionType.SETTER, method, paramName, qualifiers);
            }
        }
    }

    /**
     * Analyze @Bean methods in @Configuration classes.
     */
    private void analyzeBeanMethods(String configFqn, ClassOrInterfaceDeclaration cid, CompilationUnit cu) {
        for (MethodDeclaration method : cid.getMethods()) {
            if (method.getAnnotationByName("Bean").isEmpty()) {
                continue;
            }

            String beanMethodName = method.getNameAsString();

            for (Parameter param : method.getParameters()) {
                String paramName = param.getNameAsString();
                Type paramType = param.getType();
                String targetFqn = resolveTypeFqn(paramType, cid, cu);

                if (targetFqn != null) {
                    List<String> qualifiers = extractQualifiers(param);
                    // The bean produced by this method depends on the parameter
                    addDependency(configFqn + "#" + beanMethodName, targetFqn,
                            InjectionType.BEAN_METHOD, method, paramName, qualifiers);
                }
            }
        }
    }

    /**
     * Analyze @PostConstruct methods to find dependencies used during
     * initialization.
     * These require method extraction strategy since @Lazy won't help.
     */
    private void analyzePostConstructMethods(String beanFqn, ClassOrInterfaceDeclaration cid, CompilationUnit cu) {
        // Get all injected field names in this class
        Set<String> injectedFieldNames = new HashSet<>();
        Map<String, String> fieldTypeMap = new HashMap<>();

        for (FieldDeclaration field : cid.getFields()) {
            if (isInjectedField(field)) {
                field.getVariables().forEach(variable -> {
                    String fieldName = variable.getNameAsString();
                    injectedFieldNames.add(fieldName);
                    String typeFqn = resolveTypeFqn(variable.getType(), cid, cu);
                    if (typeFqn != null) {
                        fieldTypeMap.put(fieldName, typeFqn);
                    }
                });
            }
        }

        if (injectedFieldNames.isEmpty()) {
            return;
        }

        // Find @PostConstruct methods
        for (MethodDeclaration method : cid.getMethods()) {
            boolean isPostConstruct = method.getAnnotationByName("PostConstruct").isPresent()
                    || method.getAnnotationByName("javax.annotation.PostConstruct").isPresent()
                    || method.getAnnotationByName("jakarta.annotation.PostConstruct").isPresent();

            if (!isPostConstruct) {
                continue;
            }

            // Check if this method uses any injected fields
            method.findAll(com.github.javaparser.ast.expr.NameExpr.class).forEach(nameExpr -> {
                String name = nameExpr.getNameAsString();
                if (injectedFieldNames.contains(name)) {
                    String fieldType = fieldTypeMap.getOrDefault(name, "unknown");
                    postConstructWarnings.add(new PostConstructWarning(
                            beanFqn, method.getNameAsString(), name, fieldType));
                }
            });

            // Also check for this.fieldName patterns
            method.findAll(com.github.javaparser.ast.expr.FieldAccessExpr.class).forEach(fieldAccess -> {
                if (fieldAccess.getScope().isThisExpr()) {
                    String name = fieldAccess.getNameAsString();
                    if (injectedFieldNames.contains(name)) {
                        String fieldType = fieldTypeMap.getOrDefault(name, "unknown");
                        postConstructWarnings.add(new PostConstructWarning(
                                beanFqn, method.getNameAsString(), name, fieldType));
                    }
                }
            });
        }
    }

    private boolean isInjectedField(FieldDeclaration field) {
        return field.getAnnotationByName(AUTOWIRED).isPresent()
                || field.getAnnotationByName("Inject").isPresent()
                || field.getAnnotationByName(RESOURCE).isPresent();
    }

    private List<String> extractQualifiers(FieldDeclaration field) {
        List<String> qualifiers = new ArrayList<>();
        extractQualifierValue(field.getAnnotationByName(QUALIFIER)).ifPresent(qualifiers::add);
        extractQualifierValue(field.getAnnotationByName(RESOURCE)).ifPresent(qualifiers::add);
        return qualifiers;
    }

    private List<String> extractQualifiers(Parameter param) {
        List<String> qualifiers = new ArrayList<>();
        extractQualifierValue(param.getAnnotationByName(QUALIFIER)).ifPresent(qualifiers::add);
        extractQualifierValue(param.getAnnotationByName(RESOURCE)).ifPresent(qualifiers::add);
        return qualifiers;
    }

    private List<String> extractQualifiers(MethodDeclaration method) {
        List<String> qualifiers = new ArrayList<>();
        extractQualifierValue(method.getAnnotationByName(QUALIFIER)).ifPresent(qualifiers::add);
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

    /**
     * Resolve type FQN using AbstractCompiler utility.
     * Delegates to AbstractCompiler.resolveTypeFqn() for consistent type
     * resolution.
     */
    private String resolveTypeFqn(Type type, ClassOrInterfaceDeclaration context, CompilationUnit cu) {
        return AbstractCompiler.resolveTypeFqn(type, context, cu);
    }

    private void addDependency(String from, String to, InjectionType type,
            com.github.javaparser.ast.Node node, String fieldName,
            List<String> qualifiers) {
        BeanDependency dep = new BeanDependency(from, to, type, node, fieldName, qualifiers);
        adjacencyList.computeIfAbsent(from, k -> new HashSet<>()).add(dep);
        simpleGraph.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }
}
