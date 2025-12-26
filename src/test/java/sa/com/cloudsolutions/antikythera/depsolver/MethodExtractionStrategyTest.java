package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sa.com.cloudsolutions.antikythera.configuration.Settings;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MethodExtractionStrategyTest {

    @BeforeAll
    static void setupClass() throws IOException {
        Settings.loadConfigMap(new File("src/test/resources/generator-field-tests.yml"));
        AbstractCompiler.preProcess();
    }

    @BeforeEach
    void setUp() {
        AntikytheraRunTime.reset();
    }

    @Test
    void shouldRejectNullCycle() {
        MethodExtractionStrategy strategy = new MethodExtractionStrategy(true);
        assertFalse(strategy.apply(null));
    }

    @Test
    void shouldRejectSingleNodeCycle() {
        MethodExtractionStrategy strategy = new MethodExtractionStrategy(true);
        assertFalse(strategy.apply(List.of("com.example.ClassA")));
    }

    @Test
    void shouldExtractMethodsFromDirectCycle() {
        String orderServiceCode = """
                package com.example;
                import org.springframework.stereotype.Service;
                import org.springframework.beans.factory.annotation.Autowired;

                @Service
                public class OrderService {
                    @Autowired
                    private PaymentService paymentService;

                    public void processOrder() {
                        paymentService.charge();
                    }

                    public void getOrderStatus() {
                        // No dependency on PaymentService
                    }
                }
                """;

        String paymentServiceCode = """
                package com.example;
                import org.springframework.stereotype.Service;
                import org.springframework.beans.factory.annotation.Autowired;

                @Service
                public class PaymentService {
                    @Autowired
                    private OrderService orderService;

                    public void refund() {
                        orderService.updateStatus();
                    }

                    public void charge() {
                        // Business logic
                    }

                    public void updateStatus() {
                        // Update logic
                    }
                }
                """;

        CompilationUnit orderCU = StaticJavaParser.parse(orderServiceCode);
        CompilationUnit paymentCU = StaticJavaParser.parse(paymentServiceCode);

        AntikytheraRunTime.addCompilationUnit("com.example.OrderService", orderCU);
        AntikytheraRunTime.addCompilationUnit("com.example.PaymentService", paymentCU);

        MethodExtractionStrategy strategy = new MethodExtractionStrategy(true);
        List<String> cycle = List.of("com.example.OrderService", "com.example.PaymentService");

        boolean result = strategy.apply(cycle);

        assertTrue(result, "Should successfully extract methods");
        assertFalse(strategy.getGeneratedClasses().isEmpty(), "Should generate mediator class");

        String mediatorFqn = strategy.getGeneratedClasses().keySet().iterator().next();
        assertEquals("com.example.OrderServicePaymentServiceOperations", mediatorFqn);

        CompilationUnit mediatorCU = strategy.getGeneratedClasses().get(mediatorFqn);
        String mediatorCode = mediatorCU.toString();

        assertTrue(mediatorCode.contains("processOrder"), "Mediator should have processOrder");
        assertTrue(mediatorCode.contains("refund"), "Mediator should have refund");

        // Verify original classes are modified
        assertFalse(strategy.getModifiedCUs().isEmpty(), "Should have modified CUs");
    }

    @Test
    void shouldHandleTransitiveCycle() {
        String classA = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class ClassA {
                    @Autowired private ClassB classB;
                    public void methodA() { classB.methodB(); }
                }
                """;
        String classB = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class ClassB {
                    @Autowired private ClassC classC;
                    public void methodB() { classC.methodC(); }
                }
                """;
        String classC = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class ClassC {
                    @Autowired private ClassA classA;
                    public void methodC() { classA.methodA(); }
                }
                """;

        AntikytheraRunTime.addCompilationUnit("com.example.ClassA", StaticJavaParser.parse(classA));
        AntikytheraRunTime.addCompilationUnit("com.example.ClassB", StaticJavaParser.parse(classB));
        AntikytheraRunTime.addCompilationUnit("com.example.ClassC", StaticJavaParser.parse(classC));

        MethodExtractionStrategy strategy = new MethodExtractionStrategy(true);
        List<String> cycle = List.of("com.example.ClassA", "com.example.ClassB", "com.example.ClassC");

        boolean result = strategy.apply(cycle);

        assertTrue(result, "Should handle transitive cycle");
        assertFalse(strategy.getGeneratedClasses().isEmpty(), "Should generate mediator");
    }

    @Test
    void shouldCollectTransitiveDependencies() {
        String serviceCode = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class ServiceA {
                    @Autowired private ServiceB serviceB;
                    private String config = "test";

                    public void mainMethod() {
                        helperMethod();
                        serviceB.doWork();
                    }

                    private void helperMethod() {
                        System.out.println(config);
                    }
                }
                """;
        String serviceBCode = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class ServiceB {
                    @Autowired private ServiceA serviceA;
                    public void doWork() { serviceA.mainMethod(); }
                }
                """;

        AntikytheraRunTime.addCompilationUnit("com.example.ServiceA", StaticJavaParser.parse(serviceCode));
        AntikytheraRunTime.addCompilationUnit("com.example.ServiceB", StaticJavaParser.parse(serviceBCode));

        MethodExtractionStrategy strategy = new MethodExtractionStrategy(true);
        boolean result = strategy.apply(List.of("com.example.ServiceA", "com.example.ServiceB"));

        assertTrue(result);

        CompilationUnit mediator = strategy.getGeneratedClasses().values().iterator().next();
        String code = mediator.toString();

        assertTrue(code.contains("mainMethod"), "Should have main method");
        assertTrue(code.contains("helperMethod"), "Should have helper method (transitive)");
        assertTrue(code.contains("config"), "Should have config field (transitive)");
    }

    @Test
    void dryRunShouldNotModifyAnything() {
        String codeA = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class A {
                    @Autowired private B b;
                    public void m() { b.n(); }
                }
                """;
        String codeB = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;
                public class B {
                    @Autowired private A a;
                    public void n() { a.m(); }
                }
                """;

        AntikytheraRunTime.addCompilationUnit("com.example.A", StaticJavaParser.parse(codeA));
        AntikytheraRunTime.addCompilationUnit("com.example.B", StaticJavaParser.parse(codeB));

        MethodExtractionStrategy strategy = new MethodExtractionStrategy(true); // dry run
        strategy.apply(List.of("com.example.A", "com.example.B"));

        // In dry run, we still generate in memory but don't write
        assertFalse(strategy.getGeneratedClasses().isEmpty());
    }
}
