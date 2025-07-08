package sa.com.cloudsolutions.antikythera.evaluator.mock;

import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Set;

public class MockedFieldDetector extends VoidVisitorAdapter<Set<Expression>> {
    private final String variableName;

    public MockedFieldDetector(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public void visit(FieldAccessExpr n, Set<Expression> arg) {
        // Check if the field access is on our variable of interest
        if (n.getScope().toString().equals(variableName)) {
            arg.add(n.getNameAsExpression());
        }
        super.visit(n, arg);
    }

    @Override
    public void visit(MethodCallExpr n, Set<Expression> arg) {
        // Check if the method call is on our variable of interest
        if (n.getScope().isPresent() && n.getScope().orElseThrow().toString().equals(variableName)) {
            String methodName = n.getNameAsString();
            String fieldName = null;

            // Check if it's a getter method (starts with "get" and has no parameters)
            if (methodName.startsWith("get") && methodName.length() > 3 && n.getArguments().isEmpty()) {
                // Convert getter name to field name (e.g., getName -> name)
                fieldName = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            }
            // Check if it's a boolean getter method (starts with "is" and has no parameters)
            else if (methodName.startsWith("is") && methodName.length() > 2 && n.getArguments().isEmpty()) {
                // Convert boolean getter name to field name (e.g., isValid -> valid)
                fieldName = Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
            }

            if (fieldName != null) {
                // Create a name expression for the derived field name
                com.github.javaparser.ast.expr.NameExpr fieldNameExpr = new com.github.javaparser.ast.expr.NameExpr(fieldName);
                arg.add(fieldNameExpr);
            }
        }
        super.visit(n, arg);
    }
}
