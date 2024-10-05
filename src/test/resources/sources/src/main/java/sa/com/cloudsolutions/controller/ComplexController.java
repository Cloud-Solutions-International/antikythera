package sa.com.cloudsolutions.controller;


import sa.com.cloudsolutions.dto.SimpleDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/entities"})
@Service
public class ComplexController {

    private static final Logger logger = LoggerFactory.getLogger(ComplexController.class);
    @CrossOrigin
    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SimpleDTO>> list() {
        return new ResponseEntity<List<SimpleDTO>>(List.of(), HttpStatus.OK);
    }
}
