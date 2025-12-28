package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.evaluator.Scope;
import sa.com.cloudsolutions.antikythera.evaluator.ScopeChain;
import sa.com.cloudsolutions.antikythera.generator.CopyUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Abstract base class for all dependency resolution strategies.
 * Provides common functionality for file handling, logging, and AST analysis.
 */
public abstract class AbstractExtractionStrategy {
    private static final Logger logger = LoggerFactory.getLogger(AbstractExtractionStrategy.class);

    protected final Set<CompilationUnit> modifiedCUs = new HashSet<>();
    protected boolean dryRun = false;

    public AbstractExtractionStrategy() {
        this(true);
    }

    public AbstractExtractionStrategy(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Set<CompilationUnit> getModifiedCUs() {
        return modifiedCUs;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    /**
     * Find a class declaration by its fully qualified name.
     */
    protected ClassOrInterfaceDeclaration findClassDeclaration(String fqn) {
        CompilationUnit cu = AntikytheraRunTime.getCompilationUnit(fqn);
        if (cu == null) {
            return null;
        }
        return cu.findFirst(ClassOrInterfaceDeclaration.class).orElse(null);
    }

    /**
     * Determine if a method call is being made on a specific field variable.
     * Uses ScopeChain to accurately resolve the scope of the method call.
     *
     * @param mce       The method call expression
     * @param fieldName The name of the field to check against
     * @return true if the method is called on the specified field
     */
    protected boolean isMethodCallOnField(MethodCallExpr mce, String fieldName) {
        ScopeChain chain = ScopeChain.findScopeChain(mce);
        if (!chain.isEmpty()) {
            List<Scope> scopes = chain.getChain();
            if (!scopes.isEmpty()) {
                Scope firstScope = scopes.get(0);
                com.github.javaparser.ast.expr.Expression expr = firstScope.getExpression();

                if (expr.isNameExpr()) {
                    return expr.asNameExpr().getNameAsString().equals(fieldName);
                } else if (expr.isFieldAccessExpr()) {
                    FieldAccessExpr fae = expr.asFieldAccessExpr();
                    return fae.getNameAsString().equals(fieldName);
                }
            }
        }
        return false;
    }

    /**
     * Add an import if not already present.
     */
    protected void addImport(CompilationUnit cu, String importName) {
        boolean hasImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(importName));
        if (!hasImport) {
            cu.addImport(new ImportDeclaration(importName, false, false));
        }
    }

    /**
     * Write all modified files to disk.
     */
    public void writeChanges(String basePath) throws IOException {
        if (dryRun) {
            logger.info("Dry run mode - {} file(s) would be modified", modifiedCUs.size());
            return;
        }

        logger.info("Writing {} modified file(s)...", modifiedCUs.size());

        for (CompilationUnit cu : modifiedCUs) {
            if (cu.getStorage().isPresent()) {
                Path filePath = cu.getStorage().get().getPath();
                CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
            } else {
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString().replace('.', '/'))
                        .orElse("");
                String className = cu.findFirst(ClassOrInterfaceDeclaration.class)
                        .map(NodeWithSimpleName::getNameAsString)
                        .orElse(cu.getPrimaryTypeName().orElse("Unknown"));

                // Use platform-independent path construction
                Path filePath = Path.of(basePath, packageName, className + ".java");
                CopyUtils.writeFileAbsolute(filePath.toString(), cu.toString());
            }
        }
    }
}
