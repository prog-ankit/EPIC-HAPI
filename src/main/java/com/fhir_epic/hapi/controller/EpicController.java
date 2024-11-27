package com.fhir_epic.hapi.controller;

import com.fhir_epic.hapi.service.EpicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class EpicController {

	@Autowired
	EpicService epicService;

	@PostMapping("/last-24-hours-activtity")
	ResponseEntity<String> last24HoursActivity() {
		return epicService.last24HoursActivity();
	}
}
