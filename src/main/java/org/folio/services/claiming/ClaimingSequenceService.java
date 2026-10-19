package org.folio.services.claiming;

import static org.folio.models.TableNames.CLAIMING_SEQUENCES_TABLE;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.core.ResponseUtil;
import org.folio.rest.exceptions.HttpException;
import org.folio.rest.jaxrs.model.ClaimingSequence;
import org.folio.rest.jaxrs.model.ClaimingSequenceCollection;
import org.folio.rest.jaxrs.resource.OrdersStorageClaimingSequences;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class ClaimingSequenceService {

  private static final String SORT_BY_NAME = "sortBy name";
  private static final String ALL_RECORDS_SORTED_BY_NAME = "cql.allRecords=1 " + SORT_BY_NAME;
  private static final Pattern SORT_BY_PATTERN = Pattern.compile("\\bsortby\\b", Pattern.CASE_INSENSITIVE);

  private final ClaimingSequenceNormalizer normalizer;
  private final ClaimingSequenceValidator validator;

  public void getClaimingSequences(String query, int offset, int limit, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(CLAIMING_SEQUENCES_TABLE, ClaimingSequence.class, ClaimingSequenceCollection.class, withDefaultSort(query),
      offset, limit, okapiHeaders, vertxContext, OrdersStorageClaimingSequences.GetOrdersStorageClaimingSequencesResponse.class,
      asyncResultHandler);
  }

  public void getClaimingSequence(String id, Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(CLAIMING_SEQUENCES_TABLE, ClaimingSequence.class, id, okapiHeaders, vertxContext,
      OrdersStorageClaimingSequences.GetOrdersStorageClaimingSequencesByIdResponse.class, asyncResultHandler);
  }

  public void createClaimingSequence(ClaimingSequence entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (isInvalid(entity, asyncResultHandler)) {
      return;
    }
    PgUtil.post(CLAIMING_SEQUENCES_TABLE, entity, okapiHeaders, vertxContext,
      OrdersStorageClaimingSequences.PostOrdersStorageClaimingSequencesResponse.class, asyncResultHandler);
  }

  public void updateClaimingSequence(String id, ClaimingSequence entity, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    if (isInvalid(entity, asyncResultHandler)) {
      return;
    }
    PgUtil.put(CLAIMING_SEQUENCES_TABLE, entity, id, okapiHeaders, vertxContext,
      OrdersStorageClaimingSequences.PutOrdersStorageClaimingSequencesByIdResponse.class, asyncResultHandler);
  }

  public void deleteClaimingSequence(String id, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(CLAIMING_SEQUENCES_TABLE, id, okapiHeaders, vertxContext,
      OrdersStorageClaimingSequences.DeleteOrdersStorageClaimingSequencesByIdResponse.class, asyncResultHandler);
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

  private boolean isInvalid(ClaimingSequence entity, Handler<AsyncResult<Response>> asyncResultHandler) {
    var errors = validator.validate(normalizer.normalize(entity));
    if (errors.getErrors().isEmpty()) {
      return false;
    }
    log.warn("isInvalid:: Claiming sequence '{}' rejected with {} error(s)", entity.getId(), errors.getTotalRecords());
    asyncResultHandler.handle(ResponseUtil.buildErrorResponse(new HttpException(HttpStatus.SC_UNPROCESSABLE_ENTITY, errors)));
    return true;
  }

}
