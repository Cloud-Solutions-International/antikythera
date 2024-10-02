package com.csi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.Map;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ComplexDTO {
    private Long id;
    private String name;
    private String description;

    private Map<String, SimpleDTO> simpleMap;
    private Map<String, List<SimpleDTO>> mediumMap;
    private Map<String, Map<String, List<SimpleDTO>>> complexMap;
}
