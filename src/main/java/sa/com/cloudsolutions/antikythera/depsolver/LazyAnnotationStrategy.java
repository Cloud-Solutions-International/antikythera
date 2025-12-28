package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for breaking circular dependencies by adding @Lazy annotation.
 * 
 * <p>
 * Works for field and setter injection. For constructor injection,
 * use SetterInjectionStrategy or InterfaceExtractionStrategy instead.
 * </p>
 */
public class LazyAnnotationStrategy extends AbstractExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LazyAnnotationStrategy.class);
    private static final String LAZY_ANNOTATION = "Lazy";
    private static final String LAZY_IMPORT = "org.springframework.context.annotation.Lazy";

    public LazyAnnotationStrategy() {
        super();
    }

    public LazyAnnotationStrategy(boolean dryRun) {
        super(dryRun);
    }

    /**
     * Apply @Lazy to a dependency edge.
     * 
     * @param edge The dependency edge to fix
     * @return true if @Lazy was successfully added
     */
    public boolean apply(BeanDependency edge) {
        Node astNode = edge.astNode();
        if (astNode == null) {
            return false;
        }

        boolean result = switch (edge.injectionType()) {
            case FIELD -> addLazyToField(astNode, edge);
            case SETTER -> addLazyToSetter(astNode, edge);
            case CONSTRUCTOR -> addLazyToConstructorParameter(astNode, edge);
            case BEAN_METHOD -> addLazyToBeanMethodParameter(astNode, edge);
        };

        if (result) {
            logger.info("Added @Lazy to {}", edge);
        }

        return result;
    }

    /**
     * Add @Lazy to a field declaration.
     */
    private boolean addLazyToField(Node node, BeanDependency edge) {
        if (!(node instanceof FieldDeclaration field)) {
            logger.error("Expected FieldDeclaration but got: {}", node.getClass().getSimpleName());
            return false;
        }

        // Check if already has @Lazy
        if (field.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            logger.info("Already has @Lazy: {}", edge);
            return true;
        }

        // Add @Lazy annotation
        field.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        field.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add @Lazy to a setter method's parameter.
     */
    private boolean addLazyToSetter(Node node, BeanDependency edge) {
        if (!(node instanceof MethodDeclaration method)) {
            logger.error("Expected MethodDeclaration but got: {}", node.getClass().getSimpleName());
            return false;
        }

        // Check if already has @Lazy on method
        if (method.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            logger.info("Already has @Lazy: {}", edge);
            return true;
        }

        // Add @Lazy to the setter method (not the parameter)
        method.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        method.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add @Lazy to a constructor parameter.
     * Spring supports @Lazy on constructor parameters to break cycles.
     */
    private boolean addLazyToConstructorParameter(Node node, BeanDependency edge) {
        if (!(node instanceof ConstructorDeclaration ctor)) {
            logger.error("Expected ConstructorDeclaration but got: {}", node.getClass().getSimpleName());
            return false;
        }

        String paramName = edge.fieldName();
        Parameter param = ctor.getParameters().stream()
                .filter(p -> p.getNameAsString().equals(paramName))
                .findFirst()
                .orElse(null);

        if (param == null) {
            logger.error("Parameter not found: {}", paramName);
            return false;
        }

        // Check if already has @Lazy
        if (param.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            logger.info("Already has @Lazy: {}", edge);
            return true;
        }

        // Add @Lazy to the parameter
        param.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        ctor.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add @Lazy to a @Bean method parameter.
     * Spring supports @Lazy on @Bean method parameters to break cycles.
     */
    private boolean addLazyToBeanMethodParameter(Node node, BeanDependency edge) {
        if (!(node instanceof MethodDeclaration method)) {
            logger.error("Expected MethodDeclaration but got: {}", node.getClass().getSimpleName());
            return false;
        }

        // Verify this is a @Bean method
        if (method.getAnnotationByName("Bean").isEmpty()) {
            logger.error("Method is not a @Bean method: {}", method.getNameAsString());
            return false;
        }

        String paramName = edge.fieldName();
        Parameter param = method.getParameters().stream()
                .filter(p -> p.getNameAsString().equals(paramName))
                .findFirst()
                .orElse(null);

        if (param == null) {
            logger.error("Parameter not found in @Bean method: {}", paramName);
            return false;
        }

        // Check if already has @Lazy
        if (param.getAnnotationByName(LAZY_ANNOTATION).isPresent()) {
            logger.info("Already has @Lazy: {}", edge);
            return true;
        }

        // Add @Lazy to the parameter
        param.addAnnotation(new MarkerAnnotationExpr(new Name(LAZY_ANNOTATION)));

        // Ensure import exists
        method.findCompilationUnit().ifPresent(cu -> {
            addLazyImport(cu);
            modifiedCUs.add(cu);
        });

        return true;
    }

    /**
     * Add the @Lazy import if not already present.
     */
    private void addLazyImport(CompilationUnit cu) {
        addImport(cu, LAZY_IMPORT);
    }
}
