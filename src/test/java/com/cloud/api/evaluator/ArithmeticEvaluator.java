package com.cloud.api.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.GeneratorException;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;


public class ArithmeticEvaluator extends AbstractCompiler {
    Logger logger = org.slf4j.LoggerFactory.getLogger(ArithmeticEvaluator.class);

    Evaluator evaluator = new Evaluator();

    protected ArithmeticEvaluator() throws IOException {
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
        ArithmeticEvaluator arithmaticEvaluator = new ArithmeticEvaluator();
        arithmaticEvaluator.doStuff();
    }


    private void doStuff() throws FileNotFoundException, EvaluatorException {
        CompilationUnit cu = javaParser.parse(new File("src/test/java/com/cloud/api/evaluator/Arithmetic.java")).getResult().get();
        cu.accept(new ControllerFieldVisitor(), null);

        MethodDeclaration doStuffMethod = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("doStuff")).orElseThrow();
        evaluator.setScope("arithmetic");

        evaluator.executeMethod(doStuffMethod);
    }
}
