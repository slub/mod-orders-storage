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
import org.folio.rest.jaxrs.model.ClaimSequence;
import org.folio.rest.jaxrs.resource.OrdersStorageClaimSequences;
import org.folio.rest.persist.HelperUtils;
import org.folio.services.claiming.ClaimSequenceService;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

public class ClaimSequencesAPI extends BaseApi implements OrdersStorageClaimSequences {

  @Autowired
  private ClaimSequenceService claimSequenceService;

  public ClaimSequencesAPI() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  public void getOrdersStorageClaimSequences(String query, String totalRecords, int offset, int limit,
                                                Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
                                                Context vertxContext) {
    claimSequenceService.getClaimSequences(query, offset, limit, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void postOrdersStorageClaimSequences(ClaimSequence entity, Map<String, String> okapiHeaders,
                                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimSequenceService.createClaimSequence(entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void getOrdersStorageClaimSequencesById(String id, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimSequenceService.getClaimSequence(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void putOrdersStorageClaimSequencesById(String id, ClaimSequence entity, Map<String, String> okapiHeaders,
                                                    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimSequenceService.updateClaimSequence(id, entity, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  @Validate
  public void deleteOrdersStorageClaimSequencesById(String id, Map<String, String> okapiHeaders,
                                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    claimSequenceService.deleteClaimSequence(id, okapiHeaders, asyncResultHandler, vertxContext);
  }

  @Override
  protected String getEndpoint(Object entity) {
    return HelperUtils.getEndpoint(OrdersStorageClaimSequences.class) + mapFrom(entity).getString("id");
  }

}
