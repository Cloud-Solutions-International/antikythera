package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class UsageFinder {

    public static void main(String[] args) throws IOException {
        File yamlFile = new File(Settings.class.getClassLoader().getResource("depsolver.yml").getFile());
        Settings.loadConfigMap(yamlFile);
        AbstractCompiler.preProcess();

        Map<String, CompilationUnit> resolved = AntikytheraRunTime.getResolvedClasses();

        for (Map.Entry<String, CompilationUnit> entry : resolved.entrySet()) {
            String cls = entry.getKey();
            CompilationUnit cu = entry.getValue();

            for(TypeDeclaration<?> t : cu.getTypes() ) {
                if (t.isClassOrInterfaceDeclaration()) {
                    ClassOrInterfaceDeclaration cdecl = t.asClassOrInterfaceDeclaration();
                    if (cdecl.getAnnotationByName("Entity").isEmpty()) {
                        for (FieldDeclaration fd : cdecl.getFields()) {
                            String type = fd.getVariable(0).getTypeAsString();
                            if (type.toString().contains("List ") || type.toString().contains("Set<") || type.contains("Map<")
                                || type.toString().contains("Set ") || type.toString().contains("Map ") || type.toString().contains("List<")
                            ) {
                                if (!cdecl.getFullyQualifiedName().get().contains("dto")) {
                                    System.out.println(cdecl.getFullyQualifiedName().get() + " : "
                                            + type
                                            + " : "
                                            + fd.getVariable(0).getNameAsString());
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
