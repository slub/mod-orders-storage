package org.folio.services.claiming;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.ClaimingSequence;
import org.folio.rest.jaxrs.model.ClaimingSequenceStage;

/**
 * Brings a claiming sequence into its stored shape before validation: names trimmed, blank notes removed and
 * stage numbers assigned from the position in the stages list (1, 2, 3, ...), overwriting client values.
 */
public class ClaimingSequenceNormalizer {

  public ClaimingSequence normalize(ClaimingSequence claimingSequence) {
    claimingSequence.setName(StringUtils.trim(claimingSequence.getName()));
    List<ClaimingSequenceStage> stages = claimingSequence.getStages();
    for (int i = 0; i < stages.size(); i++) {
      var stage = stages.get(i);
      stage.setStage(i + 1);
      stage.setName(StringUtils.trim(stage.getName()));
      stage.setInternalNote(StringUtils.isBlank(stage.getInternalNote()) ? null : stage.getInternalNote());
      stage.setExternalNote(StringUtils.isBlank(stage.getExternalNote()) ? null : stage.getExternalNote());
    }
    return claimingSequence;
  }

}
