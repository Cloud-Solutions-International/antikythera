package sa.com.cloudsolutions.antikythera.parser;

import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.lang.reflect.Method;

/**
 * A wrapper that unifies CallableDeclaration from java parser with the reflection API
 */
public class Callable {
    /**
     * A non-null callable declaration if the method call is associated with a source code.
     */
    CallableDeclaration<?> callableDeclaration;
    /**
     * A non-null Method instance from java reflection for a call associated with a class
     */
    Method method;
    /**
     * The class where the method was found.
     * Useful when the class we are processing is not the one that actually provides the method but
     * one of its ancestors.
     */
    Class<?> foundInClass;

    ClassOrInterfaceDeclaration classOrInterfaceDeclaration;

    /**
     * The method call expression associated with this callable.
     * A callable instance is typically created when we search through our source code and binaries
     * to find a method declaration or method. In essence, the method call expression is the search
     * criteria that resulted in this instance being created.
     */
    MCEWrapper mce;

    public Callable(CallableDeclaration<?> callableDeclaration, MCEWrapper mce) {
        this.callableDeclaration = callableDeclaration;
        callableDeclaration.findAncestor(ClassOrInterfaceDeclaration.class)
                .ifPresent(orInterfaceDeclaration -> this.classOrInterfaceDeclaration = orInterfaceDeclaration);
        this.mce = mce;
    }

    public Callable(Method method, MCEWrapper mce) {
        this.method = method;
        this.mce = mce;
    }

    protected Callable() {

    }

    @SuppressWarnings("java:S1452")
    public CallableDeclaration<?> getCallableDeclaration() {
        return callableDeclaration;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public boolean isMethod() {
        return method != null;
    }

    public boolean isMethodDeclaration() {
        return callableDeclaration != null && callableDeclaration.isMethodDeclaration();
    }

    public boolean isCallableDeclaration() {
        return callableDeclaration != null;
    }

    public MethodDeclaration asMethodDeclaration() {
        if (isCallableDeclaration()) {
            return callableDeclaration.asMethodDeclaration();
        }
        return null;
    }

    public String getNameAsString() {
        if (isMethod()) {
            return method.getName();
        }
        if (callableDeclaration != null) {
            return callableDeclaration.getNameAsString();
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Callable other) {
            if (method != null) {
                return method.equals(other.method);
            }
            return callableDeclaration.equals(other.callableDeclaration);
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (method != null) {
            return method.hashCode() + 141;
        }
        return callableDeclaration.hashCode() + 431;
    }

    @Override
    public String toString() {
        return getNameAsString();
   }

    public Class<?> getFoundInClass() {
        return foundInClass;
    }

    public void setFoundInClass(Class<?> foundInClass) {
        this.foundInClass = foundInClass;
    }
    public MCEWrapper getMce() {
        return mce;
    }

    @SuppressWarnings("unused")
    public ClassOrInterfaceDeclaration getClassOrInterfaceDeclaration() {
        return classOrInterfaceDeclaration;
    }

    public void setMce(MCEWrapper mce) {
        this.mce = mce;
    }
}
