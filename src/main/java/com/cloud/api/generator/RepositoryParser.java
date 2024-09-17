package com.cloud.api.generator;

import com.cloud.api.configurations.Settings;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.BinaryExpression;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.GroupByElement;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RepositoryParser extends ClassProcessor{
    Map<MethodDeclaration, String> queries;
    public RepositoryParser() throws IOException {
        super();
        queries = new HashMap<>();
    }

    public static void main(String[] args) throws IOException {
        Settings.loadConfigMap();
        RepositoryParser parser = new RepositoryParser();
        parser.process();
    }

    private void process() throws IOException {
        File f = new File("/home/raditha/workspace/python/CSI/selenium/repos/EHR-IP/csi-ehr-ip-java-sev/src/main/java/com/csi/vidaplus/ehr/ip/admissionwithcareplane/dao/discharge/DischargeDetailRepository.java");
        CompilationUnit cu = javaParser.parse(f).getResult().orElseThrow(() -> new IllegalStateException("Parse error"));
        cu.accept(new Visitor(), null);

        var cls = cu.getTypes().get(0).asClassOrInterfaceDeclaration();
        var parents = cls.getExtendedTypes();
        if(!parents.isEmpty() && parents.get(0).toString().startsWith("JpaRepository")) {
            Optional<NodeList<Type>> t = parents.get(0).getTypeArguments();
            if(t.isPresent()) {
                Type entity = t.get().get(0);

                String nameAsString = entity.asClassOrInterfaceType().resolve().describe();
                String fileName = basePath + File.separator + nameAsString.replaceAll("\\.","/") + SUFFIX;
                CompilationUnit entityCu = javaParser.parse(
                        new File(fileName)).getResult().orElseThrow(
                                () -> new IllegalStateException("Parse error")
                );

                String table = null;

                for(var ann : entityCu.getTypes().get(0).getAnnotations()) {
                    if(ann.getNameAsString().equals("Table")) {
                        if(ann.isNormalAnnotationExpr()) {
                            for(var pair : ann.asNormalAnnotationExpr().getPairs()) {
                                if(pair.getNameAsString().equals("name")) {
                                    table = pair.getValue().toString();
                                }
                            }
                        }
                        else {
                            table = ann.asSingleMemberAnnotationExpr().getMemberValue().toString();
                        }
                    }
                }

                for (var entry : queries.entrySet()) {
                    String query = entry.getValue();
                    try {
                        query = query.replace(entity.asClassOrInterfaceType().getNameAsString(), table);
                        Select stmt = (Select) CCJSqlParserUtil.parse(cleanUp(query));
                        convertFieldsToSnakeCase(stmt);
                        System.out.println(entry.getKey().getNameAsString() +  "\n\t" + stmt);
                    } catch (JSQLParserException e) {
                        System.out.println("\tUnparsable: " + query);
                    }
                }
            }
        }
    }
    private void convertFieldsToSnakeCase(Statement stmt) {
        if(stmt instanceof  Select) {
            PlainSelect select = ((Select) stmt).getPlainSelect();
            List<SelectItem<?>> items = select.getSelectItems();
            if(items.size() == 1 && items.get(0).toString().length() == 1) {
                // This is a select * query but because it's an HQL query it appears as SELECT t
                // replace select t with select *
                items.set(0, SelectItem.from(new AllColumns()));
            }
            else {
                for (int i = 0; i < items.size(); i++) {
                    SelectItem<?> item = items.get(i);
                    String itemStr = item.toString();
                    if (itemStr.contains(".")) {
                        String[] parts = itemStr.split("\\.");
                        if (parts.length == 2) {
                            String field = parts[1];
                            String snakeCaseField = camelToSnake(field);
                            items.set(i, SelectItem.from(new Column(snakeCaseField)));
                        }
                    }
                }
            }

            if (select.getWhere() != null) {
                select.setWhere(convertExpressionToSnakeCase(select.getWhere()));
            }

            if (select.getGroupBy() != null) {
                GroupByElement group = select.getGroupBy();
                List<Expression> groupBy = group.getGroupByExpressions();
                for (int i = 0; i < groupBy.size(); i++) {
                    groupBy.set(i, convertExpressionToSnakeCase(groupBy.get(i)));
                }
            }

            if (select.getOrderByElements() != null) {
                List<OrderByElement> orderBy = select.getOrderByElements();
                for (int i = 0; i < orderBy.size(); i++) {
                    orderBy.get(i).setExpression(convertExpressionToSnakeCase(orderBy.get(i).getExpression()));
                }
            }

            if (select.getHaving() != null) {
                select.setHaving(convertExpressionToSnakeCase(select.getHaving()));
            }
        }
    }


    private Expression convertExpressionToSnakeCase(Expression expr) {
        if (expr instanceof AndExpression) {
            AndExpression andExpr = (AndExpression) expr;
            andExpr.setLeftExpression(convertExpressionToSnakeCase(andExpr.getLeftExpression()));
            andExpr.setRightExpression(convertExpressionToSnakeCase(andExpr.getRightExpression()));
        }
        else if (expr instanceof Function) {
            Function function = (Function) expr;
            ExpressionList<?> params = function.getParameters();
            function.getParameters();
        }
        else if (expr instanceof EqualsTo) {
            EqualsTo equalsTo = (EqualsTo) expr;
            equalsTo.setLeftExpression(convertExpressionToSnakeCase(equalsTo.getLeftExpression()));
            equalsTo.setRightExpression(convertExpressionToSnakeCase(equalsTo.getRightExpression()));
        }
        else if (expr instanceof BinaryExpression) {
            BinaryExpression binaryExpr = (BinaryExpression) expr;
            binaryExpr.setLeftExpression(convertExpressionToSnakeCase(binaryExpr.getLeftExpression()));
            binaryExpr.setRightExpression(convertExpressionToSnakeCase(binaryExpr.getRightExpression()));
        } else if (expr instanceof Column) {
            Column column = (Column) expr;
            String columnName = column.getColumnName();

            String snakeCaseField = camelToSnake(columnName);
            column.setColumnName(snakeCaseField);
            return column;
        }
        return expr;
    }

    public static String camelToSnake(String str) {
        return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    class Visitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(MethodDeclaration n, Void arg) {
            super.visit(n, arg);

            for (var ann : n.getAnnotations()) {
                if (ann.getNameAsString().equals("Query")) {
                    String query = null;
                    if (ann.isSingleMemberAnnotationExpr()) {
                        query = ann.asSingleMemberAnnotationExpr().getMemberValue().toString();
                    } else if (ann.isNormalAnnotationExpr()) {
                        boolean nt = false;
                        for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if (pair.getNameAsString().equals("nativeQuery")) {
                                if (pair.getValue().toString().equals("true")) {
                                    nt = true;
                                    System.out.println("\tNative Query");
                                } else {
                                    System.out.println("\tJPQL Query");
                                }
                            }
                        }
                        for (var pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if (pair.getNameAsString().equals("value")) {
                                query = pair.getValue().toString();
                            }
                        }
                    }

                    query = cleanUp(query);
                    queries.put(n, query);
                }
            }
        }
    }

    /**
     * CLean up method to be called before handing over to JSQL
     * @param sql
     * @return
     */
    private String cleanUp(String sql) {
        // If a JPA query is using a projection, we will have a new keyword immediately after the select
        // JSQL does not recognize this. So we will remove everything from the NEW keyword to the FROM
        // keyword and replace it with the '*' character.
        // Use case-insensitive regex to find and replace the NEW keyword and the FROM keyword
        Pattern pattern = Pattern.compile("new\\s+.*?\\s+from\\s+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(sql);
        if (matcher.find()) {
            sql = matcher.replaceAll(" * from ");
        }

        // Remove '+' signs only when they have spaces and a quotation mark on either side
        sql = sql.replaceAll("\"\\s*\\+\\s*\"", " ");

        // Remove quotation marks
        sql = sql.replace("\"", "");

        return sql;
    }

}

