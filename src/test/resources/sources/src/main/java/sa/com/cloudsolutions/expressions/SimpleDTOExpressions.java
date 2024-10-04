package com.sa.cloudsolutions.expressions;

import com.sa.cloudsolutions.dto.SimpleDTO;

class SimpleDTOExpressions {
    void eval() {
        SimpleDTO dto = new SimpleDTO();
        if(dto.getPatient() == null) {
            System.out.println("Patient is null");
        }
        if(dto.getDietType() == null) {
            System.out.println("DietType is null");
        }
        if(dto.getHospital() == null) {
            System.out.println("Hospital is null");
        }
        if(dto.getGroup() == null) {
            System.out.println("Group is null");
        }
    }
}
