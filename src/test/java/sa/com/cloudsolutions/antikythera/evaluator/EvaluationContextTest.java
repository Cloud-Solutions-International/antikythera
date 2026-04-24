package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;

import static org.junit.jupiter.api.Assertions.*;

class EvaluationContextTest {

    private EvaluationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new EvaluationContext();
    }

    @Test
    void testDefaultSingletonIsNotNull() {
        assertNotNull(EvaluationContext.getDefault());
    }

    @Test
    void testPushPop() {
        Variable v = new Variable("hello");
        ctx.push(v);
        assertSame(v, ctx.pop());
    }

    @Test
    void testResetStackClearsStack() {
        ctx.push(new Variable("a"));
        ctx.push(new Variable("b"));
        ctx.resetStack();
        assertThrows(java.util.NoSuchElementException.class, () -> ctx.pop());
    }

    @Test
    void testSubClasses() {
        ctx.addSubClass("Parent", "Child1");
        ctx.addSubClass("Parent", "Child2");
        assertEquals(2, ctx.findSubClasses("Parent").size());
        assertTrue(ctx.findSubClasses("Parent").contains("Child1"));
        assertTrue(ctx.findSubClasses("Parent").contains("Child2"));
        assertTrue(ctx.findSubClasses("Unknown").isEmpty());
    }

    @Test
    void testImplementations() {
        ctx.addImplementation("Iface", "Impl1");
        ctx.addImplementation("Iface", "Impl2");
        assertEquals(2, ctx.findImplementations("Iface").size());
        assertTrue(ctx.findImplementations("Unknown").isEmpty());
    }

    @Test
    void testAutoWire() {
        Variable v = new Variable("bean");
        ctx.autoWire("com.example.MyService", v);
        assertSame(v, ctx.getAutoWire("com.example.MyService"));
        assertNull(ctx.getAutoWire("com.example.Unknown"));
    }

    @Test
    void testResetAutowires() {
        ctx.autoWire("com.example.MyService", new Variable("bean"));
        ctx.resetAutowires();
        assertNull(ctx.getAutoWire("com.example.MyService"));
    }

    @Test
    void testStaticVariables() {
        Variable v = new Variable(42);
        ctx.setStaticVariable("com.Foo", "COUNT", v);
        assertSame(v, ctx.getStaticVariable("com.Foo", "COUNT"));
        assertNull(ctx.getStaticVariable("com.Foo", "MISSING"));
        assertNull(ctx.getStaticVariable("com.Bar", "COUNT"));
    }

    @Test
    void testResetStatics() {
        ctx.setStaticVariable("com.Foo", "COUNT", new Variable(1));
        ctx.resetStatics();
        assertNull(ctx.getStaticVariable("com.Foo", "COUNT"));
    }

    // ── Compilation-unit registry ──

    @Test
    void testCompilationUnitRegistry() {
        CompilationUnit cu = StaticJavaParser.parse("class Foo {}");
        ctx.addCompilationUnit("com.example.Foo", cu);
        assertSame(cu, ctx.getCompilationUnit("com.example.Foo"));
        assertNull(ctx.getCompilationUnit("com.example.Bar"));
        assertEquals(1, ctx.getCompilationUnits().size());
    }

    // ── Type registry ──

    @Test
    void testTypeRegistry() {
        TypeWrapper tw = new TypeWrapper();
        ctx.addType("com.example.Foo", tw);
        assertSame(tw, ctx.getType("com.example.Foo"));
        assertNull(ctx.getType("com.example.Bar"));
        assertEquals(1, ctx.getResolvedTypes().size());
    }

    @Test
    void testGetTypeDeclarationPresent() {
        CompilationUnit cu = StaticJavaParser.parse("class Foo {}");
        TypeWrapper tw = new TypeWrapper(cu.getType(0));
        ctx.addType("Foo", tw);
        assertTrue(ctx.getTypeDeclaration("Foo").isPresent());
    }

    @Test
    void testGetTypeDeclarationAbsent() {
        assertTrue(ctx.getTypeDeclaration("Missing").isEmpty());
    }

    @Test
    void testGetTypeDeclarationNullType() {
        TypeWrapper tw = new TypeWrapper();
        ctx.addType("NoType", tw);
        assertTrue(ctx.getTypeDeclaration("NoType").isEmpty());
    }

    @Test
    void testIsServiceClass() {
        TypeWrapper tw = new TypeWrapper();
        tw.setService(true);
        ctx.addType("Svc", tw);
        assertTrue(ctx.isServiceClass("Svc"));
        assertFalse(ctx.isServiceClass("Missing"));
    }

    @Test
    void testIsControllerClass() {
        TypeWrapper tw = new TypeWrapper();
        tw.setController(true);
        ctx.addType("Ctrl", tw);
        assertTrue(ctx.isControllerClass("Ctrl"));
        assertFalse(ctx.isControllerClass("Missing"));
    }

    @Test
    void testIsComponentClass() {
        TypeWrapper tw = new TypeWrapper();
        tw.setComponent(true);
        ctx.addType("Comp", tw);
        assertTrue(ctx.isComponentClass("Comp"));
        assertFalse(ctx.isComponentClass("Missing"));
    }

    @Test
    void testIsInterface() {
        TypeWrapper tw = new TypeWrapper();
        tw.setInterface(true);
        ctx.addType("Iface", tw);
        assertTrue(ctx.isInterface("Iface"));
        assertFalse(ctx.isInterface("Missing"));
    }

    @Test
    void testIsServiceClassReturnsFalseWhenNotService() {
        TypeWrapper tw = new TypeWrapper();
        ctx.addType("NotSvc", tw);
        assertFalse(ctx.isServiceClass("NotSvc"));
    }

    @Test
    void testResetAll() {
        ctx.push(new Variable("a"));
        ctx.addSubClass("P", "C");
        ctx.addImplementation("I", "Impl");
        ctx.autoWire("Svc", new Variable("b"));
        ctx.setStaticVariable("Cls", "f", new Variable(1));

        ctx.resetAll();

        assertThrows(java.util.NoSuchElementException.class, () -> ctx.pop());
        assertTrue(ctx.findSubClasses("P").isEmpty());
        assertTrue(ctx.findImplementations("I").isEmpty());
        assertNull(ctx.getAutoWire("Svc"));
        assertNull(ctx.getStaticVariable("Cls", "f"));
        assertTrue(ctx.getResolvedTypes().isEmpty());
        assertTrue(ctx.getCompilationUnits().isEmpty());
    }
}
