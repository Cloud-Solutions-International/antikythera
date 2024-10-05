package sa.com.cloudsolutions.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.*;
import static sa.com.cloudsolutions.dto.Constants.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexDTO {
    private int first = FIRST_GROUP;
    private int second = SECOND_GROUP;

    private Long id;
    private String name;
    private String description;



    private Map<String, SimpleDTO> simpleMap;
    private Map<String, List<SimpleDTO>> mediumMap;
    private Map<String, Map<String, List<SimpleDTO>>> complexMap;
}
