package sa.com.cloudsolutions.antikythera.parser.converter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating query converters.
 * Uses HQLParserAdapter (ANTLR4-based) as the default implementation.
 */
public class QueryConverterFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryConverterFactory.class);
    
    /**
     * Creates a query converter using the ANTLR4-based HQL parser.
     * 
     * @return JpaQueryConverter instance (HQLParserAdapter)
     */
    public static JpaQueryConverter createConverter() {
        logger.debug("Using HQLParserAdapter (ANTLR4-based parser)");
        return new HQLParserAdapter();
    }
    
    /**
     * Creates a query converter with explicit dialect specification.
     * 
     * @param dialect The database dialect to use
     * @return JpaQueryConverter instance (HQLParserAdapter)
     */
    public static JpaQueryConverter createConverter(DatabaseDialect dialect) {
        logger.debug("Using HQLParserAdapter (ANTLR4-based parser) with dialect: {}", 
                   dialect != null ? dialect.getDisplayName() : "default");
        return new HQLParserAdapter();
    }
}
