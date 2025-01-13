package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodArgument;
import sa.com.cloudsolutions.antikythera.generator.QueryMethodParameter;
import sa.com.cloudsolutions.antikythera.generator.RepositoryQuery;

import java.sql.SQLException;
import java.util.Optional;

public class DatabaseArgumentGenerator extends DummyArgumentGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseArgumentGenerator.class);

    /**
     * The last executed database query.
     */
    static RepositoryQuery query;

    private static boolean prepared;

    @Override
    public Variable mockParameter(String typeName) {
        Variable q = null;
        for(int i = 0 ; i < query.getMethodParameters().size() && q == null; i++) {
            QueryMethodArgument arg = query.getMethodArguments().get(i);

            if (arg.getArgument().isNameExpr()) {
                q = matchParameterAndArgument(typeName, i, arg);
            }
        }
        if (q == null) {
            return super.mockParameter(typeName);
        }
        return q;
    }

    private static Variable matchParameterAndArgument(String typeName, int i, QueryMethodArgument arg) {
        QueryMethodParameter param = query.getMethodParameters().get(i);

        String name = arg.getArgument().asNameExpr().getNameAsString();
        if (name.equals(typeName) && param.getColumnName() != null) {
            String[] parts = param.getColumnName().split("\\.");
            String col = parts.length > 1 ? parts[1] : parts[0];

            if (col.equals(param.getColumnName())) {
                Type type = param.getParameter().getType();
                if (type.isClassOrInterfaceType()) {
                    Optional<NodeList<Type>> typeArguments = type.asClassOrInterfaceType().getTypeArguments();
                    String t = typeArguments.isPresent() && typeArguments.get().getFirst().isPresent()
                            ? typeArguments.get().getFirst().get().asString()
                            : type.asClassOrInterfaceType().getNameAsString();
                    return getValueFromColumn(t, col);
                }
                else {
                    throw new RuntimeException("Unhandled");
                }
            }
        }
        return null;
    }

    private static Variable getValueFromColumn(String t, String col)  {
        try {
            return switch (t) {
                case "Integer", "int" ->  new Variable(query.getSimplifiedResultSet().getInt(col));
                case "String" -> new Variable(query.getSimplifiedResultSet().getString(col));
                case "boolean", "Boolean" -> new Variable(query.getSimplifiedResultSet().getBoolean(col));
                case "double", "Double" -> new Variable(query.getSimplifiedResultSet().getDouble(col));
                case "float", "Float" -> new Variable(query.getSimplifiedResultSet().getFloat(col));
                case "Long", "long" -> new Variable(query.getSimplifiedResultSet().getLong(col));
                case "short", "Short" -> new Variable(query.getSimplifiedResultSet().getShort(col));
                case "byte" -> new Variable(query.getSimplifiedResultSet().getByte(col));
                case "char", "Character" -> new Variable(query.getSimplifiedResultSet().getString(col).charAt(0));
                default -> new Variable(query.getSimplifiedResultSet().getObject(col));
            };
        } catch (SQLException e) {
            logger.debug(e.getMessage());
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

    private static void prepare()  {
        if (query != null && query.getSimplifiedResultSet() != null) {
            prepared = true;
        }
    }

    public static void setQuery(RepositoryQuery query) {
        DatabaseArgumentGenerator.query = query;
        prepared = false;
        prepare();
    }
}
