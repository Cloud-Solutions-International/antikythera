package sa.com.cloudsolutions.antikythera.evaluator.mock;


import org.mockito.Mock;

import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class Hello {
    @Mock
    Statement statement;

    void setUpBase() throws SQLException {
        when(statement.execute(any())).thenReturn(true);
    }

}
