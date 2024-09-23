package com.csi.controller;

import com.csi.dto.SimpleDTO;
import com.csi.dto.ComplexDTO;

import java.util.List;

@RestController
@RequestMapping({"/medium/entities"})
@Service
public class MediumController {

    private static Logger logger = LogManager.getLogger(AssessmentChartServiceWrite.class);
    @CrossOrigin
    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SimpleDTO>> list() {
        return new ResponseEntity<List<SimpleDTO>>(List.of(), HttpStatus.OK);
    }
}
