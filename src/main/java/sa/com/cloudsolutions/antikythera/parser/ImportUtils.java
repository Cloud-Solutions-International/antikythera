package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import sa.com.cloudsolutions.antikythera.depsolver.Graph;
import sa.com.cloudsolutions.antikythera.depsolver.GraphNode;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.exception.AntikytheraException;
import sa.com.cloudsolutions.antikythera.exception.DepsolverException;

public class ImportUtils {

    public static GraphNode addImport(GraphNode node, Type type) {
        if (type.isClassOrInterfaceType()) {
            ClassOrInterfaceType ct = type.asClassOrInterfaceType();
            ImportUtils.addImport(node, ct.getNameAsString());
        }
        else {
            ImportUtils.addImport(node, type.toString());
        }
        return null;
    }

    public static GraphNode addImport(GraphNode node, String name) {
        GraphNode returnValue = null;
        try {
            ImportWrapper imp = AbstractCompiler.findImport(node.getCompilationUnit(), name);
            if (imp != null) {
                node.getDestination().addImport(imp.getImport());
                if (imp.getType() != null) {
                    returnValue = Graph.createGraphNode(imp.getType());
                }
                if (imp.getField() != null) {
                    Graph.createGraphNode(imp.getField());
                } else if (imp.getImport().isAsterisk() && !imp.isExternal()) {
                    CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(imp.getImport().getNameAsString());
                    if (cu != null) {
                        TypeDeclaration<?> td = AbstractCompiler.getMatchingType(cu, name);
                        if (td != null) {
                            Graph.createGraphNode(td);
                        }
                    }
                }
            } else {
                String fullyQualifiedName = AbstractCompiler.findFullyQualifiedName(node.getCompilationUnit(), name);
                CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fullyQualifiedName);
                if (cu != null) {
                    TypeDeclaration<?> t = AbstractCompiler.getMatchingType(cu, name);
                    if (t != null) {
                        returnValue = Graph.createGraphNode(t);
                    }
                }
            }
        } catch (AntikytheraException e) {
            throw new DepsolverException(e);
        }
        return returnValue;
    }
}
