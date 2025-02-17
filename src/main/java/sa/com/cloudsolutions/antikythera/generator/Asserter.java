package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.lang.reflect.Method;

public abstract class Asserter {
    public abstract void assertNotNull(BlockStmt body, String variable);
    public abstract void setupImports(CompilationUnit gen);
}
