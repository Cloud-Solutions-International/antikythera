package sa.com.cloudsolutions.antikythera.depsolver;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Strategy for converting constructor injection to setter injection with @Lazy.
 * 
 * <p>
 * This resolves constructor injection cycles by:
 * <ol>
 * <li>Removing the 'final' modifier from the field</li>
 * <li>Removing the parameter from the constructor</li>
 * <li>Removing the assignment from the constructor body</li>
 * <li>Adding a setter method with @Autowired @Lazy</li>
 * </ol>
 */
public class SetterInjectionStrategy extends AbstractExtractionStrategy {

    private static final Logger logger = LoggerFactory.getLogger(SetterInjectionStrategy.class);
    private static final String AUTOWIRED_IMPORT = "org.springframework.beans.factory.annotation.Autowired";
    private static final String LAZY_IMPORT = "org.springframework.context.annotation.Lazy";

    public SetterInjectionStrategy() {
        super();
    }

    public SetterInjectionStrategy(boolean dryRun) {
        super(dryRun);
    }

    /**
     * Convert constructor injection to setter injection for a dependency edge.
     * 
     * @param edge The constructor injection dependency edge
     * @return true if conversion was successful
     */
    public boolean apply(BeanDependency edge) {
        if (edge.injectionType() != InjectionType.CONSTRUCTOR) {
            logger.warn("Not a constructor injection: {}", edge);
            return false;
        }

        Node astNode = edge.astNode();
        if (astNode == null) {
            logger.error("No AST node for edge: {}", edge);
            return false;
        }

        // Find the containing class
        Optional<ClassOrInterfaceDeclaration> classOpt = astNode.findAncestor(ClassOrInterfaceDeclaration.class);
        if (classOpt.isEmpty()) {
            logger.error("Cannot find class for: {}", edge);
            return false;
        }

        ClassOrInterfaceDeclaration classDecl = classOpt.get();
        String fieldName = edge.fieldName();

        // Step 1: Find the field and remove 'final' modifier
        Optional<FieldDeclaration> fieldOpt = findField(classDecl, fieldName);
        if (fieldOpt.isEmpty()) {
            logger.error("Cannot find field: {}", fieldName);
            return false;
        }
        FieldDeclaration field = fieldOpt.get();
        Type fieldType = field.getVariable(0).getType();

        removeFinalModifier(field);

        // Step 2: Remove parameter and assignment from constructor
        removeFromConstructor(classDecl, fieldName);

        // Step 3: Add setter method with @Autowired @Lazy
        addSetterMethod(classDecl, fieldName, fieldType);

        // Add required imports
        classDecl.findCompilationUnit().ifPresent(cu -> {
            addImport(cu, AUTOWIRED_IMPORT);
            addImport(cu, LAZY_IMPORT);
            modifiedCUs.add(cu);
        });

        logger.info("Converted to setter injection: {}", edge);
        return true;
    }

    /**
     * Find a field by name in the class.
     */
    private Optional<FieldDeclaration> findField(ClassOrInterfaceDeclaration classDecl, String fieldName) {
        return classDecl.getFields().stream()
                .filter(f -> f.getVariables().stream()
                        .anyMatch(v -> v.getNameAsString().equals(fieldName)))
                .findFirst();
    }

    /**
     * Remove the 'final' modifier from a field.
     */
    private void removeFinalModifier(FieldDeclaration field) {
        field.getModifiers().removeIf(m -> m.getKeyword() == Modifier.Keyword.FINAL);
    }

    /**
     * Remove parameter and assignment from constructor.
     */
    private void removeFromConstructor(ClassOrInterfaceDeclaration classDecl, String fieldName) {
        for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
            // Remove the parameter
            constructor.getParameters().removeIf(p -> p.getNameAsString().equals(fieldName));

            // Remove the assignment statement: this.fieldName = fieldName
            constructor.getBody().getStatements().removeIf(stmt -> {
                if (stmt instanceof ExpressionStmt exprStmt) {
                    if (exprStmt.getExpression() instanceof AssignExpr assignExpr) {
                        if (assignExpr.getTarget() instanceof FieldAccessExpr fieldAccess) {
                            return fieldAccess.getNameAsString().equals(fieldName);
                        }
                        if (assignExpr.getTarget() instanceof NameExpr nameExpr) {
                            return nameExpr.getNameAsString().equals(fieldName);
                        }
                    }
                }
                return false;
            });
        }
    }

    /**
     * Add a setter method with @Autowired @Lazy annotations.
     */
    private void addSetterMethod(ClassOrInterfaceDeclaration classDecl, String fieldName, Type fieldType) {
        // Check if setter already exists
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        boolean setterExists = classDecl.getMethods().stream()
                .anyMatch(m -> m.getNameAsString().equals(setterName));

        if (setterExists) {
            // Just add @Lazy to existing setter if it has @Autowired
            classDecl.getMethods().stream()
                    .filter(m -> m.getNameAsString().equals(setterName))
                    .findFirst()
                    .ifPresent(m -> {
                        if (m.getAnnotationByName("Lazy").isEmpty()) {
                            m.addAnnotation(new MarkerAnnotationExpr(new Name("Lazy")));
                        }
                    });
            return;
        }

        // Create new setter method
        MethodDeclaration setter = new MethodDeclaration();
        setter.setName(setterName);
        setter.setType(new VoidType());
        setter.addModifier(Modifier.Keyword.PUBLIC);

        // Add parameter
        setter.addParameter(fieldType, fieldName);

        // Add annotations
        setter.addAnnotation(new MarkerAnnotationExpr(new Name("Autowired")));
        setter.addAnnotation(new MarkerAnnotationExpr(new Name("Lazy")));

        // Add body: this.fieldName = fieldName
        BlockStmt body = new BlockStmt();
        AssignExpr assignment = new AssignExpr(
                new FieldAccessExpr(new ThisExpr(), fieldName),
                new NameExpr(fieldName),
                AssignExpr.Operator.ASSIGN);
        body.addStatement(new ExpressionStmt(assignment));
        setter.setBody(body);

        classDecl.addMember(setter);
    }
}
