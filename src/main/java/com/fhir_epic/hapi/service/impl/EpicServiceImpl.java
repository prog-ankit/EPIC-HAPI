package com.fhir_epic.hapi.service.impl;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fhir_epic.hapi.config.ApplicationProperties;
import com.fhir_epic.hapi.service.EpicService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

@Service
public class EpicServiceImpl implements EpicService {

	private static final Logger LOGGER = LoggerFactory.getLogger(EpicServiceImpl.class);

	@Autowired
	ApplicationProperties applicationProperties;

	@Autowired
	private ObjectMapper objectMapper;

	RestTemplate restTemplate;
	FhirContext ctx;
	IParser parser;

	EpicServiceImpl() {
		restTemplate = new RestTemplate();
		ctx = FhirContext.forR4();
		parser = ctx.newJsonParser();
	}

	public String signJWTToken() {
		LOGGER.info("Inside Sign Jwt Token Class");
		String jwtToken = generateToken();
		LOGGER.info("JWT Token : {}", jwtToken);

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

		MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
		data.add("grant_type", "client_credentials");
		data.add("client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
		data.add("client_assertion", jwtToken);

		HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(data, httpHeaders);
		HashMap response = restTemplate.postForObject(applicationProperties.getAuthURL(), request, HashMap.class);

		return response != null ? (String) response.get("access_token") : "";
	}

	@Override
	public ResponseEntity<String> last24HoursActivity() {
		String bulkDataKickoffUrl = applicationProperties.getBaseUrl() + "R4/Group/" + applicationProperties.getGroupId() + "/$export?_type=Encounter";
		LOGGER.info("Initiating bulk data request: {}",bulkDataKickoffUrl);
		String accessToken = signJWTToken();
		String bulkDataRequestUrl = initiateBulkDataRequest(accessToken,bulkDataKickoffUrl);
		if(bulkDataRequestUrl == null) {
			return ResponseEntity.status(400).body("Error: Bulk data kickoff request failed.");
		}
		try {
			ResponseEntity<String> bulkDataResponse = checkBulkDataRequest(bulkDataRequestUrl,accessToken);
			if (bulkDataResponse == null || bulkDataResponse.getStatusCode().value() != 200) {
				return ResponseEntity.status(400).body("Error: Bulk data request failed.");
			}
			List<String> encounterUrls = getEncounterUrl(bulkDataResponse.getBody());
			List<String> messages = processEncounters(encounterUrls,accessToken);
			if(messages.isEmpty()) {
				LOGGER.info("No activity in last 24 hoours");
				return ResponseEntity.ok("Done");
			}
			LOGGER.info("-------------------------------------------------");
			LOGGER.info("Activity in last 24 hours: ");
			for (String message : messages) {
				LOGGER.info(message);
			}
			System.out.println();

		} catch (InterruptedException | JsonProcessingException e) {
			throw new RuntimeException(e);
		} finally {
			if (!deleteBulkDataRequest(bulkDataRequestUrl, accessToken)) {
				LOGGER.info("Error: Bulk data request deletion failed.");
			}
		}
		return ResponseEntity.ok("Done");
	}

	// Initiates the bulk data request and returns the request URL if successful.
	private String initiateBulkDataRequest(String accessToken, String bulkDataKickoffUrl) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		headers.set("Accept", "application/fhir+json");
		headers.set("Prefer", "respond-async");
		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(bulkDataKickoffUrl, HttpMethod.GET, entity, String.class);
		if(response.getStatusCode().value() != 202) {
			LOGGER.error("Error: Bulk data kickoff request failed with status: {}",response.getStatusCode());
			return null;
		}
		return response.getHeaders().get("Content-Location").get(0);
	}

	//	Checks the status of the bulk data request, retrying until it's complete.
	private ResponseEntity<String> checkBulkDataRequest(String requestUrl, String accessToken)
			throws InterruptedException {
		LOGGER.info("Check bulk data request status: {}",requestUrl);
		Thread.sleep(10000);
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<String> entity = new HttpEntity<>(headers);
		for (int i = 0; i < 3; i++) {
			ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.GET, entity, String.class);
			if(response.getStatusCode().value() == 200) {
				return response;
			} else if(response.getStatusCode().value() == 202) {
				Thread.sleep(10000);
			} else {
				return null;
			}
		}
		return null;
	}

	//	Processes encounter data, fetches patient information, and returns formatted messages.
	private List<String> processEncounters(List<String> encounterUrls, String accessToken)
			throws JsonProcessingException {
		LOGGER.info("Processing Encounters...");
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		HttpEntity<String> entity = new HttpEntity<>(headers);
		Date currentDate = new Date();
		long last24HoursAgoInMillis = currentDate.getTime() - (24 * 60 * 60 * 1000);
		Date last24HoursAgo = new Date(last24HoursAgoInMillis);
		List<String> messages = new ArrayList<>();
		for (String url : encounterUrls) {
			ResponseEntity<String> response = restTemplate.exchange(url,HttpMethod.GET,entity, String.class);
			if (response.getStatusCode().value() == 200) {
				String[] allResponses = response.getBody().split("\\n");
				for (String line : allResponses) {
					Encounter encounter = parser.parseResource(Encounter.class,line);
					if(!isValidEncounter(encounter,currentDate,last24HoursAgo)) {
						String message = buildEncounterMessage(encounter,accessToken);
						if(message != null) {
							messages.add(message);
						}
					}
				}

			}
		}
		return messages;
	}

	//	Validates if the encounter activity happen within the last 24 hours.
	private boolean isValidEncounter(Encounter encounter, Date currentDate, Date last24Hours) {
			Date endDate = encounter.getPeriod().getEnd();
		return endDate != null &&( endDate.before(currentDate) && endDate.after(last24Hours));
	}

	//	Builds a message for an encounter by retrieving related patient information.
	private String buildEncounterMessage(Encounter encounter, String accessToken) {
		String patientId = encounter.getSubject().getReference();
		if(patientId == null) {
			return null;
		}
		String patientUrl = applicationProperties.getBaseUrl()+"R4/"+patientId;
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		headers.set("Accept","application/fhir+json");
		HttpEntity<String> entity = new HttpEntity<>(headers);
		ResponseEntity<String> response = restTemplate.exchange(patientUrl, HttpMethod.GET, entity, String.class);
		if(response.getStatusCode().value() == 200) {
			Patient patient = parser.parseResource(Patient.class,response.getBody());
			return formatEncounterMessage(encounter,patient);
		}
		return null;
	}

	//	Formats the encounter and patient details into a readable message.
	private String formatEncounterMessage(Encounter encounter, Patient patient) {
		return "Encounter ID: "+ encounter.getId() + "\n" +
				"Status: "+encounter.getStatus() +"\n"+
				"Class: "+encounter.getClass_().getCode() + " - "+ encounter.getClass_().getDisplay() + "\n" +
				"Period Start: "+encounter.getPeriod().getStart()+ " , "+ "End: "+encounter.getPeriod().getEnd() +"\n"+
				"Patient ID: "+patient.getId() + "\n" +
				"Name: "+patient.getName().get(0).getText() +"\n" +
				"DOB: "+patient.getBirthDate() + "\n" +
				"Gender: "+patient.getGender() + "\n";
	}

	// 	Retrieves the URL of the requested Resource
	private List<String> getEncounterUrl(String jsonOutput) {
		JsonNode rootNode = null;
		try {
			rootNode = objectMapper.readTree(jsonOutput);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
		JsonNode outputNode = rootNode.get("output");
		List<String> encounterUrls = new ArrayList<>();
		if (outputNode != null && outputNode.isArray()) {
			Iterator<JsonNode> elements = outputNode.elements();
			while (elements.hasNext()) {
				JsonNode item = elements.next();
				if ("Encounter".equals(item.get("type").asText())) {
					encounterUrls.add(item.get("url").asText());
				}
			}
		}
		return encounterUrls;
	}

	//	Deletes the bulk data request.
	private boolean deleteBulkDataRequest(String requestUrl, String accessToken) {
		LOGGER.info("Deleting bulk data request: {}", requestUrl);
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", "Bearer " + accessToken);
		headers.set("Accept", "application/fhir+json");
		headers.set("Prefer", "respond-async");
		HttpEntity<String> entity = new HttpEntity<>(headers);

		ResponseEntity<String> response = restTemplate.exchange(requestUrl, HttpMethod.DELETE, entity, String.class);
		return response.getStatusCode().value() == 200 || response.getStatusCode().value() == 202;
	}

	// 	Loads Private Key form the specified path and converts it to RSA Private Key
	private PrivateKey loadPrivateKey() {
		try {
			File privateKeyFile = new File("private-key.pem");
			String privateKeyPEM = new String(Files.readAllBytes(privateKeyFile.toPath()));
			privateKeyPEM = privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----", "")
					.replace("-----END PRIVATE KEY-----", "")
					.replaceAll("\\s+", "");

			byte[] encoded = Base64.getDecoder().decode(privateKeyPEM);
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
			Key key = KeyFactory.getInstance("RSA").generatePrivate(keySpec);

			return (RSAPrivateKey) key;
		} catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException  e) {
			throw new RuntimeException(e);
		}

	}

	//	Generates a JWT Token
	public String generateToken() {
		PrivateKey privateKey = loadPrivateKey();
		Map<String, Object> claims = new HashMap<>();
		claims.put("iss", applicationProperties.getClientId());
		claims.put("sub", applicationProperties.getClientId());
		claims.put("aud", applicationProperties.getAuthURL());
		claims.put("jti", UUID.randomUUID().toString());
		claims.put("iat", (System.currentTimeMillis() / 1000));
		claims.put("nbf", (System.currentTimeMillis() / 1000));
		claims.put("exp", ((System.currentTimeMillis() + 5 * 60 * 1000) / 1000));

		return Jwts.builder()
				.claims(claims)
				.signWith(privateKey, SignatureAlgorithm.RS256)
				.compact();
	}
}
