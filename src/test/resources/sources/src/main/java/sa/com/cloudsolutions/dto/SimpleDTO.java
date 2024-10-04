package com.sa.cloudsolutions.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import static com.sa.cloudsolutions.dto.Constants.FIRST_GROUP;

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
    private Integer group = FIRST_GROUP;
}
