package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import sa.com.cloudsolutions.antikythera.evaluator.AntikytheraRunTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceExtractionStrategy.
 */
class InterfaceExtractionStrategyTest {

    private InterfaceExtractionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new InterfaceExtractionStrategy(true); // dry-run mode
        AntikytheraRunTime.reset();
    }

    @Test
    @DisplayName("Should find methods called on field")
    void findCalledMethodsOnField() {
        String code = """
                package com.example;
                import org.springframework.stereotype.Service;

                @Service
                public class OrderService {
                    private PaymentService paymentService;

                    public void processOrder() {
                        paymentService.processPayment();
                        paymentService.validateCard();
                    }
                }
                """;

        String targetCode = """
                package com.example;
                import org.springframework.stereotype.Service;

                @Service
                public class PaymentService {
                    public void processPayment() { }
                    public void validateCard() { }
                    public void refund() { } // Not used, should not be in interface
                }
                """;

        // Register target class in runtime
        CompilationUnit targetCU = StaticJavaParser.parse(targetCode);
        AntikytheraRunTime.addCompilationUnit("com.example.PaymentService", targetCU);

        CompilationUnit callerCU = StaticJavaParser.parse(code);
        FieldDeclaration field = callerCU.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                field,
                "paymentService");

        boolean result = strategy.apply(edge);

        assertTrue(result, "Should successfully extract interface");
        assertEquals(1, strategy.getGeneratedInterfaces().size());

        CompilationUnit interfaceCU = strategy.getGeneratedInterfaces().get("com.example.PaymentService");
        assertNotNull(interfaceCU);

        ClassOrInterfaceDeclaration iface = interfaceCU.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        assertEquals("IPaymentService", iface.getNameAsString());
        assertTrue(iface.isInterface());

        // Should have only 2 methods (processPayment, validateCard) not 3 (refund not
        // used)
        assertEquals(2, iface.getMethods().size());
    }

    @Test
    @DisplayName("Should modify caller field type to interface")
    void modifyCallerFieldType() {
        String code = """
                package com.example;

                public class OrderService {
                    private PaymentService paymentService;

                    public void process() {
                        paymentService.pay();
                    }
                }
                """;

        String targetCode = """
                package com.example;
                public class PaymentService {
                    public void pay() { }
                }
                """;

        CompilationUnit targetCU = StaticJavaParser.parse(targetCode);
        AntikytheraRunTime.addCompilationUnit("com.example.PaymentService", targetCU);

        CompilationUnit callerCU = StaticJavaParser.parse(code);
        FieldDeclaration field = callerCU.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                field,
                "paymentService");

        strategy.apply(edge);

        // Verify field type changed to interface
        ClassOrInterfaceDeclaration callerClass = callerCU.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        FieldDeclaration modifiedField = callerClass.getFields().get(0);
        assertEquals("IPaymentService", modifiedField.getVariable(0).getTypeAsString());
    }

    @Test
    @DisplayName("Should add implements clause to target class")
    void addImplementsClauseToTarget() {
        String code = """
                package com.example;

                public class OrderService {
                    private PaymentService paymentService;

                    public void process() {
                        paymentService.pay();
                    }
                }
                """;

        String targetCode = """
                package com.example;
                public class PaymentService {
                    public void pay() { }
                }
                """;

        CompilationUnit targetCU = StaticJavaParser.parse(targetCode);
        AntikytheraRunTime.addCompilationUnit("com.example.PaymentService", targetCU);

        CompilationUnit callerCU = StaticJavaParser.parse(code);
        FieldDeclaration field = callerCU.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                field,
                "paymentService");

        strategy.apply(edge);

        // Verify target class now implements interface
        ClassOrInterfaceDeclaration targetClass = targetCU.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        assertTrue(targetClass.getImplementedTypes().stream()
                .anyMatch(t -> t.getNameAsString().equals("IPaymentService")));
    }

    @Test
    @DisplayName("Should handle generic types")
    void handleGenericTypes() {
        String code = """
                package com.example;

                public class OrderService {
                    private DataService<Order> dataService;

                    public void process() {
                        dataService.find(1L);
                    }
                }
                """;

        String targetCode = """
                package com.example;
                public class DataService<T> {
                    public T find(Long id) { return null; }
                }
                """;

        CompilationUnit targetCU = StaticJavaParser.parse(targetCode);
        AntikytheraRunTime.addCompilationUnit("com.example.DataService", targetCU);

        CompilationUnit callerCU = StaticJavaParser.parse(code);
        FieldDeclaration field = callerCU.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.DataService",
                InjectionType.FIELD,
                field,
                "dataService");

        strategy.apply(edge);

        // Verify interface has type parameter
        CompilationUnit interfaceCU = strategy.getGeneratedInterfaces().get("com.example.DataService");
        ClassOrInterfaceDeclaration iface = interfaceCU.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        assertEquals(1, iface.getTypeParameters().size());
        assertEquals("T", iface.getTypeParameters().get(0).getNameAsString());
    }

    @Test
    @DisplayName("Should reject when no methods called")
    void rejectWhenNoMethodsCalled() {
        String code = """
                package com.example;

                public class OrderService {
                    private PaymentService paymentService;

                    public void process() {
                        // No calls to paymentService
                    }
                }
                """;

        String targetCode = """
                package com.example;
                public class PaymentService {
                    public void pay() { }
                }
                """;

        CompilationUnit targetCU = StaticJavaParser.parse(targetCode);
        AntikytheraRunTime.addCompilationUnit("com.example.PaymentService", targetCU);

        CompilationUnit callerCU = StaticJavaParser.parse(code);
        FieldDeclaration field = callerCU.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                field,
                "paymentService");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should fail when no methods called on field");
        assertTrue(strategy.getGeneratedInterfaces().isEmpty());
    }

    @Test
    @DisplayName("Should handle null AST node gracefully")
    void handleNullAstNode() {
        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                null,
                "paymentService");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should return false for null AST node");
    }

    @Test
    @DisplayName("Dry-run mode should track but not write")
    void dryRunTracksButDoesNotWrite() {
        assertTrue(strategy.isDryRun());

        String code = """
                package com.example;

                public class OrderService {
                    private PaymentService paymentService;

                    public void process() {
                        paymentService.pay();
                    }
                }
                """;

        String targetCode = """
                package com.example;
                public class PaymentService {
                    public void pay() { }
                }
                """;

        CompilationUnit targetCU = StaticJavaParser.parse(targetCode);
        AntikytheraRunTime.addCompilationUnit("com.example.PaymentService", targetCU);

        CompilationUnit callerCU = StaticJavaParser.parse(code);
        FieldDeclaration field = callerCU.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                field,
                "paymentService");

        strategy.apply(edge);

        assertEquals(2, strategy.getModifiedCUs().size()); // caller + target
        assertEquals(1, strategy.getGeneratedInterfaces().size());

        assertDoesNotThrow(() -> strategy.writeChanges("/some/path"));
    }
}
