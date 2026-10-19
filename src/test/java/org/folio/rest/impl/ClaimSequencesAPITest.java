package org.folio.rest.impl;

import static io.restassured.RestAssured.given;

import static org.folio.StorageTestSuite.storageUrl;
import static org.folio.rest.utils.TestEntities.CLAIM_SEQUENCE;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.http.ContentType;
import io.restassured.http.Headers;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.net.MalformedURLException;
import java.util.UUID;

import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.Test;

/**
 * Covers the rules of /orders-storage/claim-sequences that the generic EntitiesCrudTest does not:
 * stage numbering, stage validation, unique names, optimistic locking and the default sort.
 */
@IsolatedTenant
public class ClaimSequencesAPITest extends TestBase {

  private final Headers headers = getIsolatedTenantHeaders(UUID.randomUUID().toString());

  @Test
  void testStagesAreNumberedFromListPosition() throws MalformedURLException {
    JsonObject sequence = newSequence("Numbering");
    JsonArray stages = sequence.getJsonArray("stages");
    stages.getJsonObject(0).put("stage", 5);
    stages.getJsonObject(1).put("stage", 2);
    stages.getJsonObject(2).put("stage", 2);
    stages.getJsonObject(3).remove("stage");

    String id = createEntity(CLAIM_SEQUENCE.getEndpoint(), sequence.encode(), headers);

    getSequence(id)
      .body("stages.stage", contains(1, 2, 3, 4, 5, 6))
      .body("stages.name", contains("1st claim", "2nd claim", "3rd claim",
        "1st phone reminder", "2nd phone reminder", "3rd phone reminder"));
    querySequences("name==\"Numbering\"")
      .body("claimSequences[0].stages.stage", contains(1, 2, 3, 4, 5, 6));
  }

  @Test
  void testReorderedStagesAreRenumbered() throws MalformedURLException {
    String id = createEntity(CLAIM_SEQUENCE.getEndpoint(), newSequence("Reorder").encode(), headers);
    JsonObject stored = new JsonObject(getSequence(id).extract().asString());
    JsonArray stages = stored.getJsonArray("stages");
    JsonObject first = stages.getJsonObject(0);
    stages.remove(0);
    stages.add(first);

    putSequence(id, stored).statusCode(204);

    getSequence(id)
      .body("stages.stage", contains(1, 2, 3, 4, 5, 6))
      .body("stages.name", contains("2nd claim", "3rd claim",
        "1st phone reminder", "2nd phone reminder", "3rd phone reminder", "1st claim"));
  }

  @Test
  void testDuplicateStageNamesAreAllowed() throws MalformedURLException {
    JsonObject sequence = newSequence("Same stage names");
    sequence.getJsonArray("stages").getJsonObject(2).put("name", "1st claim");

    createEntity(CLAIM_SEQUENCE.getEndpoint(), sequence.encode(), headers);
  }

  @Test
  void testDuplicateSequenceNameIsRejected() throws MalformedURLException {
    createEntity(CLAIM_SEQUENCE.getEndpoint(), newSequence("Serials").encode(), headers);

    postSequence(newSequence("serials"))
      .statusCode(422)
      .contentType(ContentType.JSON)
      .body(containsString("value already exists in table"));
    postSequence(newSequence("  Sérials  "))
      .statusCode(422);
  }

  @Test
  void testStageRulesAreValidated() throws MalformedURLException {
    JsonObject sequence = newSequence("Invalid stages");
    JsonArray stages = sequence.getJsonArray("stages");
    stages.getJsonObject(0).remove("transmissionMethod");
    stages.getJsonObject(1).put("name", "   ");
    stages.getJsonObject(3).put("transmissionMethod", "FTP");

    postSequence(sequence)
      .statusCode(422)
      .body("errors.code", contains("transmissionMethodRequired", "claimSequenceStageNameRequired",
        "transmissionMethodNotAllowed"))
      .body("errors[0].parameters.find { it.key == 'stageName' }.value", equalTo("1st claim"))
      .body("errors[2].parameters.find { it.key == 'stage' }.value", equalTo("4"))
      .body("errors[2].parameters.find { it.key == 'stageName' }.value", equalTo("1st phone reminder"));
  }

  @Test
  void testBlankSequenceNameIsRejected() throws MalformedURLException {
    postSequence(newSequence("   "))
      .statusCode(422)
      .body("errors.code", contains("claimSequenceNameRequired"));
  }

  @Test
  void testSchemaViolations() throws MalformedURLException {
    postSequence(newSequence("Empty").put("stages", new JsonArray())).statusCode(422);
    postSequence(withFirstStage(newSequence("Zero"), "interval", 0)).statusCode(422);
    postSequence(withFirstStage(newSequence("Too long"), "interval", 3651)).statusCode(422);
    postSequence(withFirstStage(newSequence("Unknown property"), "stageNumber", 1)).statusCode(422);
    // 400, not 422: an unknown enum value is rejected while deserializing the body, before bean validation runs
    postSequence(withFirstStage(newSequence("Unknown enum"), "deliveryType", "PHONE")).statusCode(400);
  }

  @Test
  void testNotesAndDefaults() throws MalformedURLException {
    JsonObject sequence = newSequence("Notes");
    sequence.remove("afterLastStage");
    sequence.remove("isActive");
    JsonArray stages = sequence.getJsonArray("stages");
    stages.getJsonObject(0).put("internalNote", "Sent by email").put("externalNote", "  ");
    stages.getJsonObject(3).put("externalNote", "Please call back");

    String id = createEntity(CLAIM_SEQUENCE.getEndpoint(), sequence.encode(), headers);

    JsonObject stored = new JsonObject(getSequence(id).extract().asString());
    JsonObject integration = stored.getJsonArray("stages").getJsonObject(0);
    JsonObject manual = stored.getJsonArray("stages").getJsonObject(3);
    assertEquals("STOP", stored.getString("afterLastStage"));
    assertTrue(stored.getBoolean("isActive"));
    assertEquals("Please call back", manual.getString("externalNote"));
    assertEquals("Sent by email", integration.getString("internalNote"));
    assertFalse(integration.containsKey("externalNote"));
  }

  @Test
  void testOptimisticLocking() throws MalformedURLException {
    String id = createEntity(CLAIM_SEQUENCE.getEndpoint(), newSequence("Locking").encode(), headers);
    JsonObject stored = new JsonObject(getSequence(id).extract().asString());

    putSequence(id, stored.copy().put("description", "first")).statusCode(204);
    // the same _version again is stale now
    putSequence(id, stored.copy().put("description", "second")).statusCode(409);
    // a PUT without _version is a conflict too
    JsonObject withoutVersion = stored.copy().put("description", "third");
    withoutVersion.remove("_version");
    putSequence(id, withoutVersion).statusCode(409);

    getSequence(id).body("description", equalTo("first"));
  }

  @Test
  void testCollectionIsSortedByNameByDefault() throws MalformedURLException {
    JsonObject inactive = newSequence("c").put("isActive", false);
    createEntity(CLAIM_SEQUENCE.getEndpoint(), newSequence("b").encode(), headers);
    createEntity(CLAIM_SEQUENCE.getEndpoint(), inactive.encode(), headers);
    createEntity(CLAIM_SEQUENCE.getEndpoint(), newSequence("A").encode(), headers);
    createEntity(CLAIM_SEQUENCE.getEndpoint(), newSequence("d").encode(), headers);

    querySequences(null).body("claimSequences.name", contains("A", "b", "c", "d"));
    querySequences("isActive==true").body("claimSequences.name", contains("A", "b", "d"));
    querySequences("cql.allRecords=1 sortBy name/sort.descending").body("claimSequences.name", contains("d", "c", "b", "A"));
  }

  private JsonObject newSequence(String name) {
    return new JsonObject(getFile(TestData.ClaimSequence.DEFAULT))
      .put("id", UUID.randomUUID().toString())
      .put("name", name);
  }

  private static JsonObject withFirstStage(JsonObject sequence, String key, Object value) {
    sequence.getJsonArray("stages").getJsonObject(0).put(key, value);
    return sequence;
  }

  private ValidatableResponse postSequence(JsonObject sequence) throws MalformedURLException {
    return postData(CLAIM_SEQUENCE.getEndpoint(), sequence.encode(), headers).then().log().ifValidationFails();
  }

  private ValidatableResponse putSequence(String id, JsonObject sequence) throws MalformedURLException {
    return putData(CLAIM_SEQUENCE.getEndpointWithId(), id, sequence.encode(), headers).then().log().ifValidationFails();
  }

  private ValidatableResponse getSequence(String id) throws MalformedURLException {
    return getDataById(CLAIM_SEQUENCE.getEndpointWithId(), id, headers).then().log().ifValidationFails().statusCode(200);
  }

  private ValidatableResponse querySequences(String cql) throws MalformedURLException {
    var request = given().headers(headers).contentType(ContentType.JSON);
    if (cql != null) {
      request.queryParam("query", cql);
    }
    return request.when().get(storageUrl(CLAIM_SEQUENCE.getEndpoint())).then().log().ifValidationFails().statusCode(200);
  }

}
