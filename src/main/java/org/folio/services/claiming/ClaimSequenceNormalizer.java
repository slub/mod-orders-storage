package org.folio.services.claiming;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.ClaimSequence;
import org.folio.rest.jaxrs.model.ClaimSequenceStage;

/**
 * Brings a claim sequence into its stored shape before validation: names trimmed, blank notes removed and
 * stage numbers assigned from the position in the stages list (1, 2, 3, ...), overwriting client values.
 */
public class ClaimSequenceNormalizer {

  public ClaimSequence normalize(ClaimSequence claimSequence) {
    claimSequence.setName(StringUtils.trim(claimSequence.getName()));
    List<ClaimSequenceStage> stages = claimSequence.getStages();
    for (int i = 0; i < stages.size(); i++) {
      var stage = stages.get(i);
      stage.setStage(i + 1);
      stage.setName(StringUtils.trim(stage.getName()));
      stage.setInternalNote(StringUtils.isBlank(stage.getInternalNote()) ? null : stage.getInternalNote());
      stage.setExternalNote(StringUtils.isBlank(stage.getExternalNote()) ? null : stage.getExternalNote());
    }
    return claimSequence;
  }

}
