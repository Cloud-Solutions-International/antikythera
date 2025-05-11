package sa.com.cloudsolutions.antikythera.parser;


public class Stats {
    int controllers;
    int methods;
    int tests = 0;

    public int getControllers() {
        return controllers;
    }

    public int getMethods() {
        return methods;
    }

    public void setTests(int tests) {
        this.tests = tests;
    }

    public int getTests() {
        return tests;
    }
}
