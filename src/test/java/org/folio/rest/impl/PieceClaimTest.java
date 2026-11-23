package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.StorageTestSuite;
import org.folio.event.AuditEventType;
import org.folio.rest.jaxrs.model.Piece;
import org.folio.rest.jaxrs.model.PieceAuditEvent;
import org.folio.rest.jaxrs.model.PieceClaim;
import org.folio.rest.jaxrs.model.PieceClaimSequence;
import org.folio.rest.jaxrs.model.PieceCollection;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PurchaseOrder;
import org.folio.rest.jaxrs.model.Title;
import org.folio.rest.util.TestConfig;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies that piece.claim.sequence is stored as sent by the generic piece PUTs (absent = removed), validated on
 * both PUTs, dropped on creation and kept by the claiming timer.
 */
@IsolatedTenant
public class PieceClaimTest extends TestBase {

  private static final String PIECES_BATCH_ENDPOINT = "/orders-storage/pieces-batch";

  private String userId;
  private Headers headers;
  private String poLineId;
  private String titleId;

  @BeforeAll
  public static void beforeAll() throws ExecutionException, InterruptedException, TimeoutException {
    TestConfig.startMockServer();
  }

  @AfterAll
  public static void afterAll() {
    TestConfig.closeMockServer();
  }

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID().toString();
    headers = getIsolatedTenantHeaders(userId);
  }

  // the isolated tenant only exists once the test method runs (BeforeTestExecutionCallback), not in @BeforeEach
  private void createOrderData() throws MalformedURLException {
    PurchaseOrder purchaseOrder = getFileAsObject(TestData.PurchaseOrder.DEFAULT, PurchaseOrder.class)
      .withId(UUID.randomUUID().toString());
    createEntity(PURCHASE_ORDER.getEndpoint(), mapFrom(purchaseOrder).encode(), headers);

    PoLine poLine = getFileAsObject(TestData.PoLine.DEFAULT, PoLine.class)
      .withId(UUID.randomUUID().toString()).withIsPackage(true)
      .withClaimingActive(true).withClaimingInterval(1).withPurchaseOrderId(purchaseOrder.getId());
    createEntity(PO_LINE.getEndpoint(), mapFrom(poLine).encode(), headers);
    poLineId = poLine.getId();

    Title title = getFileAsObject(TestData.Title.DEFAULT, Title.class)
      .withId(UUID.randomUUID().toString()).withClaimingActive(true).withClaimingInterval(1).withPoLineId(poLineId);
    createEntity(TITLES.getEndpoint(), mapFrom(title).encode(), headers);
    titleId = title.getId();
  }

  @Test
  void testSinglePutStoresAndRemovesClaim() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaim claim = claim(2);

    putPiece(piece.withReceivingStatus(Piece.ReceivingStatus.CLAIM_SENT).withClaimingInterval(14).withClaim(claim))
      .then()
      .statusCode(204);
    Piece claimed = getPiece(piece.getId());
    assertEquals(claim, claimed.getClaim());
    assertEquals(14, claimed.getClaimingInterval());

    // the wrapper pieces view returns the stored piece jsonb
    getDataById("/orders-storage/wrapper-pieces/{id}", piece.getId(), headers)
      .then()
      .statusCode(200)
      .body("piece.claim.sequence.id", equalTo(claim.getSequence().getId()))
      .body("piece.claim.sequence.stage", equalTo(2));

    // stored as sent: a changed value replaces it, a piece without claim removes it
    PieceClaim changed = claim(3);
    putPiece(claimed.withClaim(changed)).then().statusCode(204);
    assertEquals(changed, getPiece(piece.getId()).getClaim());

    putPiece(getPiece(piece.getId()).withClaim(null)).then().statusCode(204);
    assertNull(getPiece(piece.getId()).getClaim());
  }

  @Test
  void testBatchPutStoresAndRemovesClaim() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();

    PieceClaim claim = claim(1);
    putPiecesBatch(getPiece(piece.getId()).withComment("Claimed").withClaim(claim)).then().statusCode(204);
    Piece updated = getPiece(piece.getId());
    assertEquals("Claimed", updated.getComment());
    assertEquals(claim, updated.getClaim());

    // claim without sequence is accepted and stored as no claim at all (one form for "no sequence")
    putPiecesBatch(updated.withClaim(new PieceClaim())).then().statusCode(204);
    assertNull(getPiece(piece.getId()).getClaim());

    putPiecesBatch(getPiece(piece.getId()).withClaim(claim)).then().statusCode(204);
    putPiece(getPiece(piece.getId()).withClaim(new PieceClaim())).then().statusCode(204);
    assertNull(getPiece(piece.getId()).getClaim());

    putPiecesBatch(getPiece(piece.getId()).withClaim(null)).then().statusCode(204);
    assertNull(getPiece(piece.getId()).getClaim());
  }

  @Test
  void testSequenceWithoutStage() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    // a sequence chosen for the piece before any stage of it was sent
    PieceClaim claim = new PieceClaim().withSequence(new PieceClaimSequence().withId(UUID.randomUUID().toString()));

    putPiecesBatch(piece.withClaim(claim)).then().statusCode(204);

    Piece stored = getPiece(piece.getId());
    assertEquals(claim.getSequence().getId(), stored.getClaim().getSequence().getId());
    assertNull(stored.getClaim().getSequence().getStage());
  }

  @Test
  void testSinglePutRejectsInvalidClaim() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();

    for (JsonObject invalid : invalidClaims()) {
      putData(PIECE.getEndpointWithId(), piece.getId(), mapFrom(piece).put("claim", invalid).encode(), headers)
        .then()
        .statusCode(422);
    }
    assertNull(getPiece(piece.getId()).getClaim());
  }

  @Test
  void testBatchPutRejectsInvalidSequence() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    Piece other = createPiece(2);

    // RMB validates the batch body against the schema (no @Validate needed): stage 0, missing id and a non-UUID id
    // are rejected with 422 naming the field; nothing of the batch is written
    List<JsonObject> invalidSequences = List.of(
      new JsonObject().put("id", UUID.randomUUID().toString()).put("stage", 0),
      new JsonObject().put("stage", 1),
      new JsonObject().put("id", "not-a-uuid").put("stage", 1));
    for (JsonObject invalid : invalidSequences) {
      JsonObject body = new JsonObject()
        .put("pieces", List.of(
          mapFrom(other.withComment("Changed by batch PUT")),
          mapFrom(piece).put("claim", new JsonObject().put("sequence", invalid))))
        .put("totalRecords", 2);
      putData(PIECES_BATCH_ENDPOINT, body.encode(), headers)
        .then()
        .statusCode(422)
        .body("errors[0].parameters[0].key", startsWith("pieces[1].claim.sequence."));
    }
    assertNull(getPiece(piece.getId()).getClaim());
    assertNotEquals("Changed by batch PUT", getPiece(other.getId()).getComment());
  }

  @Test
  void testBatchPutRejectsUnknownClaimProperty() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();

    JsonObject claim = new JsonObject()
      .put("sequence", new JsonObject().put("id", UUID.randomUUID().toString()).put("stage", 1))
      .put("count", 1);
    JsonObject body = new JsonObject().put("pieces", List.of(mapFrom(piece).put("claim", claim))).put("totalRecords", 1);
    // RMB rejects unknown properties (additionalProperties: false) with 422 before any write
    putData(PIECES_BATCH_ENDPOINT, body.encode(), headers)
      .then()
      .statusCode(422);
    assertNull(getPiece(piece.getId()).getClaim());
  }

  @Test
  void testPieceCreationDropsClaim() throws MalformedURLException {
    createOrderData();
    Piece piece = newPiece(1).withClaim(claim(1));
    createEntity(PIECE.getEndpoint(), mapFrom(piece).encode(), headers);
    assertNull(getPiece(piece.getId()).getClaim());

    Piece batchPiece = newPiece(2).withClaim(claim(1));
    postData(PIECES_BATCH_ENDPOINT, Json.encode(new PieceCollection().withPieces(List.of(batchPiece)).withTotalRecords(1)), headers)
      .then()
      .statusCode(201);
    assertNull(getPiece(batchPiece.getId()).getClaim());
  }

  @Test
  void testClaimingTimerKeepsClaim() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaim claim = claim(1);
    Date eligibleDate = Date.from(LocalDate.now().minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant());

    putPiecesBatch(piece.withReceivingStatus(Piece.ReceivingStatus.CLAIM_SENT).withClaimingInterval(1)
      .withStatusUpdatedDate(eligibleDate).withClaim(claim))
      .then()
      .statusCode(204);

    callClaimingApi(headers);

    Piece late = getPiece(piece.getId());
    assertEquals(Piece.ReceivingStatus.LATE, late.getReceivingStatus());
    assertEquals(claim, late.getClaim());
  }

  @Test
  void testBatchPutSendsAuditEventWithClaim() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaim claim = claim(2);

    putPiecesBatch(piece.withClaim(claim)).then().statusCode(204);

    List<String> events = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 2, userId);
    assertEquals(2, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.EDIT);
    PieceAuditEvent editEvent = Json.decodeValue(events.get(1), PieceAuditEvent.class);
    assertTrue(JsonObject.mapFrom(editEvent.getPieceSnapshot()).encode().contains(claim.getSequence().getId()));
  }

  private static List<JsonObject> invalidClaims() {
    return List.of(
      new JsonObject().put("sequence", new JsonObject().put("id", UUID.randomUUID().toString()).put("stage", 0)),
      new JsonObject().put("sequence", new JsonObject().put("stage", 1)),
      new JsonObject().put("sequence", new JsonObject().put("id", "not-a-uuid").put("stage", 1)),
      new JsonObject().put("sequence", new JsonObject().put("id", UUID.randomUUID().toString()).put("stageNumber", 1)),
      new JsonObject().put("count", 1));
  }

  private Piece newPiece(int sequenceNumber) {
    return getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withPoLineId(poLineId).withTitleId(titleId).withSequenceNumber(sequenceNumber);
  }

  private Piece createPiece() throws MalformedURLException {
    return createPiece(1);
  }

  private Piece createPiece(int sequenceNumber) throws MalformedURLException {
    Piece piece = newPiece(sequenceNumber);
    createEntity(PIECE.getEndpoint(), mapFrom(piece).encode(), headers);
    return getPiece(piece.getId());
  }

  private Piece getPiece(String id) throws MalformedURLException {
    return getDataById(PIECE.getEndpointWithId(), id, headers)
      .then()
      .statusCode(200)
      .extract()
      .as(Piece.class);
  }

  private Response putPiece(Piece piece) throws MalformedURLException {
    return putData(PIECE.getEndpointWithId(), piece.getId(), Json.encode(piece), headers);
  }

  private Response putPiecesBatch(Piece... pieces) throws MalformedURLException {
    return putData(PIECES_BATCH_ENDPOINT, Json.encode(new PieceCollection().withPieces(List.of(pieces))
      .withTotalRecords(pieces.length)), headers);
  }

  private static PieceClaim claim(int stage) {
    return new PieceClaim().withSequence(new PieceClaimSequence().withId(UUID.randomUUID().toString()).withStage(stage));
  }
}
