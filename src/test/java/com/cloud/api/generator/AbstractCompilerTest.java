package com.cloud.api.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AbstractCompilerTest {

    @Test
    void testClassToPath() {
        String className = "com.cloud.api.generator.AbstractCompilerTest";
        assertEquals("com/cloud/api/generator/AbstractCompilerTest.java", AbstractCompiler.classToPath(className));
        assertEquals("com/cloud/api/generator/AbstractCompilerTest.java",
                AbstractCompiler.classToPath(className + ".java"));
    }

    @Test
    void testPathToClass() {
        String path = "com/cloud/api/generator/AbstractCompilerTest.java";
        assertEquals("com.cloud.api.generator.AbstractCompilerTest", AbstractCompiler.pathToClass(path));
        assertEquals("com.cloud.api.generator.AbstractCompilerTest.javaxxx",
                AbstractCompiler.pathToClass(path + "xxx"));
    }
}
