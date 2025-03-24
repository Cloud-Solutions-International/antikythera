package sa.com.cloudsolutions.antikythera.examples;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;

public class Logger {

    static class LoggerVisitor extends ModifierVisitor<Void> {

        public MethodCallExpr visit(MethodCallExpr mce, Void arg) {
            if (mce.toString().startsWith("logger.")) {
                System.out.println(mce);
            }
            return mce;
        }
    }

    public static void main(String[] args) {
         CompilationUnit cu  = StaticJavaParser
                 .parse("/home/raditha/csi/billing/csi-bm-invoice-java-service/src/main/java/com/csi/bm/invoice/service/impl/InpatientOrderServiceImpl.java");

         cu.findAll(MethodCallExpr.class).forEach(mce -> {
             LoggerVisitor visitor = new LoggerVisitor();
             mce.accept(visitor, null);
         });
    }
}
