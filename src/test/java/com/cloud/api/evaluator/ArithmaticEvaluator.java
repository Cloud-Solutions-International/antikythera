package com.cloud.api.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.GeneratorException;
import com.cloud.api.generator.RepositoryQuery;
import com.cloud.api.generator.RestControllerParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ArithmaticEvaluator extends AbstractCompiler {
    Logger logger = org.slf4j.LoggerFactory.getLogger(ArithmaticEvaluator.class);

    Evaluator evaluator = new Evaluator();

    protected ArithmaticEvaluator() throws IOException {
    }


    private class ControllerFieldVisitor extends VoidVisitorAdapter<Void> {

        /**
         * The field visitor will be used to identify the repositories that are being used in the controller.
         *
         * @param field the field to inspect
         * @param arg not used
         */
        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (var variable : field.getVariables()) {
                try {
                    evaluator.identifyFieldVariables(variable);
                } catch (UnsolvedSymbolException e) {
                    logger.debug("ignore {}", variable);
                } catch (IOException e) {
                    String action = Settings.getProperty("dependencies.on_error").toString();
                    if(action == null || action.equals("exit")) {
                        throw new GeneratorException("Exception while processing fields", e);
                    }
                    logger.error("Exception while processing fields");
                    logger.error("\t{}",e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Settings.loadConfigMap();
        ArithmaticEvaluator arithmaticEvaluator = new ArithmaticEvaluator();
        arithmaticEvaluator.doStuff();
    }


    private void doStuff() throws FileNotFoundException, EvaluatorException {
        // Parse the Arithmatic.java file
        CompilationUnit cu = javaParser.parse(new File("src/test/java/com/cloud/api/evaluator/Arithmatic.java")).getResult().get();
        cu.accept(new ControllerFieldVisitor(), null);

        // Find the doStuff method
        MethodDeclaration doStuffMethod = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();

        // Process all statements in the doStuff method
        List<Statement> statements = doStuffMethod.getBody().orElseThrow().getStatements();

        // Evaluate each statement in doStuff
        for (Statement stmt : statements) {
            if (stmt.isExpressionStmt()) {
                ExpressionStmt exprStmt = stmt.asExpressionStmt();
                Expression expr = exprStmt.getExpression();
                if (expr.isVariableDeclarationExpr()) {
                    VariableDeclarationExpr varDeclExpr = expr.asVariableDeclarationExpr();
                    for (var decl : varDeclExpr.getVariables()) {
                        if (decl.getInitializer().isPresent() && decl.getInitializer().get().isMethodCallExpr()) {
                            MethodCallExpr methodCall = decl.getInitializer().get().asMethodCallExpr();
                            if (methodCall.getNameAsString().equals("calculate")) {
                                // Set up the arguments for the calculate method
                                List<Expression> arguments = methodCall.getArguments();
                                Object[] argValues = new Object[arguments.size()];
                                for (int i = 0; i < arguments.size(); i++) {
                                    argValues[i] = evaluator.evaluateExpression(arguments.get(i));
                                }

                                // Find the calculate method
                                MethodDeclaration calculateMethod = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("calculate")).orElseThrow();

                                // Process all statements in the calculate method
                                List<Statement> calculateStatements = calculateMethod.getBody().orElseThrow().getStatements();

                                // Evaluate each statement in calculate
                                for (Statement calcStmt : calculateStatements) {
                                    if (calcStmt.isExpressionStmt()) {
                                        ExpressionStmt calcExprStmt = calcStmt.asExpressionStmt();
                                        Expression calcExpr = calcExprStmt.getExpression();
                                        Object result = evaluator.evaluateExpression(calcExpr);
                                        System.out.println("Result: " + result);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
