package com.cloud.api.evaluator;

import com.cloud.api.configurations.Settings;
import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
import com.cloud.api.generator.GeneratorException;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import org.slf4j.Logger;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Evaluator;
import sa.com.cloudsolutions.antikythera.evaluator.Variable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class HelloEvaluator extends AbstractCompiler {
    Logger logger = org.slf4j.LoggerFactory.getLogger(HelloEvaluator.class);

    Evaluator evaluator = new Evaluator();

    protected HelloEvaluator() throws IOException {
    }



    public static void main(String[] args) throws Exception {
        Settings.loadConfigMap();
        HelloEvaluator helloEvaluator = new HelloEvaluator();
        helloEvaluator.doStuff();
    }

    private void doStuff() throws IOException, EvaluatorException {
        CompilationUnit cu = javaParser.parse(new File("src/test/java/com/cloud/api/evaluator/Hello.java")).getResult().get();
        evaluator.setupFields(cu);

        Variable u = new Variable("upper cased");
        AntikytheraRunTime.push(u);
        MethodDeclaration helloUpper = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloUpper")).orElseThrow();
        evaluator.setScope("helloUpper");
        evaluator.executeMethod(helloUpper);

        MethodDeclaration helloWorld = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloWorld")).orElseThrow();
        evaluator.setScope("helloWorld");
        evaluator.executeMethod(helloWorld);

        MethodDeclaration helloName = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloName")).orElseThrow();
        evaluator.setScope("helloName");
        Variable v = new Variable("World");
        AntikytheraRunTime.push(v);
        evaluator.executeMethod(helloName);

        AntikytheraRunTime.push(v);
        MethodDeclaration helloChained = cu.findFirst(MethodDeclaration.class, m -> m.getNameAsString().equals("helloChained")).orElseThrow();
        evaluator.executeMethod(helloChained);
    }
}
