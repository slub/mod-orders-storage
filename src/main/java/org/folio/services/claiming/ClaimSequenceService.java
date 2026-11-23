package org.folio.services.claiming;

import static org.folio.models.TableNames.CLAIM_SEQUENCES_TABLE;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.core.ResponseUtil;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.ClaimSequence;
import org.folio.rest.jaxrs.model.ClaimSequenceCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageClaimSequences;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class ClaimSequenceService {

  private static final String SORT_BY_NAME = "sortBy name";
  private static final String ALL_RECORDS_SORTED_BY_NAME = "cql.allRecords=1 " + SORT_BY_NAME;
  private static final Pattern SORT_BY_PATTERN = Pattern.compile("\\bsortby\\b", Pattern.CASE_INSENSITIVE);

  private final ClaimSequenceNormalizer normalizer;
  private final ClaimSequenceValidator validator;

  public void getClaimSequences(String query, int offset, int limit, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(CLAIM_SEQUENCES_TABLE, ClaimSequence.class, ClaimSequenceCollection.class, withDefaultSort(query),
      offset, limit, okapiHeaders, vertxContext, OrdersStorageClaimSequences.GetOrdersStorageClaimSequencesResponse.class,
      asyncResultHandler);
  }

  public void getClaimSequence(String id, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(CLAIM_SEQUENCES_TABLE, ClaimSequence.class, id, okapiHeaders, vertxContext,
      OrdersStorageClaimSequences.GetOrdersStorageClaimSequencesByIdResponse.class, asyncResultHandler);
  }

  public void createClaimSequence(ClaimSequence entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (isInvalid(entity, asyncResultHandler)) {
      return;
    }
    PgUtil.post(CLAIM_SEQUENCES_TABLE, entity, okapiHeaders, vertxContext,
      OrdersStorageClaimSequences.PostOrdersStorageClaimSequencesResponse.class, asyncResultHandler);
  }

  public void updateClaimSequence(String id, ClaimSequence entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (isInvalid(entity, asyncResultHandler)) {
      return;
    }
    PgUtil.put(CLAIM_SEQUENCES_TABLE, entity, id, okapiHeaders, vertxContext,
      OrdersStorageClaimSequences.PutOrdersStorageClaimSequencesByIdResponse.class, asyncResultHandler);
  }

  public void deleteClaimSequence(String id, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(CLAIM_SEQUENCES_TABLE, id, okapiHeaders, vertxContext,
      OrdersStorageClaimSequences.DeleteOrdersStorageClaimSequencesByIdResponse.class, asyncResultHandler);
  }

  /**
   * Sorts by name unless the client asked for an order itself.
   */
  static String withDefaultSort(String query) {
    if (StringUtils.isBlank(query)) {
      return ALL_RECORDS_SORTED_BY_NAME;
    }
    return SORT_BY_PATTERN.matcher(query).find() ? query : query + " " + SORT_BY_NAME;
  }

  private boolean isInvalid(ClaimSequence entity, Handler<AsyncResult<Response>> asyncResultHandler) {
    var errors = validator.validate(normalizer.normalize(entity));
    if (errors.getErrors().isEmpty()) {
      return false;
    }
    log.warn("isInvalid:: Claim sequence '{}' rejected with {} error(s)", entity.getId(), errors.getTotalRecords());
    asyncResultHandler.handle(ResponseUtil.buildErrorResponse(new HttpException(HttpStatus.SC_UNPROCESSABLE_ENTITY, errors)));
    return true;
  }

}
