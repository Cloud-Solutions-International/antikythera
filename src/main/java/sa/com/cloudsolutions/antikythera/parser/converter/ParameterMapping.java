package sa.com.cloudsolutions.antikythera.parser.converter;


/**
 * Represents the mapping of a parameter from the original JPA query to the converted native SQL.
 * <p>
 * This class tracks how named parameters in JPA queries are converted to positional
 * parameters in native SQL, along with type and column information.
 * <p>
 * Requirements addressed: 3.5
 */
public record ParameterMapping(String originalName, int position, Class<?> type, String columnName) {
}
