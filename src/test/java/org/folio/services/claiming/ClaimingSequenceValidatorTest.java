package org.folio.services.claiming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.ClaimingSequence;
import org.folio.rest.jaxrs.model.ClaimingSequenceStage;
import org.folio.rest.jaxrs.model.ClaimingSequenceStage.DeliveryType;
import org.folio.rest.jaxrs.model.ClaimingSequenceStage.TransmissionMethod;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.junit.jupiter.api.Test;

public class ClaimingSequenceValidatorTest {

  private final ClaimingSequenceNormalizer normalizer = new ClaimingSequenceNormalizer();
  private final ClaimingSequenceValidator validator = new ClaimingSequenceValidator();

  @Test
  void testNormalizeAssignsStageNumbersFromListPosition() {
    var claimingSequence = sequence("Serials",
      manualStage("Phone").withStage(5),
      integrationStage("Email", TransmissionMethod.EMAIL).withStage(2),
      integrationStage("Email", TransmissionMethod.EMAIL).withStage(2));

    normalizer.normalize(claimingSequence);

    assertEquals(List.of(1, 2, 3), claimingSequence.getStages().stream().map(ClaimingSequenceStage::getStage).toList());
  }

  @Test
  void testNormalizeTrimsNamesAndDropsBlankNotes() {
    var claimingSequence = sequence("  Serials  ",
      manualStage("  Phone  ").withInternalNote("   ").withExternalNote(" Please call back "));

    normalizer.normalize(claimingSequence);

    var stage = claimingSequence.getStages().getFirst();
    assertEquals("Serials", claimingSequence.getName());
    assertEquals("Phone", stage.getName());
    assertNull(stage.getInternalNote());
    assertEquals(" Please call back ", stage.getExternalNote());
  }

  @Test
  void testValidSequenceHasNoErrors() {
    var claimingSequence = sequence("Serials",
      manualStage("Phone"),
      integrationStage("Email", TransmissionMethod.EMAIL),
      integrationStage("Email", TransmissionMethod.FTP));

    var errors = validator.validate(normalizer.normalize(claimingSequence));

    assertTrue(errors.getErrors().isEmpty());
    assertEquals(0, errors.getTotalRecords());
  }

  @Test
  void testDuplicateStageNamesAreAllowed() {
    var claimingSequence = sequence("Serials",
      integrationStage("Email reminder", TransmissionMethod.EMAIL),
      integrationStage("Email reminder", TransmissionMethod.EMAIL));

    assertTrue(validator.validate(normalizer.normalize(claimingSequence)).getErrors().isEmpty());
  }

  @Test
  void testBlankNamesAreRejected() {
    var claimingSequence = sequence("   ", manualStage("  "));

    var errors = validator.validate(normalizer.normalize(claimingSequence)).getErrors();

    assertEquals(List.of("claimingSequenceNameRequired", "claimingSequenceStageNameRequired"), codes(errors));
  }

  @Test
  void testTransmissionMethodMustMatchDeliveryType() {
    var claimingSequence = sequence("Serials",
      integrationStage("Email", null),
      manualStage("Phone").withTransmissionMethod(TransmissionMethod.FTP));

    var errors = validator.validate(normalizer.normalize(claimingSequence)).getErrors();

    assertEquals(List.of("transmissionMethodRequired", "transmissionMethodNotAllowed"), codes(errors));
    assertEquals(Map.of("stage", "1", "stageName", "Email"), parameters(errors.get(0)));
    assertEquals(Map.of("stage", "2", "stageName", "Phone"), parameters(errors.get(1)));
  }

  private static ClaimingSequence sequence(String name, ClaimingSequenceStage... stages) {
    return new ClaimingSequence().withName(name).withStages(new ArrayList<>(List.of(stages)));
  }

  private static ClaimingSequenceStage manualStage(String name) {
    return new ClaimingSequenceStage().withName(name).withDeliveryType(DeliveryType.MANUAL).withInterval(14);
  }

  private static ClaimingSequenceStage integrationStage(String name, TransmissionMethod transmissionMethod) {
    return new ClaimingSequenceStage().withName(name).withDeliveryType(DeliveryType.INTEGRATION)
      .withTransmissionMethod(transmissionMethod).withInterval(21);
  }

  private static List<String> codes(List<Error> errors) {
    return errors.stream().map(Error::getCode).toList();
  }

  private static Map<String, String> parameters(Error error) {
    return error.getParameters().stream().collect(Collectors.toMap(Parameter::getKey, Parameter::getValue));
  }

}
