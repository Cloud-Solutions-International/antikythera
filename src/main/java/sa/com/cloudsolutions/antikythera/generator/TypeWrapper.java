package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.TypeDeclaration;

public class TypeWrapper {
    TypeDeclaration<?> type;
    Class<?> clazz;
    private boolean isController;
    private boolean isService;
    private boolean component;
    private boolean isInterface;

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

    public boolean isController() {
        return isController;
    }
    public void setController(boolean isController) {
        this.isController = isController;
    }
    public boolean isService() {
        return isService;
    }
    public void setService(boolean isService) {
        this.isService = isService;
    }

    public boolean isComponent() {
        return component;
    }

    public void setComponent(boolean component) {
        this.component = component;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public void setInterface(boolean isInterface) {
        this.isInterface = isInterface;
    }
}
