package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import sa.com.cloudsolutions.antikythera.depsolver.DepSolver;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;

import java.io.File;

public class ServicesParser {
    CompilationUnit cu;

    public ServicesParser(File file) {
    }

    public ServicesParser(String cls) {
        this.cu = AntikytheraRunTime.getCompilationUnit(cls);
        if (this.cu == null) {
            throw new AntikytheraException("Class not found: " + cls);
        }
    }

    public void start() {
        for(TypeDeclaration<?> decl : cu.getTypes()) {
            DepSolver solver = DepSolver.createSolver();
            decl.findAll(MethodDeclaration.class).forEach(md -> {
                if (!md.isPrivate()) {
                    Graph.createGraphNode(md);

                }
            });
            solver.dfs();
        }
    }
}
