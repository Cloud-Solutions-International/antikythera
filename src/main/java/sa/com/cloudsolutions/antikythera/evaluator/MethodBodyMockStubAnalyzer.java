package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.body.VariableDeclarator;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Finds patterns {@code Object x = scope.method(...); ... (T) x;} and registers cast type {@code T}
 * so Mockito {@code when(scope.method(...)).thenReturn(...)} can use a value assignable to {@code T}
 * instead of {@code new Object()}.
 */
public final class MethodBodyMockStubAnalyzer {

    private MethodBodyMockStubAnalyzer() {
    }

    /**
     * Registers cast hints from <em>all</em> methods on the type so delegated calls (e.g. {@code a()}
     * calling {@code b()}) still see {@code Object x = mock.foo(); (T) x} patterns from {@code b()}
     * when tests are generated for {@code a()}.
     */
    public static void registerHintsForType(ClassOrInterfaceDeclaration type, CompilationUnit cu) {
        for (MethodDeclaration md : type.getMethods()) {
            registerHintsForMethod(md, cu);
        }
    }

    public static void registerHintsForMethod(MethodDeclaration md, CompilationUnit cu) {
        Optional<BlockStmt> bodyOpt = md.getBody();
        if (bodyOpt.isEmpty()) {
            return;
        }
        BlockStmt body = bodyOpt.get();
        Map<String, ScopeMethod> objectLocals = new HashMap<>();
        body.findAll(VariableDeclarationExpr.class).forEach(vdecl -> {
            if (!isObjectLikeType(vdecl.getElementType())) {
                return;
            }
            for (VariableDeclarator vd : vdecl.getVariables()) {
                vd.getInitializer().ifPresent(init -> {
                    if (init instanceof MethodCallExpr mce && mce.getScope().isPresent()
                            && mce.getScope().get() instanceof NameExpr scopeName) {
                        objectLocals.put(vd.getNameAsString(),
                                new ScopeMethod(scopeName.getNameAsString(), mce.getNameAsString()));
                    }
                });
            }
        });

        body.findAll(CastExpr.class).forEach(cast -> {
            if (!(cast.getExpression() instanceof NameExpr ne)) {
                return;
            }
            ScopeMethod sm = objectLocals.get(ne.getNameAsString());
            if (sm == null) {
                return;
            }
            String fqn = AbstractCompiler.findFullyQualifiedName(cu, cast.getType());
            if (fqn != null) {
                GeneratorState.putMockStubReturnHint(sm.scope, sm.method, fqn);
            }
        });
    }

    private static boolean isObjectLikeType(Type t) {
        if (t.isClassOrInterfaceType()) {
            String n = t.asClassOrInterfaceType().getNameAsString();
            return "Object".equals(n) || "java.lang.Object".equals(n);
        }
        return false;
    }

    private record ScopeMethod(String scope, String method) {
    }
}
