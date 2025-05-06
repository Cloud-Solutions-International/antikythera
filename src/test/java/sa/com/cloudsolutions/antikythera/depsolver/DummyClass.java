package sa.com.cloudsolutions.antikythera.depsolver;

public class DummyClass {
    private static final String PREFIX =  "prefix";
    private static final String SUFFIX = "suffix";

    @DummyAnnotation(value = PREFIX + " " + SUFFIX)
    public void exampleMethod() {}
}
