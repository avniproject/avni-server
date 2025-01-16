package org.avni.server.common;

public class ValidationResult {
    private String message;
    private ValidationResultType validationResultType;

    public static ValidationResult Success = new ValidationResult(ValidationResultType.Success);

    public static ValidationResult Failure(String message) {
        return new ValidationResult(ValidationResultType.Failure, message);
    }

    private ValidationResult(ValidationResultType validationResultType) {
        this.validationResultType = validationResultType;
    }

    public ValidationResult(ValidationResultType validationResultType, String message) {
        this.message = message;
        this.validationResultType = validationResultType;
    }

    public String getMessage() {
        return message;
    }

    public boolean isFailure() {
        return hasType(ValidationResultType.Failure);
    }

    public boolean isSuccess() {
        return hasType(ValidationResultType.Success);
    }

    public boolean hasType(ValidationResultType validationResultType) {
        return this.validationResultType == validationResultType;
    }

    private enum ValidationResultType {
        Failure, Warning, Success
    }
}
