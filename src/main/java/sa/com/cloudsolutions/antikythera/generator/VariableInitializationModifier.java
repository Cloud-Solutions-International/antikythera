package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;

/**
 * A JavaParser visitor that modifies variable initialization within a method.
 */
public class VariableInitializationModifier extends ModifierVisitor<Void> {

    private final String variableName;
    private final Expression newInitialization;
    private boolean variableFoundAndReplaced = false; // Track if the variable was found and replaced

    /**
     * Constructor for VariableInitializationModifier.
     *
     * @param variableName The name of the variable to modify.
     * @param newInitialization The new initialization expression.
     */
    public VariableInitializationModifier(String variableName, Expression newInitialization) {
        this.variableName = variableName;
        this.newInitialization = newInitialization;
    }

    /**
     * Visits MethodDeclaration nodes to find and modify the variable initialization.
     * @param method The MethodDeclaration node.
     * @param arg  Additional argument (not used here).
     * @return The modified MethodDeclaration node.
     */
    @Override
    public Visitable visit(MethodDeclaration method, Void arg) {
        super.visit(method, arg); // Ensure we visit child nodes

        // Reset the flag at the beginning of each method visit
        variableFoundAndReplaced = false;

        for (int i = 0; i < method.getBody().get().getStatements().size(); i++) {
            if (method.getBody().get().getStatements().get(i).isExpressionStmt()) {
                ExpressionStmt exprStmt = (ExpressionStmt) method.getBody().get().getStatements().get(i);
                if (exprStmt.getExpression().isVariableDeclarationExpr()) {
                    VariableDeclarationExpr varDeclExpr = exprStmt.getExpression().asVariableDeclarationExpr();
                    for (VariableDeclarator varDeclarator : varDeclExpr.getVariables()) {
                        if (varDeclarator.getName().getIdentifier().equals(variableName)) {
                            // Variable found!  Replace the initializer.
                            varDeclarator.setInitializer(newInitialization);
                            variableFoundAndReplaced = true;
                            break; // Exit the inner loop
                        }
                    }
                }
            }
            if(variableFoundAndReplaced){
                break; // Exit the outer loop, no need to search further in this method
            }
        }
        return method;
    }
}
