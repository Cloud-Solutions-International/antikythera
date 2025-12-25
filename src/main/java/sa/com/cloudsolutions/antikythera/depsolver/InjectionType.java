package sa.com.cloudsolutions.antikythera.depsolver;

/**
 * Types of dependency injection in Spring beans.
 * Used by the cycle detection and elimination tool.
 */
public enum InjectionType {
    /**
     * Field injection using @Autowired, @Inject, or @Resource on a field.
     */
    FIELD,

    /**
     * Constructor injection - single constructor or @Autowired constructor.
     */
    CONSTRUCTOR,

    /**
     * Setter injection using @Autowired on a setter method.
     */
    SETTER,

    /**
     * Bean method in a Configuration class (using {@code @Bean} and {@code @Configuration} annotations).
     */
    BEAN_METHOD
}
