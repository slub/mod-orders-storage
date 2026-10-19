package org.folio.rest.exceptions;

import org.folio.rest.jaxrs.model.Error;

public enum ErrorCodes {

  GENERIC_ERROR_CODE("genericError", "Generic error"),
  POSTGRE_SQL_ERROR("pgException", "PostgreSQL exception"),
  TITLE_EXIST("titleExist", "The title for poLine already exist"),
  ORDER_TEMPLATE_CATEGORY_IS_USED("orderTemplateCategoryIsUsed", "The order template category is used in some order template and cannot be deleted"),
  RESOURCE_NOT_FOUND("resourceNotFound", "Resource not found"),
  UNIQUE_FIELD_CONSTRAINT_ERROR("uniqueField{0}{1}Error", "Field {0} must be unique"),
  CLAIMING_SEQUENCE_NAME_REQUIRED("claimingSequenceNameRequired", "Claiming sequence name must not be blank"),
  CLAIMING_SEQUENCE_STAGE_NAME_REQUIRED("claimingSequenceStageNameRequired", "Claiming sequence stage name must not be blank"),
  TRANSMISSION_METHOD_REQUIRED("transmissionMethodRequired", "Transmission method is required for a stage with delivery type INTEGRATION"),
  TRANSMISSION_METHOD_NOT_ALLOWED("transmissionMethodNotAllowed", "Transmission method is not allowed for a stage with delivery type MANUAL");

  private final String code;
  private final String description;

  ErrorCodes(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public String getCode() {
    return code;
  }

  @Override
  public String toString() {
    return code + ": " + description;
  }

  public Error toError() {
    return new Error().withCode(code).withMessage(description);
  }
}
