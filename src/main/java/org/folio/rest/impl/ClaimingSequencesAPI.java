package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;

import javax.ws.rs.core.Response;
import java.util.Map;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.rest.annotations.Validate;
import org.folio.rest.core.BaseApi;
import org.folio.rest.jaxrs.model.ClaimingSequence;
import org.folio.rest.jaxrs.resource.OrdersStorageClaimingSequences;
import org.folio.rest.persist.HelperUtils;
import org.folio.services.claiming.ClaimingSequenceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class ClaimingSequencesAPI extends BaseApi implements OrdersStorageClaimingSequences {

  @Autowired
  private ClaimingSequenceService claimingSequenceService;

  public ClaimingSequencesAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getOrdersStorageClaimingSequences(String query, String totalRecords, int offset, int limit,
                                                Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    claimingSequenceService.getClaimingSequences(query, offset, limit, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void postOrdersStorageClaimingSequences(ClaimingSequence entity, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimingSequenceService.createClaimingSequence(entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void getOrdersStorageClaimingSequencesById(String id, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimingSequenceService.getClaimingSequence(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void putOrdersStorageClaimingSequencesById(String id, ClaimingSequence entity, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimingSequenceService.updateClaimingSequence(id, entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void deleteOrdersStorageClaimingSequencesById(String id, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimingSequenceService.deleteClaimingSequence(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageClaimingSequences.class) + mapFrom(entity).getString("id");
  }

}
