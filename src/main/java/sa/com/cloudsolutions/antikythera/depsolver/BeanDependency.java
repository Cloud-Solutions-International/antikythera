package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.Node;
import java.util.List;

/**
 * Represents a dependency edge between two Spring beans.
 * Used by the cycle detection and elimination tool.
 *
 * @param fromBean      Fully qualified name of the bean that has the dependency
 * @param targetBean    Fully qualified name of the bean being depended on
 * @param injectionType How the dependency is injected (FIELD, CONSTRUCTOR,
 *                      SETTER, BEAN_METHOD)
 * @param astNode       The AST node representing this dependency (for
 *                      modification)
 * @param fieldName     The field/parameter name (e.g., "paymentService")
 * @param qualifiers    @Qualifier or @Resource name values to preserve during
 *                      modification
 */
public record BeanDependency(
        String fromBean,
        String targetBean,
        InjectionType injectionType,
        Node astNode,
        String fieldName,
        List<String> qualifiers) {
    /**
     * Create a dependency without qualifiers.
     */
    public BeanDependency(String fromBean, String targetBean, InjectionType injectionType,
            Node astNode, String fieldName) {
        this(fromBean, targetBean, injectionType, astNode, fieldName, List.of());
    }

    /**
     * Returns true if this is a "hard" cycle that Spring cannot resolve at all.
     * Constructor and @Bean cycles are always hard cycles.
     */
    public boolean isHardCycle() {
        return injectionType == InjectionType.CONSTRUCTOR
                || injectionType == InjectionType.BEAN_METHOD;
    }

    /**
     * Returns true if this dependency has qualifier annotations.
     */
    public boolean hasQualifiers() {
        return qualifiers != null && !qualifiers.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("%s -[%s]-> %s (%s)",
                fromBean.substring(fromBean.lastIndexOf('.') + 1),
                injectionType,
                targetBean.substring(targetBean.lastIndexOf('.') + 1),
                fieldName);
    }
}
