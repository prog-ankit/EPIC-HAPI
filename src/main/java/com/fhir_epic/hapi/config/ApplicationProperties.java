package com.fhir_epic.hapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationProperties {
	@Value("${client.id}")
	String clientId;

	@Value("${auth.url}")
	String authURL;

	@Value("${group.id}")
	String groupId;

	@Value("${base.url}")
	String baseUrl;

	public String getClientId() {
		return clientId;
	}

	public String getAuthURL() {
		return authURL;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getBaseUrl() {
		return baseUrl;
	}
}
