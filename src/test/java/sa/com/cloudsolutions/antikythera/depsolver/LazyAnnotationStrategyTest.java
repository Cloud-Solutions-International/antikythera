package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LazyAnnotationStrategy.
 */
class LazyAnnotationStrategyTest {

    private LazyAnnotationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new LazyAnnotationStrategy(true); // dry-run mode
    }

    @Test
    @DisplayName("Should add @Lazy to field injection")
    void addLazyToFieldInjection() {
        String code = """
                package com.example;
                import org.springframework.stereotype.Service;
                import org.springframework.beans.factory.annotation.Autowired;

                @Service
                public class OrderService {
                    @Autowired
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

        assertTrue(result, "Should successfully add @Lazy");
        assertTrue(field.getAnnotationByName("Lazy").isPresent(),
                "Field should have @Lazy annotation");
        assertEquals(1, strategy.getModifiedCUs().size());
    }

    @Test
    @DisplayName("Should add @Lazy import when applying to field")
    void addLazyImport() {
        String code = """
                package com.example;
                import org.springframework.stereotype.Service;

                @Service
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

        strategy.apply(edge);

        boolean hasLazyImport = cu.getImports().stream()
                .anyMatch(i -> i.getNameAsString().equals("org.springframework.context.annotation.Lazy"));
        assertTrue(hasLazyImport, "Should add Lazy import");
    }

    @Test
    @DisplayName("Should add @Lazy to setter method")
    void addLazyToSetterMethod() {
        String code = """
                package com.example;
                import org.springframework.stereotype.Service;
                import org.springframework.beans.factory.annotation.Autowired;

                @Service
                public class ReportService {
                    private DataService dataService;

                    @Autowired
                    public void setDataService(DataService dataService) {
                        this.dataService = dataService;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration setter = cu.findAll(MethodDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.ReportService",
                "com.example.DataService",
                InjectionType.SETTER,
                setter,
                "dataService");

        boolean result = strategy.apply(edge);

        assertTrue(result, "Should successfully add @Lazy to setter");
        assertTrue(setter.getAnnotationByName("Lazy").isPresent(),
                "Setter should have @Lazy annotation");
    }

    @Test
    @DisplayName("Should skip if @Lazy already present on field")
    void skipIfAlreadyHasLazyOnField() {
        String code = """
                package com.example;
                import org.springframework.context.annotation.Lazy;

                public class OrderService {
                    @Lazy
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

        assertTrue(result, "Should return true even if already has @Lazy");
        // Should still only have one @Lazy
        assertEquals(1, field.getAnnotations().stream()
                .filter(a -> a.getNameAsString().equals("Lazy")).count());
    }

    @Test
    @DisplayName("Should reject constructor injection")
    void rejectConstructorInjection() {
        String code = """
                package com.example;
                public class UserService {
                    public UserService(NotificationService ns) { }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);

        BeanDependency edge = new BeanDependency(
                "com.example.UserService",
                "com.example.NotificationService",
                InjectionType.CONSTRUCTOR,
                cu, // Pass any node, won't be used
                "notificationService");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should reject constructor injection");
        assertTrue(strategy.getModifiedCUs().isEmpty(),
                "Should not modify any CUs for constructor injection");
    }

    @Test
    @DisplayName("Should reject @Bean method injection")
    void rejectBeanMethodInjection() {
        String code = """
                package com.example;

                @Configuration
                public class AppConfig {
                    @Bean
                    public CacheManager cacheManager(ConnectionPool pool) {
                        return new CacheManager(pool);
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        MethodDeclaration beanMethod = cu.findAll(MethodDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.CacheManager",
                "com.example.ConnectionPool",
                InjectionType.BEAN_METHOD,
                beanMethod,
                "pool");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should reject @Bean method injection");
    }

    @Test
    @DisplayName("Should handle null AST node gracefully")
    void handleNullAstNode() {
        BeanDependency edge = new BeanDependency(
                "com.example.OrderService",
                "com.example.PaymentService",
                InjectionType.FIELD,
                null, // null AST node
                "paymentService");

        boolean result = strategy.apply(edge);

        assertFalse(result, "Should return false for null AST node");
    }

    @Test
    @DisplayName("Dry-run mode should not write files")
    void dryRunDoesNotWrite() {
        assertTrue(strategy.isDryRun(), "Strategy should be in dry-run mode");

        String code = """
                package com.example;
                public class Test {
                    private Dependency dep;
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(code);
        FieldDeclaration field = cu.findAll(FieldDeclaration.class).get(0);

        BeanDependency edge = new BeanDependency(
                "com.example.Test",
                "com.example.Dependency",
                InjectionType.FIELD,
                field,
                "dep");

        strategy.apply(edge);

        // writeChanges should not throw even with dry-run
        assertDoesNotThrow(() -> strategy.writeChanges("/some/path"));
    }
}
