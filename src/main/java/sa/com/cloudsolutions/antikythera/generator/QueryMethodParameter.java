package sa.com.cloudsolutions.antikythera.generator;


import com.github.javaparser.ast.body.Parameter;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parameter in the query method.
 *
 * Lets get the normenclature sorted out. Though arguments and parameters are used interchangeably
 * they have a subtle difference.
 *     An argument is the value that is passed to a function when it is called.
 *     A parameter refers to the variable that is used in the function declaration
 */
public class QueryMethodParameter {
    /**
     * The name of the argument as defined in the respository function
     */
    Parameter parameter;

    private String placeHolderName;

    /**
     * Typically column names in SQL are camel_cased.
     * Those mappings will be saved here.
     */
    private String columnName;

    /**
     * True if this column was removed from the WHERE clause or GROUP BY.
     */
    private boolean removed;

    private List<Integer> placeHolderId;

    /**
     * The position at which this parameter appears in the function parameters list.
     */
    int paramIndex;

    public QueryMethodParameter(Parameter parameter, int index) {
        this.parameter = parameter;
        parameter.getAnnotationByName("Param").ifPresent(a -> {
                    if(a.isStringLiteralExpr()) {
                        setPlaceHolderName(a.asStringLiteralExpr().asString());
                    }
                    else {
                        setPlaceHolderName(a.asSingleMemberAnnotationExpr().getMemberValue().asStringLiteralExpr().asString());
                    }
                }
        );
        setPlaceHolderId(new ArrayList<>());
        this.paramIndex = index;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    /**
     * The name of the jdbc named parameter.
     * These can typicall be identified in the JPARepository function by the @Param annotation.
     * If they are used along side custom queries, the query will have a place holder that starts
     * with the : character and matches the name of the parameter.
     * example:
     *     select u from User u where u.firstname = :firstname or u.lastname = :lastname
     *     User findByLastnameOrFirstname(@Param("lastname") String lastname,
     *                                  @Param("firstname") String firstname);
     */
    public String getPlaceHolderName() {
        return placeHolderName;
    }

    public void setPlaceHolderName(String placeHolderName) {
        this.placeHolderName = placeHolderName;
    }

    /**
     * Tracks usage of this column with numeric jdbc query parameters
     * Unlike named parameters, numeric parameters can appear in multiple places. THough the same
     * number can be used in two different places it's common to see programmers use two different ids.
     * These numbers will start counting from 1, in keeping with the usual jdbc convention.
     */
    public List<Integer> getPlaceHolderId() {
        return placeHolderId;
    }

    public void setPlaceHolderId(List<Integer> placeHolderId) {
        this.placeHolderId = placeHolderId;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public boolean isRemoved() {
        return removed;
    }

    public void setRemoved(boolean b) {
        this.removed = b;
    }
}
