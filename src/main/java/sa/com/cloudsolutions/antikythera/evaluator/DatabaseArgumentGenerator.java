package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.body.Parameter;
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
            ResultSet rs = query.getSimplifiedResultSet();
            if (rs.next()) {
                prepared = true;
            }
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
