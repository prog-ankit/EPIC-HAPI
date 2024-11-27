package com.fhir_epic.hapi.service;

import org.springframework.http.ResponseEntity;

public interface EpicService {
	ResponseEntity<String> last24HoursActivity();
}
