package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.ImportDeclaration;

public class ImportWrapper {
    ImportDeclaration imp;
    boolean isExternal;

    public ImportWrapper(ImportDeclaration imp, boolean isExternal) {
        this.imp = imp;
        this.isExternal = isExternal;
    }

    public ImportWrapper(ImportDeclaration imp) {
        this.imp = imp;
        this.isExternal = false;
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
}
