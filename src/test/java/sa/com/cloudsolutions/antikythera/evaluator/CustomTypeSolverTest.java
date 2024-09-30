package sa.com.cloudsolutions.antikythera.evaluator;

import com.cloud.api.generator.AbstractCompiler;
import com.cloud.api.generator.EvaluatorException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;

public class CustomTypeSolver extends AbstractCompiler {
    protected CustomTypeSolver() throws IOException {
    }

    void doStuff() throws IOException, EvaluatorException {
        MemoryTypeSolver solver = new MemoryTypeSolver();
        combinedTypeSolver.add(solver);

        File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/A.java");
        CompilationUnit cuA = javaParser.parse(file).getResult().get();

        cuA.getTypes().forEach(type -> {
            JavaParserClassDeclaration classDeclaration = new JavaParserClassDeclaration(type.asClassOrInterfaceDeclaration(), solver);
            solver.addDeclaration(type.getNameAsString(), classDeclaration);
        });

        file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/B.java");
        CompilationUnit cuB = javaParser.parse(file).getResult().get();

        TypeDeclaration<?> cdecl = cuB.getType(0);
        System.out.println(cdecl);
        cdecl.getFields().get(1).asFieldDeclaration().getElementType().resolve();
    }

    public static void main(String[] args) throws IOException {
        Settings.loadConfigMap();
        CustomTypeSolver customTypeSolver = new CustomTypeSolver();
        try {
            customTypeSolver.doStuff();
        } catch (IOException | EvaluatorException e) {
            e.printStackTrace();
        }
    }
}
