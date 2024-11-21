package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

public class ImportWrapper {
    ImportDeclaration imp;
    boolean isExternal;
    private TypeDeclaration<?> type;
    private FieldDeclaration fieldDeclaration;
    private MethodDeclaration methodDeclaration;

    public ImportWrapper(ImportDeclaration imp, boolean isExternal) {
        this.imp = imp;
        this.isExternal = isExternal;
    }

    public ImportWrapper(ImportDeclaration imp) {
        this.imp = imp;
        this.isExternal = AntikytheraRunTime.getCompilationUnit(imp.getNameAsString()) == null;
    }

    public ImportDeclaration getImport() {
        return imp;
    }

    public boolean isExternal() {
        return isExternal;
    }

    public void setExternal(boolean isExternal) {
        this.isExternal = isExternal;
    }

    public String getNameAsString() {
        return imp.getNameAsString();
    }

    public void setType(TypeDeclaration<?> type) {
        this.type = type;
    }

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
}
