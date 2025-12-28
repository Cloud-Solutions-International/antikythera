package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SetterInjectionStrategy.
 */
class SetterInjectionStrategyTest {

    private SetterInjectionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new SetterInjectionStrategy(true); // dry-run mode
    }

    @Test
    @DisplayName("Should convert constructor injection to setter")
    void convertConstructorToSetter() {
        String code = """
                package com.example;
                import org.springframework.stereotype.Service;

                @Service
                public class UserService {
                    private final NotificationService notificationService;

                    public UserService(NotificationService notificationService) {
                        this.notificationService = notificationService;
                    }

                    public void createUser(String email) {
                        notificationService.send(email);
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.UserService",
                "com.example.NotificationService",
                InjectionType.CONSTRUCTOR,
                constructor,
                "notificationService");

        boolean result = strategy.apply(edge);

        assertTrue(result, "Should successfully convert constructor injection");

        ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        // Verify final modifier removed from field
        FieldDeclaration field = classDecl.getFields().get(0);
        assertFalse(field.getModifiers().stream()
                .anyMatch(m -> m.getKeyword() == Modifier.Keyword.FINAL),
                "Field should not be final anymore");

        // Verify constructor parameter removed
        ConstructorDeclaration updatedConstructor = classDecl.getConstructors().get(0);
        assertTrue(updatedConstructor.getParameters().isEmpty(),
                "Constructor should have no parameters");

        // Verify setter method added with @Autowired @Lazy
        MethodDeclaration setter = classDecl.getMethodsByName("setNotificationService").get(0);
        assertNotNull(setter, "Setter method should exist");
        assertTrue(setter.getAnnotationByName("Autowired").isPresent(),
                "Setter should have @Autowired");
        assertTrue(setter.getAnnotationByName("Lazy").isPresent(),
                "Setter should have @Lazy");
    }

    @Test
    @DisplayName("Should add required imports")
    void addRequiredImports() {
        String code = """
                package com.example;

                public class UserService {
                    private final NotificationService ns;

                    public UserService(NotificationService ns) {
                        this.ns = ns;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.UserService",
                "com.example.NotificationService",
                InjectionType.CONSTRUCTOR,
                constructor,
                "ns");

        strategy.apply(edge);

        boolean hasAutowired = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().contains("Autowired"));
        boolean hasLazy = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().contains("Lazy"));

        assertTrue(hasAutowired, "Should add @Autowired import");
        assertTrue(hasLazy, "Should add @Lazy import");
    }

    @Test
    @DisplayName("Should remove constructor assignment statement")
    void removeConstructorAssignment() {
        String code = """
                package com.example;

                public class UserService {
                    private final NotificationService notificationService;
                    private final String name = "test";

                    public UserService(NotificationService notificationService) {
                        this.notificationService = notificationService;
                        System.out.println("Initializing");
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.UserService",
                "com.example.NotificationService",
                InjectionType.CONSTRUCTOR,
                constructor,
                "notificationService");

        strategy.apply(edge);

        ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        ConstructorDeclaration updated = classDecl.getConstructors().get(0);

        // Should still have println, but not the assignment
        assertEquals(1, updated.getBody().getStatements().size(),
                "Constructor should have 1 statement (println), not the assignment");
        assertTrue(updated.getBody().toString().contains("println"),
                "Should keep println statement");
        assertFalse(updated.getBody().toString().contains("notificationService ="),
                "Should remove assignment statement");
    }

    @Test
    @DisplayName("Should reject non-constructor injection types")
    void rejectNonConstructorInjection() {
        String code = """
                package com.example;

                public class OrderService {
                    private PaymentService paymentService;
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        FieldDeclaration field = cu.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                field,
                "paymentService");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should reject field injection");
        assertTrue(strategy.getModifiedCUs().isEmpty());
    }

    @Test
    @DisplayName("Should handle existing setter by adding @Lazy")
    void handleExistingSetterByAddingLazy() {
        String code = """
                package com.example;
                import org.springframework.beans.factory.annotation.Autowired;

                public class UserService {
                    private final NotificationService notificationService;

                    public UserService(NotificationService notificationService) {
                        this.notificationService = notificationService;
                    }

                    @Autowired
                    public void setNotificationService(NotificationService ns) {
                        this.notificationService = ns;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.UserService",
                "com.example.NotificationService",
                InjectionType.CONSTRUCTOR,
                constructor,
                "notificationService");

        boolean result = strategy.apply(edge);

        assertTrue(result, "Should succeed even with existing setter");

        ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();

        // Should have exactly one setter
        List<MethodDeclaration> setters = classDecl.getMethodsByName("setNotificationService");
        assertEquals(1, setters.size(), "Should have exactly one setter");

        // Existing setter should now have @Lazy
        MethodDeclaration setter = setters.get(0);
        assertTrue(setter.getAnnotationByName("Lazy").isPresent(),
                "Existing setter should have @Lazy added");
    }

    @Test
    @DisplayName("Should handle multiple constructor parameters, removing only target")
    void handleMultipleConstructorParameters() {
        String code = """
                package com.example;

                public class OrderService {
                    private final PaymentService paymentService;
                    private final ShippingService shippingService;

                    public OrderService(PaymentService paymentService, ShippingService shippingService) {
                        this.paymentService = paymentService;
                        this.shippingService = shippingService;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.CONSTRUCTOR,
                constructor,
                "paymentService");

        strategy.apply(edge);

        ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        ConstructorDeclaration updated = classDecl.getConstructors().get(0);

        // Should still have shippingService parameter
        assertEquals(1, updated.getParameters().size(),
                "Constructor should have 1 parameter (shippingService)");
        assertEquals("shippingService", updated.getParameter(0).getNameAsString());

        // Should still have shippingService assignment
        assertEquals(1, updated.getBody().getStatements().size(),
                "Constructor should have 1 statement (shippingService assignment)");
    }

    @Test
    @DisplayName("Should handle null AST node gracefully")
    void handleNullAstNode() {
        BeanDependency edge = new BeanDependency(
                "com.example.UserService",
                "com.example.NotificationService",
                InjectionType.CONSTRUCTOR,
                null,
                "notificationService");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should return false for null AST node");
    }

    @Test
    @DisplayName("Should create correct setter body")
    void createCorrectSetterBody() {
        String code = """
                package com.example;

                public class TestService {
                    private final DepService depService;

                    public TestService(DepService depService) {
                        this.depService = depService;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.TestService",
                "com.example.DepService",
                InjectionType.CONSTRUCTOR,
                constructor,
                "depService");

        strategy.apply(edge);

        ClassOrInterfaceDeclaration classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class).orElseThrow();
        MethodDeclaration setter = classDecl.getMethodsByName("setDepService").get(0);

        // Verify setter has correct parameter
        assertEquals(1, setter.getParameters().size());
        assertEquals("depService", setter.getParameter(0).getNameAsString());
        assertEquals("DepService", setter.getParameter(0).getTypeAsString());

        // Verify setter body is "this.depService = depService"
        String body = setter.getBody().orElseThrow().toString();
        assertTrue(body.contains("this.depService = depService"),
                "Setter body should have correct assignment: " + body);
    }

    @Test
    @DisplayName("Dry-run mode should track modifications but not write")
    void dryRunTracksModifications() {
        String code = """
                package com.example;

                public class TestService {
                    private final Dep dep;
                    public TestService(Dep dep) {
                        this.dep = dep;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        ConstructorDeclaration constructor = cu.findAll(ConstructorDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.TestService",
                "com.example.Dep",
                InjectionType.CONSTRUCTOR,
                constructor,
                "dep");

        strategy.apply(edge);

        assertTrue(strategy.isDryRun());
        assertEquals(1, strategy.getModifiedCUs().size());
        assertDoesNotThrow(() -> strategy.writeChanges("/some/path"));
    }
}
