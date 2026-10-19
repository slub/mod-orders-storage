package org.folio.services.claiming;

import static org.folio.rest.exceptions.ErrorCodes.CLAIM_SEQUENCE_NAME_REQUIRED;
import static org.folio.rest.exceptions.ErrorCodes.CLAIM_SEQUENCE_STAGE_NAME_REQUIRED;
import static org.folio.rest.exceptions.ErrorCodes.TRANSMISSION_METHOD_NOT_ALLOWED;
import static org.folio.rest.exceptions.ErrorCodes.TRANSMISSION_METHOD_REQUIRED;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.exceptions.ErrorCodes;
import org.folio.rest.jaxrs.model.ClaimSequence;
import org.folio.rest.jaxrs.model.ClaimSequenceStage;
import org.folio.rest.jaxrs.model.ClaimSequenceStage.DeliveryType;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Parameter;

/**
 * Checks the rules of a normalized claim sequence that the JSON schema cannot express. Reports every violation,
 * not only the first one. Schema rules (required fields, enums, ranges) are checked by RMB before.
 */
public class ClaimSequenceValidator {

  private static final String STAGE_PARAMETER = "stage";
  private static final String STAGE_NAME_PARAMETER = "stageName";

  public Errors validate(ClaimSequence claimSequence) {
    List<Error> errors = new ArrayList<>();
    if (StringUtils.isBlank(claimSequence.getName())) {
      errors.add(CLAIM_SEQUENCE_NAME_REQUIRED.toError());
    }
    for (ClaimSequenceStage stage : claimSequence.getStages()) {
      if (StringUtils.isBlank(stage.getName())) {
        errors.add(stageError(CLAIM_SEQUENCE_STAGE_NAME_REQUIRED, stage));
      }
      if (stage.getDeliveryType() == DeliveryType.INTEGRATION && stage.getTransmissionMethod() == null) {
        errors.add(stageError(TRANSMISSION_METHOD_REQUIRED, stage));
      }
      if (stage.getDeliveryType() == DeliveryType.MANUAL && stage.getTransmissionMethod() != null) {
        errors.add(stageError(TRANSMISSION_METHOD_NOT_ALLOWED, stage));
      }
    }
    return new Errors().withErrors(errors).withTotalRecords(errors.size());
  }

  private Error stageError(ErrorCodes errorCode, ClaimSequenceStage stage) {
    return errorCode.toError().withParameters(List.of(
      new Parameter().withKey(STAGE_PARAMETER).withValue(String.valueOf(stage.getStage())),
      new Parameter().withKey(STAGE_NAME_PARAMETER).withValue(stage.getName())));
  }

}
