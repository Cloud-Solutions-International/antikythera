package sa.com.cloudsolutions.antikythera.generator;

/**
 * Enumeration of different query types supported by the system.
 * Used to categorize queries for appropriate AI analysis and optimization.
 */
public enum QueryType {
    /**
     * Native SQL queries specified with nativeQuery=true in @Query annotation.
     * These are direct SQL statements that bypass JPA/Hibernate query translation.
     */
    NATIVE_SQL,

    /**
     * Hibernate Query Language (HQL) queries specified in @Query annotation without nativeQuery=true.
     * These use Hibernate's object-oriented query language.
     */
    HQL,

    /**
     * Derived queries inferred from method name using Spring JPA naming conventions.
     * These are automatically generated based on method names like findBy*, countBy*, etc.
     */
    DERIVED
}