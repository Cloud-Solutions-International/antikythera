package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;

/**
 * Wraps a JavaParser {@link ImportDeclaration} together with the resolved
 * {@link Class}, {@link TypeDeclaration}, {@link FieldDeclaration}, or
 * {@link MethodDeclaration} that the import refers to, so downstream code
 * can quickly distinguish external (bytecode) imports from source-level ones.
 */
public class ImportWrapper {
    ImportDeclaration imp;
    private Class<?> clazz;
    private TypeDeclaration<?> type;
    private FieldDeclaration fieldDeclaration;
    private MethodDeclaration methodDeclaration;
    /**
     * If the import is a wild card, this will represent the actual full import classname
     */
    private ImportDeclaration simplified;

    public ImportWrapper(ImportDeclaration imp, Class<?> clazz) {
        this.imp = imp;
        this.clazz = clazz;
    }

    public ImportWrapper(ImportDeclaration imp) {
        this.imp = imp;
    }

    public ImportDeclaration getImport() {
        return imp;
    }

    public boolean isExternal() {
        return clazz != null;
    }

    public String getNameAsString() {
        return imp.getNameAsString();
    }

    public void setType(TypeDeclaration<?> type) {
        this.type = type;
    }

    @SuppressWarnings("java:S1452")
    public TypeDeclaration<?> getType() {
        return type;
    }

    public void setField(FieldDeclaration fieldDeclaration) {
        this.fieldDeclaration = fieldDeclaration;
    }

    public FieldDeclaration getField() {
        return fieldDeclaration;
    }


    public MethodDeclaration getMethodDeclaration() {
        return methodDeclaration;
    }

    public void setMethodDeclaration(MethodDeclaration methodDeclaration) {
        this.methodDeclaration = methodDeclaration;
    }

    public void setSimplified(ImportDeclaration decl) {
        this.simplified = decl;
    }

    public ImportDeclaration getSimplified() {
        return simplified;
    }

    @Override
    public String toString() {
        if (imp != null) {
            return imp.toString();
        }
        return super.toString();
    }
}
