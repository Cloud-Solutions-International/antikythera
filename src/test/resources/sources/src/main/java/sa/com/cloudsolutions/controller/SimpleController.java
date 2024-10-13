package sa.com.cloudsolutions.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import sa.com.cloudsolutions.dto.SimpleDTO;
import sa.com.cloudsolutions.dto.MediumDTO;

@RestController
@RequestMapping({"/simple/entities"})
public class SimpleController {
    private static final Logger logger = LoggerFactory.getLogger(SimpleController.class);

    @CrossOrigin
    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MediumDTO> get(@RequestParam("id") Long id) {
        return new ResponseEntity<>(new MediumDTO(), HttpStatus.OK);
    }

    @CrossOrigin
    @RequestMapping(method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<SimpleDTO>> list() {
        return new ResponseEntity<List<SimpleDTO>>(List.of(), HttpStatus.OK);
    }
}
