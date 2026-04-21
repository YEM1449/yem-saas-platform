# Module 08: Error Handling And Validation

## Why This Matters

A platform is easier to use and test when failures are consistent and meaningful.

## Learning Goals

- understand where validation happens
- understand the standardized error contract
- identify business exceptions versus transport failures

## Main Mechanism

`GlobalExceptionHandler` converts exceptions into a stable API error shape.

Common categories:

- validation errors
- malformed body errors
- not found
- conflict and invalid transition errors
- forbidden and unauthorized outcomes

## Files To Study

- [../../hlm-backend/src/main/java/com/yem/hlm/backend/common/error/GlobalExceptionHandler.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/common/error/GlobalExceptionHandler.java)
- [../../hlm-backend/src/main/java/com/yem/hlm/backend/common/error/ErrorCode.java](../../hlm-backend/src/main/java/com/yem/hlm/backend/common/error/ErrorCode.java)
- [../spec/api-reference.md](../spec/api-reference.md)

## Exercise

Pick one invalid business transition, such as a forbidden vente state change, and trace how it becomes an HTTP error response.
