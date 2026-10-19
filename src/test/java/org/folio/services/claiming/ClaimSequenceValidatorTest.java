package org.folio.services.claiming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.rest.jaxrs.model.ClaimSequence;
import org.folio.rest.jaxrs.model.ClaimSequenceStage;
import org.folio.rest.jaxrs.model.ClaimSequenceStage.DeliveryType;
import org.folio.rest.jaxrs.model.ClaimSequenceStage.TransmissionMethod;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.junit.jupiter.api.Test;

public class ClaimSequenceValidatorTest {

  private final ClaimSequenceNormalizer normalizer = new ClaimSequenceNormalizer();
  private final ClaimSequenceValidator validator = new ClaimSequenceValidator();

  @Test
  void testNormalizeAssignsStageNumbersFromListPosition() {
    var claimSequence = sequence("Serials",
      manualStage("Phone").withStage(5),
      integrationStage("Email", TransmissionMethod.EMAIL).withStage(2),
      integrationStage("Email", TransmissionMethod.EMAIL).withStage(2));

    normalizer.normalize(claimSequence);

    assertEquals(List.of(1, 2, 3), claimSequence.getStages().stream().map(ClaimSequenceStage::getStage).toList());
  }

  @Test
  void testNormalizeTrimsNamesAndDropsBlankNotes() {
    var claimSequence = sequence("  Serials  ",
      manualStage("  Phone  ").withInternalNote("   ").withExternalNote(" Please call back "));

    normalizer.normalize(claimSequence);

    var stage = claimSequence.getStages().getFirst();
    assertEquals("Serials", claimSequence.getName());
    assertEquals("Phone", stage.getName());
    assertNull(stage.getInternalNote());
    assertEquals(" Please call back ", stage.getExternalNote());
  }

  @Test
  void testValidSequenceHasNoErrors() {
    var claimSequence = sequence("Serials",
      manualStage("Phone"),
      integrationStage("Email", TransmissionMethod.EMAIL),
      integrationStage("Email", TransmissionMethod.FTP));

    var errors = validator.validate(normalizer.normalize(claimSequence));

    assertTrue(errors.getErrors().isEmpty());
    assertEquals(0, errors.getTotalRecords());
  }

  @Test
  void testDuplicateStageNamesAreAllowed() {
    var claimSequence = sequence("Serials",
      integrationStage("Email reminder", TransmissionMethod.EMAIL),
      integrationStage("Email reminder", TransmissionMethod.EMAIL));

    assertTrue(validator.validate(normalizer.normalize(claimSequence)).getErrors().isEmpty());
  }

  @Test
  void testBlankNamesAreRejected() {
    var claimSequence = sequence("   ", manualStage("  "));

    var errors = validator.validate(normalizer.normalize(claimSequence)).getErrors();

    assertEquals(List.of("claimSequenceNameRequired", "claimSequenceStageNameRequired"), codes(errors));
  }

  @Test
  void testTransmissionMethodMustMatchDeliveryType() {
    var claimSequence = sequence("Serials",
      integrationStage("Email", null),
      manualStage("Phone").withTransmissionMethod(TransmissionMethod.FTP));

    var errors = validator.validate(normalizer.normalize(claimSequence)).getErrors();

    assertEquals(List.of("transmissionMethodRequired", "transmissionMethodNotAllowed"), codes(errors));
    assertEquals(Map.of("stage", "1", "stageName", "Email"), parameters(errors.get(0)));
    assertEquals(Map.of("stage", "2", "stageName", "Phone"), parameters(errors.get(1)));
  }

  private static ClaimSequence sequence(String name, ClaimSequenceStage... stages) {
    return new ClaimSequence().withName(name).withStages(new ArrayList<>(List.of(stages)));
  }

  private static ClaimSequenceStage manualStage(String name) {
    return new ClaimSequenceStage().withName(name).withDeliveryType(DeliveryType.MANUAL).withInterval(14);
  }

  private static ClaimSequenceStage integrationStage(String name, TransmissionMethod transmissionMethod) {
    return new ClaimSequenceStage().withName(name).withDeliveryType(DeliveryType.INTEGRATION)
      .withTransmissionMethod(transmissionMethod).withInterval(21);
  }

  private static List<String> codes(List<Error> errors) {
    return errors.stream().map(Error::getCode).toList();
  }

  private static Map<String, String> parameters(Error error) {
    return error.getParameters().stream().collect(Collectors.toMap(Parameter::getKey, Parameter::getValue));
  }

}
