package sa.com.cloudsolutions.antikythera.evaluator;

import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.exception.EvaluatorException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserClassDeclaration;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;
import sa.com.cloudsolutions.antikythera.configuration.Settings;

import java.io.File;
import java.io.IOException;

public class CustomTypeSolverTest extends AbstractCompiler {
    protected CustomTypeSolverTest() throws IOException {
    }

    void doStuff() throws IOException, EvaluatorException {
        MemoryTypeSolver solver = new MemoryTypeSolver();
        combinedTypeSolver.add(solver);

        File file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/A.java");
        CompilationUnit cuA = getJavaParser().parse(file).getResult().get();

        cuA.getTypes().forEach(type -> {
            JavaParserClassDeclaration classDeclaration = new JavaParserClassDeclaration(type.asClassOrInterfaceDeclaration(), solver);
            solver.addDeclaration(type.getNameAsString(), classDeclaration);
        });

        file = new File("src/test/java/sa/com/cloudsolutions/antikythera/evaluator/B.java");
        CompilationUnit cuB = getJavaParser().parse(file).getResult().get();

        TypeDeclaration<?> cdecl = cuB.getType(0);
        System.out.println(cdecl);
        cdecl.getFields().get(1).asFieldDeclaration().getElementType().resolve();
    }

    public static void main(String[] args) throws IOException {
        Settings.loadConfigMap();
        CustomTypeSolverTest customTypeSolver = new CustomTypeSolverTest();
        try {
            customTypeSolver.doStuff();
        } catch (IOException | EvaluatorException e) {
            e.printStackTrace();
        }
    }
}
