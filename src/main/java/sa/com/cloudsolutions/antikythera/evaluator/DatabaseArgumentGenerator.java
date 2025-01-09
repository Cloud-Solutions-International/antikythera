package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodParameter;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;
import sa.com.cloudsolutions.antikythera.parser.RepositoryParser;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseArgumentGenerator extends ArgumentGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseArgumentGenerator.class);

    /**
     * The last executed database query.
     */
    static RepositoryQuery query;

    private static boolean prepared;

    @Override
    public Variable mockParameter(String typeName) {
        try {
            for(int i = 0 ; i < query.getMethodParameters().size() ; i++) {
                QueryMethodParameter param = query.getMethodParameters().get(i);

                if (param.getColumnName() != null) {
                    String[] parts = param.getColumnName().split("\\.");
                    String col = parts.length > 1 ? parts[1] : parts[0];

                    if (col.equals(RepositoryParser.camelToSnake(typeName))) {
                        Type type = param.getParameter().getType();
                        if (type.isClassOrInterfaceType()) {
                            String t = type.asClassOrInterfaceType().getTypeArguments().isPresent()
                                    ? type.asClassOrInterfaceType().getTypeArguments().get().get(0).asString()
                                    : type.asClassOrInterfaceType().getNameAsString();
                            switch (t) {
                                case "Integer":
                                case "int":
                                    return new Variable(query.getSimplifiedResultSet().getInt(col));
                                case "String":
                                    return new Variable(query.getSimplifiedResultSet().getString(col));
                                case "boolean":
                                    return new Variable(query.getSimplifiedResultSet().getBoolean(col));
                                case "double":
                                    return new Variable(query.getSimplifiedResultSet().getDouble(col));
                                case "float":
                                    return new Variable(query.getSimplifiedResultSet().getFloat(col));
                                case "Long":
                                case "long":
                                    return new Variable(query.getSimplifiedResultSet().getLong(col));
                                case "short":
                                    return new Variable(query.getSimplifiedResultSet().getShort(col));
                                case "byte":
                                    return new Variable(query.getSimplifiedResultSet().getByte(col));
                                case "char":
                                    return new Variable(query.getSimplifiedResultSet().getString(col).charAt(0));
                            }
                        }
                        return new Variable(query.getSimplifiedResultSet().getObject(col));
                    }
                }
            }
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    /**
     * Replace PathVariable and RequestParam values with the values from the database.
     *
     * Mapping parameters works like this.
     *    Request or path parameter becomes an argument to a method call.
     *    The argument in the method call becomes a parameter for a placeholder
     *    The placeholder may have been removed though!
     */
    @Override
    public void generateArgument(Parameter param) {
        if (prepared) {
            Variable v = mockParameter(param.getNameAsString());
            arguments.put(param.getNameAsString(), v);
            AntikytheraRunTime.push(v);
        }
    }

    private static void prepare() throws SQLException {
        if (query != null && query.getSimplifiedResultSet() != null) {
            prepared = true;
        }
    }

    public static void setQuery(RepositoryQuery query) {
        DatabaseArgumentGenerator.query = query;
        try {
            prepared = false;
            prepare();
        } catch (SQLException e) {
            logger.error(e.getMessage());
        }
    }
}
