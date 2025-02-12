package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.stmt.BlockStmt;

import java.lang.reflect.Method;

public class Asserter {
    private void assertNotNull(BlockStmt body, String variable) {
        body.addStatement("assertNotNull(" + variable + ");");
    }
}
