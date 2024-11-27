<h1>Epic on FHIR</h1>
<p>Epic is a strong supporter of the HL7® FHIR® standard as the future of REST-based interoperability. 
Epic on FHIR is a free resource for developers who create apps for use by patients and healthcare organizations.</p>

<hr>

<h2>EPIC REST Services</h2>
<p>EPIC provides various REST services to manipulate resources for FHIR messages. These resources include:</p>
<ul>
    <li>Account</li>
    <li>Encounter</li>
    <li>Patient</li>
    <li>DocumentReference</li>
    <li>Practitioner</li>
    <li>MedicationStatement</li>
    <li>MedicationRequest</li>
    <li>Observation</li>
</ul>

<hr>

<h2>Fast Healthcare Interoperability Resources (FHIR)</h2>
<p>FHIR is a standard for exchanging electronic health records (EHRs) between different systems. 
It is a next-generation interoperability standard developed by the standards organization Health Level 7 (HL7®).</p>

<hr>

<h2>HAPI FHIR</h2>
<p>HAPI FHIR is a comprehensive implementation of the HL7 FHIR standard for healthcare interoperability in Java.</p>
<p>HAPI defines model classes for every resource type and datatype defined by the FHIR specification. 
<strong>FhirContext</strong> is the starting point for using HAPI. It acts as a factory for most parts of the API and serves as a runtime cache of necessary operational information.</p>


<hr>
<h2>Project Description</h2>
<p>The dependencies have been defined within pom.xml.</p>
<strong>NOTE: Make sure to use the compatible versions of the depencies along with Java SDK Version.</strong>

<h3>Steps for execution: </h3>
<ul>
    <li>Add the dependencies according to the compatible version in pom.xml</li>
	<li>You need to create an .properties file within resources folder with following variable names and their data based on the app configured over EPIC</li>
		client.id=ID_ASSIGNED_TO_APP (NON_PRODUCTION_ID if not moved to production) 
		auth.url=https://fhir.epic.com/interconnect-fhir-oauth/oauth2/token (AUTH URL)
		group.id=GROUP_ID (SANDBOX TEST DATA GROUP ID is also mentioned in documentation)
		base.url=https://fhir.epic.com/interconnect-fhir-oauth/api/FHIR/ (BASE URL)
	<li>Setup the project and you're good to go.</li>	
</ul>