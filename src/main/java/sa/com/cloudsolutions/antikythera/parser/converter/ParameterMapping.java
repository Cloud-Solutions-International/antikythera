package sa.com.cloudsolutions.antikythera.parser.converter;

import java.util.Objects;

/**
 * Represents the mapping of a parameter from the original JPA query to the converted native SQL.
 * <p>
 * This class tracks how named parameters in JPA queries are converted to positional
 * parameters in native SQL, along with type and column information.
 * <p>
 * Requirements addressed: 3.5
 */
public record ParameterMapping(String originalName, int position, Class<?> type, String columnName) {

    @Override
    public String toString() {
        return "ParameterMapping{" +
                "originalName='" + originalName + '\'' +
                ", position=" + position +
                ", type=" + (type != null ? type.getSimpleName() : "null") +
                ", columnName='" + columnName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ParameterMapping that = (ParameterMapping) o;

        if (position != that.position) return false;
        if (!Objects.equals(originalName, that.originalName)) return false;
        if (!Objects.equals(type, that.type)) return false;
        return Objects.equals(columnName, that.columnName);
    }

}
