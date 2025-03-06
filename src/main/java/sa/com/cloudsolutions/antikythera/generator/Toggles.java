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
import sa.com.cloudsolutions.antikythera.constants.Constants;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;
import sa.com.cloudsolutions.antikythera.parser.ImportWrapper;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Toggles {
    private ClassOrInterfaceDeclaration current;
    private String name;
    private static String project;
    private static String repo;
    private static PrintWriter writer;

    private Toggles() throws IOException {
        Settings.loadConfigMap();
    }

    private class ToggleVisitor extends VoidVisitorAdapter<Void> {
        @Override
        public void visit(FieldDeclaration n, Void arg) {
            super.visit(n, arg);
            String elementType = n.getElementType().asString();
            if (elementType.endsWith("UtilConfig")) {
                n.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(cls -> {
                    Set<String> toggles = new HashSet();
                    current = cls;
                    name = n.getVariable(0).getNameAsString();
                    System.out.println(cls.getFullyQualifiedName() + " : " + name);
                    cls.accept(new FieldAccessVisitor(), toggles);
                    for (String s : toggles) {
                        writer.print(project);
                        writer.print(",");
                        writer.print(repo);
                        writer.print(",");
                        writer.print(cls.getFullyQualifiedName().get());
                        writer.print(",");
                        writer.println(s);
                        System.out.println("\t" + s);
                    }
                });
            }
//            else if (elementType.endsWith("FeatureTogglesService")) {
//                n.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(cls -> {
//                    Set<String> toggles = new HashSet();
//                    current = cls;
//                    name = n.getVariable(0).getNameAsString();
//                    System.out.println(cls.getFullyQualifiedName() + " : " + name);
//                    cls.accept(new FieldAccessVisitor(), toggles);
//                    for (String s : toggles) {
//                        System.out.println("\t" + s);
//                    }
//                });
//            }
        }
    }

    private class FieldAccessVisitor extends VoidVisitorAdapter<Set<String>> {
        @Override
        public void visit(MethodCallExpr n, Set<String> toggles) {
            super.visit(n, toggles);
            findConfiguration(n, toggles);
        }


        @Override
        public void visit(VariableDeclarationExpr n, Set<String> toggles) {
            super.visit(n, toggles);
            for (VariableDeclarator variableDeclarator : n.getVariables()) {
                Optional<Expression> initializer = variableDeclarator.getInitializer();
                if (initializer.isPresent()) {
                    Expression initExpr = initializer.get();
                    if (initExpr.isMethodCallExpr()) {
                        findConfiguration(initExpr.asMethodCallExpr(), toggles);
                    }
                }
            }
        }

        private  void findConfiguration(MethodCallExpr n, Set<String> toggles) {
            if (n.toString().startsWith(name)) {
                if (n.getNameAsString().equals("getConfigValue") || n.getNameAsString().equals("isConfigEnable")) {
                    Expression arg = n.getArguments().get(2);
                    if (arg.isStringLiteralExpr()) {
                        StringLiteralExpr s = arg.asStringLiteralExpr();
                        toggles.add(s.getValue());
                        return ;
                    }
                    else if (arg.isNameExpr()) {
                        String toggleName = arg.asNameExpr().getNameAsString();
                        if (current.getFieldByName(arg.toString()).isPresent()) {
                            toggles.add(toggleName);
                            return;
                        }
                        else {
                            ImportWrapper imp = AbstractCompiler.findImport(n.findCompilationUnit().get(), toggleName);
                            if (imp != null) {
                                if (imp.getField() != null) {
                                    Expression init = imp.getField().getVariable(0).getInitializer().get();
                                    if (init.isStringLiteralExpr()) {
                                        toggles.add(init.asStringLiteralExpr().getValue());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    else {
                        toggles.add(arg.toString());
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
        writer = new PrintWriter(new File("/tmp/configs.csv"));
        String path = "/home/raditha/workspaces/python/CSI/selenium/repos";
        List<Path> folders = findFolders(Paths.get(path));
        for (Path folder : folders) {
            System.out.println("Folder: " + folder);
            List<Path> subFolders = findFolders(folder);
            for (Path subFolder : subFolders) {
                if (containsPomXml(subFolder)) {
                    AntikytheraRunTime.resetAll();
                    AbstractCompiler.preProcess();
                    String[] parts = subFolder.toString().split("/");
                    project = parts[parts.length - 2];
                    repo = parts[parts.length -1];

                    Settings.setProperty(Constants.BASE_PATH, subFolder.toString());
                    t.find();
                }
            }
        }
        writer.close();
    }

    private static List<Path> findFolders(Path path) throws IOException {
        List<Path> folders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    folders.add(entry);
                }
            }
        }
        return folders;
    }

    private static boolean containsPomXml(Path folder) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "pom.xml")) {
            return stream.iterator().hasNext();
        }
    }
}
