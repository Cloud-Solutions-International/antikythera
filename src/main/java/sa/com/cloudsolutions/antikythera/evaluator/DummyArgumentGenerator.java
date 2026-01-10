package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockedFieldDetector;
import sa.com.cloudsolutions.antikythera.evaluator.mock.MockingRegistry;
import sa.com.cloudsolutions.antikythera.generator.TypeWrapper;
import sa.com.cloudsolutions.antikythera.parser.AbstractCompiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DummyArgumentGenerator extends ArgumentGenerator {

    public DummyArgumentGenerator() {
        super();
    }

    @Override
    public void generateArgument(Parameter param) throws ReflectiveOperationException {
        Variable v = mockParameter(param);
        if (v.getValue() == null) {
            v = mockNonPrimitiveParameter(param);
        }

        /*
         * Pushed to be popped later in the callee
         */
        arguments.put(param.getNameAsString(), v);
        AntikytheraRunTime.push(v);
    }

    @SuppressWarnings("unchecked")
    private Variable mockNonPrimitiveParameter(Parameter param) throws ReflectiveOperationException {
        final Variable vx = mockNonPrimitiveParameterHelper(param);
        if (vx == null) {
            return mockNonPrimitiveUsingMockito(param);
        }
        if (vx.getValue() instanceof Evaluator eval) {
            param.findAncestor(MethodDeclaration.class).ifPresent(md -> {
                Set<Expression> expressions = new HashSet<>();
                MockedFieldDetector detector = new MockedFieldDetector(param.getNameAsString());
                md.accept(detector, expressions);
                for (Expression expr : expressions) {
                    mockField(eval, expr);
                }
            });
        }
        return vx;
    }

    private static Variable mockNonPrimitiveUsingMockito(Parameter param) {
        // If helper returns null, try to create a mock using Mockito
        Type t = param.getType();
        if (t.isClassOrInterfaceType()) {
            try {
                // Try to load the class and create a mock
                if (param.findCompilationUnit().isPresent()) {
                    TypeWrapper wrapper = AbstractCompiler.findType(param.findCompilationUnit().orElseThrow(), t);
                    if (wrapper != null && wrapper.getClazz() != null) {
                        return MockingRegistry.createMockitoMockInstance(wrapper.getClazz());
                    }
                }
            } catch (Exception e) {
                // If we can't create a mock, return a Variable with null
                Variable nullVar = new Variable(null);
                nullVar.setInitializer(List.of(new com.github.javaparser.ast.expr.NullLiteralExpr()));
                return nullVar;
            }
        }
        // Return null variable - will be handled by caller
        return null;
    }

    private static void mockField(Evaluator eval, Expression expr) {
        if (expr instanceof NameExpr name) {
            Variable field = eval.getField(name.getNameAsString());
            if (field != null) {
                Type t = field.getType();
                String fullClassName = AbstractCompiler.findFullyQualifiedName(eval.getCompilationUnit(), t);
                if (fullClassName != null && !fullClassName.endsWith("String")) {
                    Variable value = Reflect.variableFactory(fullClassName);
                    if (value != null) {
                        field.getInitializer().addAll(value.getInitializer());
                        field.setValue(value.getValue());
                    }
                }
            }
        }
    }

    /**
     * Handle custom mock expressions for a given type. Assumes the list is ordered such that
     * the first viable ObjectCreationExpr represents the creation, and the rest are initialization steps.
     * If no ObjectCreationExpr is found, returns a Variable with all expressions as initializers.
     */
    private Variable handleCustomizedExpressions(TypeWrapper wrapper, List<Expression> customized) {
        for (int i = 0; i < customized.size(); i++) {
            Expression expr = customized.get(i);
            if (expr instanceof ObjectCreationExpr oce) {
                // If we can load the class, try to instantiate; else keep as initializer-only
                if (wrapper != null && wrapper.getClazz() != null) {
                    try {
                        ReflectionArguments args = Reflect.buildArguments(
                                oce, EvaluatorFactory.createLazily("", Evaluator.class), null);
                        Constructor<?> constructor = Reflect.findConstructor(wrapper.getClazz(), args.getArgumentTypes(), args.getArguments());
                        if (constructor != null) {
                            Variable v = new Variable(constructor.newInstance(args.getArguments()));
                            // Initializer should include creation + subsequent initialization steps
                            java.util.List<Expression> inits = new java.util.ArrayList<>(customized.subList(i, customized.size()));
                            v.setInitializer(inits);
                            return v;
                        }
                    } catch (Exception ignored) {
                        // Fall through to initializer-only behavior below
                    }
                }
                // Could not instantiate (JDK/unavailable or ctor mismatch). Keep initializer-only from this point onwards
                Variable v = new Variable(null);
                java.util.List<Expression> inits = new java.util.ArrayList<>(customized.subList(i, customized.size()));
                v.setInitializer(inits);
                return v;
            }
        }
        // No creation expression found; use all as initializers
        Variable v = new Variable(null);
        v.setInitializer(customized);
        return v;
    }

    private Variable mockNonPrimitiveParameterHelper(Parameter param) throws ReflectiveOperationException {
        Variable v = null;
        Type t = param.getType();

        if (!t.isClassOrInterfaceType() || param.findCompilationUnit().isEmpty()) {
            return v;
        }

        TypeWrapper wrapper = AbstractCompiler.findType(param.findCompilationUnit().orElseThrow(), t);
        String fullClassName = wrapper.getFullyQualifiedName();
        if (t.asClassOrInterfaceType().isBoxedType()) {
            return mockParameter(param);
        }
        
        // Check for custom mock expressions first (works for JDK classes too)
        List<Expression> customized = MockingRegistry.getCustomMockExpressions(fullClassName);
        if (!customized.isEmpty()) {
            return handleCustomizedExpressions(wrapper, customized);
        }

        if (wrapper.getClazz() != null) {
            return mockNonPrimitiveParameterHelper(param, wrapper);
        }

        Optional<TypeDeclaration<?>> opt = AntikytheraRunTime.getTypeDeclaration(fullClassName);
        if (opt.isPresent()) {
            TypeDeclaration<?> typeDecl = opt.get();
            // Check if it's an interface - if so, use Mockito.mock() instead of trying to instantiate
            if (typeDecl.isClassOrInterfaceDeclaration() && typeDecl.asClassOrInterfaceDeclaration().isInterface()) {
                // For interfaces, we need to use Mockito.mock() - but we need the Class object
                // Try to load it, or create a mock with the type name
                try {
                    Class<?> interfaceClass = AbstractCompiler.loadClass(fullClassName);
                    return MockingRegistry.createMockitoMockInstance(interfaceClass);
                } catch (ClassNotFoundException e) {
                    // Can't load the class, create a Variable with Mockito.mock() initializer
                    Variable mockVar = new Variable(null);
                    MethodCallExpr mockExpr = new MethodCallExpr(
                        new NameExpr("Mockito"), "mock",
                        new NodeList<>(new ClassExpr(new ClassOrInterfaceType(null, typeDecl.getNameAsString())))
                    );
                    mockVar.setInitializer(List.of(mockExpr));
                    return mockVar;
                }
            }
            v = createObjectWithSimplestConstructor(typeDecl, param.getNameAsString());
        }

        return v;
    }

    public static Variable createObjectWithSimplestConstructor(TypeDeclaration<?> cdecl, String name) {
        Evaluator o = EvaluatorFactory.create(cdecl.getFullyQualifiedName().orElseThrow(), MockingEvaluator.class);
        Variable v = new Variable(o);
        String init = ArgumentGenerator.instantiateClass(
                cdecl, name
        ).replace(";", "");
        String[] parts = init.split("=");
        v.setInitializer(List.of(StaticJavaParser.parseExpression(parts[1])));
        return v;
    }

    private Variable mockNonPrimitiveParameterHelper(Parameter param, TypeWrapper wrapper) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        String fullClassName = wrapper.getFullyQualifiedName();
        Type t = param.getType();

        if (fullClassName.startsWith("java.util")) {
            return Reflect.variableFactory(fullClassName);
        }

        Class<?> clazz = wrapper.getClazz();
        if (clazz.isInterface()) {
            /* this is an interface so no point in looking for a constructor  */
            return MockingRegistry.createMockitoMockInstance(clazz);
        }

        // Try to find a no-arg constructor first
        try {
            return new Variable(clazz.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            // No no-arg constructor, find the simplest one
            Constructor<?> simplest = findSimplestConstructor(clazz);
            if (simplest != null) {
                return createObjectWithSimplestConstructor(simplest, t);
            }
        }

        return new Variable((Object) null);
    }

    public static Variable createObjectWithSimplestConstructor(Constructor<?> simplest, Type t) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        Object[] args = new Object[simplest.getParameterCount()];
        Class<?>[] paramTypes = simplest.getParameterTypes();
        NodeList<Expression> argExprs = new NodeList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].equals(String.class)) {
                args[i] = "Antikythera";
                argExprs.add(new StringLiteralExpr("Antikythera"));
            } else {
                args[i] = Reflect.getDefault(paramTypes[i]);
                argExprs.add(Reflect.createLiteralExpression(args[i]));
            }
        }
        Variable v = new Variable(simplest.newInstance(args));
        // Set initializer
        ObjectCreationExpr oce =
            new ObjectCreationExpr()
                .setType(t.asString())
                .setArguments(argExprs);
        v.setInitializer(List.of(oce));
        return v;
    }

    @SuppressWarnings("java:S1462")
    public static Constructor<?> findSimplestConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        Constructor<?> simplest = null;
        int minParams = Integer.MAX_VALUE;
        for (Constructor<?> ctor : constructors) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            boolean allSimple = true;
            for (Class<?> pt : paramTypes) {
                if (!(Reflect.isPrimitiveOrBoxed(pt) || pt.equals(String.class))) {
                    allSimple = false;
                    break;
                }
            }
            if (allSimple && paramTypes.length < minParams) {
                minParams = paramTypes.length;
                simplest = ctor;
            }
        }
        return simplest;
    }

    protected Variable mockParameter(Parameter param) {
        Type t = param.getType();
        if (t.isClassOrInterfaceType()) {
            return Reflect.variableFactory(t.asClassOrInterfaceType().getName().asString());
        }
        return Reflect.variableFactory(param.getType().asString());
    }
}
