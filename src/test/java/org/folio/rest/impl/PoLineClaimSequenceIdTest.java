package org.folio.rest.impl;

import static org.folio.rest.utils.TestEntities.PO_LINE;
import static org.folio.rest.utils.TestEntities.PURCHASE_ORDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.restassured.http.Headers;
import io.vertx.core.json.Json;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;
import org.folio.rest.jaxrs.model.PoLine;
import org.folio.rest.jaxrs.model.PoLineCollection;
import org.folio.rest.utils.IsolatedTenant;
import org.folio.rest.utils.TestData;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the optional claimSequenceId is stored on PO lines as is: storage does not check the
 * referenced claim sequence, it only keeps or removes the id.
 */
@IsolatedTenant
public class PoLineClaimSequenceIdTest extends TestBase {

  private static final String PO_LINES_BATCH_ENDPOINT = "/orders-storage/po-lines-batch";

  private final Headers headers = getIsolatedTenantHeaders(UUID.randomUUID().toString());

  @Test
  void testClaimSequenceIdIsStoredAndRemoved() throws MalformedURLException {
    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));
    String claimSequenceId = UUID.randomUUID().toString();

    PoLine created = postData(PO_LINE.getEndpoint(), Json.encode(samplePoLine().withClaimSequenceId(claimSequenceId)), headers)
      .then()
      .statusCode(201)
      .extract()
      .as(PoLine.class);
    assertEquals(claimSequenceId, getPoLine(created.getId()).getClaimSequenceId());

    // removing the field switches the line back to "Claim by: Interval"
    putData(PO_LINE.getEndpointWithId(), created.getId(), Json.encode(created.withClaimSequenceId(null)), headers)
      .then()
      .statusCode(204);
    assertNull(getPoLine(created.getId()).getClaimSequenceId());
  }

  @Test
  void testBatchUpdateKeepsClaimSequenceId() throws MalformedURLException {
    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));
    String claimSequenceId = UUID.randomUUID().toString();

    PoLine created = postData(PO_LINE.getEndpoint(), Json.encode(samplePoLine().withClaimSequenceId(claimSequenceId)), headers)
      .as(PoLine.class);

    String batch = Json.encode(new PoLineCollection()
      .withPoLines(List.of(created.withTitleOrPackage("Changed title")))
      .withTotalRecords(1));
    putData(PO_LINES_BATCH_ENDPOINT, batch, headers)
      .then()
      .statusCode(204);

    PoLine updated = getPoLine(created.getId());
    assertEquals("Changed title", updated.getTitleOrPackage());
    assertEquals(claimSequenceId, updated.getClaimSequenceId());
  }

  @Test
  void testMalformedClaimSequenceIdIsRejected() throws MalformedURLException {
    givenTestData(Pair.of(PURCHASE_ORDER, TestData.PurchaseOrder.DEFAULT));

    postData(PO_LINE.getEndpoint(), Json.encode(samplePoLine().withClaimSequenceId("not-a-uuid")), headers)
      .then()
      .statusCode(422);
  }

  private PoLine samplePoLine() {
    return Json.decodeValue(getFile(TestData.PoLine.DEFAULT), PoLine.class)
      .withId(UUID.randomUUID().toString());
  }

  private PoLine getPoLine(String id) throws MalformedURLException {
    return getDataById(PO_LINE.getEndpointWithId(), id, headers)
      .then()
      .statusCode(200)
      .extract()
      .as(PoLine.class);
  }
}
