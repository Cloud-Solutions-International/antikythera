package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.TypeDeclaration;

public class TypeWrapper {
    TypeDeclaration<?> type;
    Class<?> clazz;

    public TypeWrapper(TypeDeclaration<?> type) {
        this.type = type;
    }

    public TypeWrapper(Class<?> cls) {
        this.clazz = cls;
    }

    public TypeWrapper() {

    }

    @SuppressWarnings("java:S1452")
    public TypeDeclaration<?> getType() {
        return type;
    }

    public void setCu(TypeDeclaration<?> type) {
        this.type = type;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public void setClass(Class<?> cls) {
        this.clazz = cls;
    }

    public String getFullyQualifiedName() {
        if (clazz == null) {
            return type.getFullyQualifiedName().orElseThrow();
        }
        return clazz.getName();
    }
}
