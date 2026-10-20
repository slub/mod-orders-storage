package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.utils.TestEntities.PIECE;
import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.folio.rest.utils.TestEntities.TITLES;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
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
import org.folio.rest.jaxrs.model.PieceClaimCollection;
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
 * Verifies that piece.claimSequence is only written by POST /orders-storage/pieces-batch/claims and kept by every
 * other piece write.
 */
@IsolatedTenant
public class PieceClaimSequenceTest extends TestBase {

  private static final String PIECES_BATCH_ENDPOINT = "/orders-storage/pieces-batch";
  private static final String PIECES_CLAIMS_ENDPOINT = "/orders-storage/pieces-batch/claims";

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
  void testClaimEndpointSetsAndRemovesClaimSequence() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaimSequence claimSequence = claimSequence(2);

    postClaims(piece.withReceivingStatus(Piece.ReceivingStatus.CLAIM_SENT).withClaimingInterval(14).withClaimSequence(claimSequence))
      .then()
      .statusCode(204);

    Piece claimed = getPiece(piece.getId());
    assertEquals(claimSequence, claimed.getClaimSequence());
    assertEquals(Piece.ReceivingStatus.CLAIM_SENT, claimed.getReceivingStatus());
    assertEquals(14, claimed.getClaimingInterval());

    // the wrapper pieces view returns the stored piece jsonb
    getDataById("/orders-storage/wrapper-pieces/{id}", piece.getId(), headers)
      .then()
      .statusCode(200)
      .body("piece.claimSequence.id", equalTo(claimSequence.getId()))
      .body("piece.claimSequence.stage", equalTo(2));

    // the endpoint is authoritative: a piece without the field has it removed
    postClaims(claimed.withClaimSequence(null))
      .then()
      .statusCode(204);
    assertNull(getPiece(piece.getId()).getClaimSequence());
  }

  @Test
  void testClaimEndpointIsAllOrNothing() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    Piece unknownPiece = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withPoLineId(poLineId).withTitleId(titleId);

    postClaims(piece.withClaimSequence(claimSequence(1)), unknownPiece.withClaimSequence(claimSequence(1)))
      .then()
      .statusCode(404);

    assertNull(getPiece(piece.getId()).getClaimSequence());
  }

  @Test
  void testClaimEndpointRejectsInvalidClaimSequence() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();

    for (JsonObject invalid : List.of(
      new JsonObject().put("id", UUID.randomUUID().toString()).put("stage", 0),
      new JsonObject().put("stage", 1),
      new JsonObject().put("id", "not-a-uuid").put("stage", 1),
      new JsonObject().put("id", UUID.randomUUID().toString()).put("stage", 1).put("stageNumber", 1))) {
      JsonObject body = new JsonObject().put("pieceClaims", List.of(new JsonObject()
        .put("piece", JsonObject.mapFrom(piece).put("claimSequence", invalid))));
      postData(PIECES_CLAIMS_ENDPOINT, body.encode(), headers)
        .then()
        .statusCode(422);
    }

    assertNull(getPiece(piece.getId()).getClaimSequence());
  }

  @Test
  void testClaimSequenceWithoutStage() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    // a sequence chosen for the piece before any stage of it was sent
    PieceClaimSequence claimSequence = new PieceClaimSequence().withId(UUID.randomUUID().toString());

    postClaims(piece.withClaimSequence(claimSequence))
      .then()
      .statusCode(204);

    Piece stored = getPiece(piece.getId());
    assertEquals(claimSequence.getId(), stored.getClaimSequence().getId());
    assertNull(stored.getClaimSequence().getStage());
  }

  @Test
  void testClaimEndpointWithEmptyList() throws MalformedURLException {
    postData(PIECES_CLAIMS_ENDPOINT, Json.encode(new PieceClaimCollection()), headers)
      .then()
      .statusCode(204);
  }

  @Test
  void testPieceUpdatesKeepStoredClaimSequence() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaimSequence claimSequence = claimSequence(3);
    postClaims(piece.withClaimSequence(claimSequence)).then().statusCode(204);

    // single PUT with a different value and with the field removed
    putData(PIECE.getEndpointWithId(), piece.getId(), Json.encode(getPiece(piece.getId())
      .withComment("Changed by PUT").withClaimSequence(claimSequence(1))), headers)
      .then()
      .statusCode(204);
    Piece updated = getPiece(piece.getId());
    assertEquals("Changed by PUT", updated.getComment());
    assertEquals(claimSequence, updated.getClaimSequence());

    putData(PIECE.getEndpointWithId(), piece.getId(), Json.encode(updated.withClaimSequence(null)), headers)
      .then()
      .statusCode(204);
    assertEquals(claimSequence, getPiece(piece.getId()).getClaimSequence());

    // batch PUT with a different value and with the field removed
    for (PieceClaimSequence requested : new PieceClaimSequence[] {claimSequence(5), null}) {
      PieceCollection pieces = new PieceCollection()
        .withPieces(List.of(getPiece(piece.getId()).withComment("Changed by batch PUT").withClaimSequence(requested)))
        .withTotalRecords(1);
      putData(PIECES_BATCH_ENDPOINT, Json.encode(pieces), headers)
        .then()
        .statusCode(204);
      updated = getPiece(piece.getId());
      assertEquals("Changed by batch PUT", updated.getComment());
      assertEquals(claimSequence, updated.getClaimSequence());
    }
  }

  @Test
  void testPieceCreationDropsClaimSequence() throws MalformedURLException {
    createOrderData();
    Piece piece = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withPoLineId(poLineId).withTitleId(titleId)
      .withClaimSequence(claimSequence(1));
    createEntity(PIECE.getEndpoint(), mapFrom(piece).encode(), headers);
    assertNull(getPiece(piece.getId()).getClaimSequence());

    Piece batchPiece = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withPoLineId(poLineId).withTitleId(titleId).withSequenceNumber(2)
      .withClaimSequence(claimSequence(1));
    postData(PIECES_BATCH_ENDPOINT, Json.encode(new PieceCollection().withPieces(List.of(batchPiece)).withTotalRecords(1)), headers)
      .then()
      .statusCode(201);
    assertNull(getPiece(batchPiece.getId()).getClaimSequence());
  }

  @Test
  void testClaimingTimerKeepsClaimSequence() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaimSequence claimSequence = claimSequence(1);
    Date eligibleDate = Date.from(LocalDate.now().minusDays(2).atStartOfDay(ZoneId.systemDefault()).toInstant());

    postClaims(piece.withReceivingStatus(Piece.ReceivingStatus.CLAIM_SENT).withClaimingInterval(1)
      .withStatusUpdatedDate(eligibleDate).withClaimSequence(claimSequence))
      .then()
      .statusCode(204);

    callClaimingApi(headers);

    Piece late = getPiece(piece.getId());
    assertEquals(Piece.ReceivingStatus.LATE, late.getReceivingStatus());
    assertEquals(claimSequence, late.getClaimSequence());
  }

  @Test
  void testClaimEndpointSendsAuditEvent() throws MalformedURLException {
    createOrderData();
    Piece piece = createPiece();
    PieceClaimSequence claimSequence = claimSequence(2);

    postClaims(piece.withClaimSequence(claimSequence)).then().statusCode(204);

    List<String> events = StorageTestSuite.checkKafkaEventSent(ISOLATED_TENANT, AuditEventType.ACQ_PIECE_CHANGED.getTopicName(), 2, userId);
    assertEquals(2, events.size());
    checkPieceEventContent(events.get(0), PieceAuditEvent.Action.CREATE);
    checkPieceEventContent(events.get(1), PieceAuditEvent.Action.EDIT);
    PieceAuditEvent editEvent = Json.decodeValue(events.get(1), PieceAuditEvent.class);
    assertTrue(JsonObject.mapFrom(editEvent.getPieceSnapshot()).encode().contains(claimSequence.getId()));
  }

  private Piece createPiece() throws MalformedURLException {
    Piece piece = getFileAsObject(TestData.Piece.DEFAULT, Piece.class)
      .withId(UUID.randomUUID().toString()).withPoLineId(poLineId).withTitleId(titleId);
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

  private Response postClaims(Piece... pieces) throws MalformedURLException {
    List<PieceClaim> pieceClaims = Arrays.stream(pieces).map(piece -> new PieceClaim().withPiece(piece)).toList();
    return postData(PIECES_CLAIMS_ENDPOINT, Json.encode(new PieceClaimCollection().withPieceClaims(pieceClaims)
      .withTotalRecords(pieceClaims.size())), headers);
  }

  private static PieceClaimSequence claimSequence(int stage) {
    return new PieceClaimSequence().withId(UUID.randomUUID().toString()).withStage(stage);
  }
}
