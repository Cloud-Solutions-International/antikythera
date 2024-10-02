package sa.com.cloudsolutions.antikythera.generator;

import com.github.javaparser.ast.body.Parameter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class RepositoryQueryTest {

    private RepositoryQuery repositoryQuery;

    @BeforeEach
    void setUp() {
        repositoryQuery = new RepositoryQuery("SELECT * FROM users WHERE id = :id", true);
    }

    @Test
    void testRemove() {
        Parameter mockParameter = mock(Parameter.class);
        RepositoryQuery.QueryMethodParameter param = new RepositoryQuery.QueryMethodParameter(mockParameter, 0);
        param.setColumnName("id");
        repositoryQuery.getMethodParameters().add(param);

        repositoryQuery.remove("id");

        assertTrue(param.removed);
    }
}
