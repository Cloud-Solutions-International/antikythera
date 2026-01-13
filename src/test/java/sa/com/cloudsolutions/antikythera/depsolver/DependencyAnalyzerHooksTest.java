package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.type.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DependencyAnalyzer Phase 1 enhancements:
 * - Signature generation for different node types
 * - Hook invocation during DFS traversal
 */
class DependencyAnalyzerHooksTest {

    private TestDependencyAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        Graph.getNodes().clear();
        analyzer = new TestDependencyAnalyzer();
        analyzer.resetAnalysis();
    }

    @Test
    @DisplayName("Signature: method includes class, name, and parameter types")
    void testMethodSignature() {
        String code = """
            package com.example;
            class Service {
                public User findById(Long id, boolean active) { return null; }
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration md = cu.findFirst(MethodDeclaration.class).orElseThrow();

        GraphNode node = Graph.createGraphNode(md);
        String signature = analyzer.getNodeSignature(node);

        assertEquals("com.example.Service#findById(Long,boolean)", signature);
    }

    @Test
    @DisplayName("Signature: overloaded methods produce distinct signatures")
    void testOverloadedMethodSignatures() {
        String code = """
            package com.example;
            class Service {
                void process(String s) {}
                void process(String s, int count) {}
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        var methods = cu.findAll(MethodDeclaration.class);

        String sig1 = analyzer.getNodeSignature(Graph.createGraphNode(methods.get(0)));
        String sig2 = analyzer.getNodeSignature(Graph.createGraphNode(methods.get(1)));

        assertNotEquals(sig1, sig2);
        assertTrue(sig1.contains("String)"));
        assertTrue(sig2.contains("String,int)"));
    }

    @Test
    @DisplayName("Signature: field uses class#fieldName format")
    void testFieldSignature() {
        String code = """
            package com.example;
            class Entity {
                private String name;
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        FieldDeclaration fd = cu.findFirst(FieldDeclaration.class).orElseThrow();

        GraphNode node = Graph.createGraphNode(fd);
        String signature = analyzer.getNodeSignature(node);

        assertEquals("com.example.Entity#name", signature);
    }

    @Test
    @DisplayName("Signature: static block uses <clinit> notation")
    void testStaticBlockSignature() {
        String code = """
            package com.example;
            class Config {
                static { System.out.println("init"); }
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        InitializerDeclaration id = cu.findFirst(InitializerDeclaration.class).orElseThrow();

        GraphNode node = Graph.createGraphNode(id);
        String signature = analyzer.getNodeSignature(node);

        assertTrue(signature.startsWith("com.example.Config#<clinit>"));
    }

    @Test
    @DisplayName("Signature: multiple static blocks get indexed signatures")
    void testMultipleStaticBlockSignatures() {
        String code = """
            package com.example;
            class Config {
                static { System.out.println("first"); }
                static { System.out.println("second"); }
            }
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        var blocks = cu.findAll(InitializerDeclaration.class);

        String sig1 = analyzer.getNodeSignature(Graph.createGraphNode(blocks.get(0)));
        String sig2 = analyzer.getNodeSignature(Graph.createGraphNode(blocks.get(1)));

        assertNotEquals(sig1, sig2);
        assertTrue(sig1.contains("<clinit>"));
        assertTrue(sig2.contains("<clinit>_1"));
    }

    @Test
    @DisplayName("Signature: type uses fully qualified name")
    void testTypeSignature() {
        String code = """
            package com.example;
            class MyService {}
            """;
        CompilationUnit cu = StaticJavaParser.parse(code);
        ClassOrInterfaceDeclaration cid = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        GraphNode node = Graph.createGraphNode(cid);
        String signature = analyzer.getNodeSignature(node);

        assertEquals("com.example.MyService", signature);
    }

    @Test
    @DisplayName("Custom visitor: subclass can provide overridden DependencyVisitor")
    void testCustomVisitorFactory() {
        CustomVisitorAnalyzer customAnalyzer = new CustomVisitorAnalyzer();
        DependencyAnalyzer.DependencyVisitor visitor = customAnalyzer.createDependencyVisitor();

        assertInstanceOf(CustomVisitorAnalyzer.TrackingVisitor.class, visitor);
    }

    /**
     * Test subclass that tracks hook invocations.
     */
    static class TestDependencyAnalyzer extends DependencyAnalyzer {
        int fieldAccessCount = 0;
        int typeUsedCount = 0;
        int lambdaCount = 0;
        int nestedTypeCount = 0;

        @Override
        protected void onFieldAccessed(GraphNode node, FieldAccessExpr fae) {
            fieldAccessCount++;
        }

        @Override
        protected void onTypeUsed(GraphNode node, Type type) {
            typeUsedCount++;
        }

        @Override
        protected void onLambdaDiscovered(GraphNode node, LambdaExpr lambda) {
            lambdaCount++;
        }

        @Override
        protected void onNestedTypeDiscovered(GraphNode node, ClassOrInterfaceDeclaration nestedType) {
            nestedTypeCount++;
        }
    }

    /**
     * Test subclass that provides a custom visitor.
     */
    static class CustomVisitorAnalyzer extends DependencyAnalyzer {
        @Override
        protected DependencyVisitor createDependencyVisitor() {
            return new TrackingVisitor();
        }

        class TrackingVisitor extends DependencyVisitor {
            // Custom visitor implementation
        }
    }
}
