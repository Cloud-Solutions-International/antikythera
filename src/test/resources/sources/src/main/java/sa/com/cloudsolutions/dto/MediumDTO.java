package sa.com.cloudsolutions.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Map;
import java.util.List;

import static sa.com.cloudsolutions.dto.Constants.FIRST_GROUP;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MediumDTO {
    private Integer group = FIRST_GROUP;
    private int number = 17;
    private Long id;
    private String name;
    private String description;

    private Map<String, SimpleDTO> simpleMap;
    private Map<String, List<SimpleDTO>> mediumMap;
    private Map<String, Map<String, List<SimpleDTO>>> complexMap;
}
