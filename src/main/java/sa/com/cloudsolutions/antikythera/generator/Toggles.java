package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class Toggles {
    private ClassOrInterfaceDeclaration current;
    private String name;
    private Toggles() throws IOException {
        Settings.loadConfigMap();
        AbstractCompiler.preProcess();
    }

    private class ToggleVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(FieldDeclaration n, Void arg) {
            super.visit(n, arg);
            if (n.getElementType().asString().endsWith("UtilConfig")) {

                n.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(cls -> {
                    Set<String> toggles = new HashSet();
                    current = cls;
                    name = n.getVariable(0).getNameAsString();
                    System.out.println(cls.getFullyQualifiedName() + " : " + name);
                    cls.accept(new FieldAccessVisitor(), name);
                });
            }
        }
    }

    private class FieldAccessVisitor extends VoidVisitorAdapter<String> {
        @Override
        public void visit(MethodCallExpr n, String field) {
            super.visit(n, field);
            findConfiguration(n, field);
        }


        @Override
        public void visit(VariableDeclarationExpr n, String arg) {
            super.visit(n, arg);
            for (VariableDeclarator variableDeclarator : n.getVariables()) {
                Optional<Expression> initializer = variableDeclarator.getInitializer();
                if (initializer.isPresent()) {
                    Expression initExpr = initializer.get();
                    if (initExpr.isMethodCallExpr()) {
                        findConfiguration(initExpr.asMethodCallExpr(), arg);
                    }
                }
            }
        }

        private  void findConfiguration(MethodCallExpr n, String field) {
            if (n.toString().startsWith(field)) {
                if (n.getNameAsString().equals("getConfigValue") || n.getNameAsString().equals("isConfigEnable")) {
                    Expression arg = n.getArguments().get(2);
                    if (arg.isStringLiteralExpr()) {
                        StringLiteralExpr s = arg.asStringLiteralExpr();
                        System.out.println("\t" + s.getValue());
                        return ;
                    }
                    else if (arg.isNameExpr()) {
                        String toggleName = arg.asNameExpr().getNameAsString();
                        if (current.getFieldByName(arg.toString()).isPresent()) {
                            System.out.println("\t" + toggleName);
                            return;
                        }
                        else {
                            ImportWrapper imp = AbstractCompiler.findImport(n.findCompilationUnit().get(), toggleName);
                            if (imp != null) {
                                if (imp.getField() != null) {
                                    Expression init = imp.getField().getVariable(0).getInitializer().get();
                                    if (init.isStringLiteralExpr()) {
                                        System.out.println("\t" + init.asStringLiteralExpr().getValue());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    else {
                        System.out.println(arg);
                        return;
                    }
                    System.out.println("\t*" + arg + " NOT FOUND");
                }
            }
        }
    }
    private void find() {
        for (CompilationUnit c : AntikytheraRunTime.getResolvedClasses().values()) {
            c.accept(new ToggleVisitor(), null);
        }
    }

    public static void main(String[] args) throws IOException {
        Toggles t = new Toggles();
        t.find();
    }
}
