package sa.com.cloudsolutions.antikythera.evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
            // If helper returns null, try to create a mock using Mockito
            Type t = param.getType();
            if (t.isClassOrInterfaceType()) {
                String typeName = t.asClassOrInterfaceType().getNameAsString();
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
            for (Expression expr : customized) {
                if (expr instanceof ObjectCreationExpr oce) {
                    if (wrapper.getClazz() != null) {
                        ReflectionArguments args = Reflect.buildArguments(oce,
                                EvaluatorFactory.createLazily("", Evaluator.class), null);
                        Constructor<?> constructor = Reflect.findConstructor(wrapper.getClazz(), args.getArgumentTypes(), args.getArguments());
                        v = new Variable(constructor.newInstance(args.getArguments()));
                        v.setInitializer(customized);
                        return v;
                    } else {
                        // For JDK classes, just create a Variable with the expression as initializer
                        v = new Variable(null);
                        v.setInitializer(customized);
                        return v;
                    }
                } else {
                    // For non-ObjectCreationExpr expressions (like method calls), create Variable with expression
                    v = new Variable(null);
                    v.setInitializer(List.of(expr));
                    return v;
                }
            }
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
