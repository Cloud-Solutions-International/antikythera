package sa.com.cloudsolutions.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static sa.com.cloudsolutions.dto.Constants.FIRST_GROUP;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SimpleDTO {
    private Long id;
    private String name;
    private String description;

    private String patient;
    private DietType dietType;
    private Hospital hospital;

    /*
     * this constructor should end up being nuked
     */
    public SimpleDTO(Long id) {
        this.id = id;
    }

    /*
     * this constructor should end up being nuked.
     */
    public SimpleDTO(Hospital hospital) {
        this.hospital = hospital;
    }
}
