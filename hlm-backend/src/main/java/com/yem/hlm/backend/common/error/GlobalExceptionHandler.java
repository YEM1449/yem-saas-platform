package com.yem.hlm.backend.common.error;

import com.yem.hlm.backend.auth.service.AccountLockedException;
import com.yem.hlm.backend.auth.service.LoginRateLimitedException;
import com.yem.hlm.backend.auth.service.UnauthorizedException;
import com.yem.hlm.backend.common.ratelimit.RateLimitExceededException;
import com.yem.hlm.backend.contact.service.*;
import com.yem.hlm.backend.immeuble.service.ImmeubleNotFoundException;
import com.yem.hlm.backend.societe.CrossSocieteAccessException;
import com.yem.hlm.backend.contract.service.ContractDepositMismatchException;
import com.yem.hlm.backend.contract.service.ContractNotFoundException;
import com.yem.hlm.backend.contract.service.InvalidContractStateException;
import com.yem.hlm.backend.contract.service.PropertyAlreadySoldException;
import com.yem.hlm.backend.deposit.service.*;

import com.yem.hlm.backend.notification.service.NotificationNotFoundException;
import com.yem.hlm.backend.media.service.MediaNotFoundException;
import com.yem.hlm.backend.media.service.MediaTooLargeException;
import com.yem.hlm.backend.media.service.MediaTypeNotAllowedException;
import com.yem.hlm.backend.outbox.service.ContactChannelMissingException;
import com.yem.hlm.backend.outbox.service.InvalidRecipientException;
import com.yem.hlm.backend.user.service.UserEmailAlreadyExistsException;
import com.yem.hlm.backend.user.service.UserNotFoundException;
import com.yem.hlm.backend.project.service.ArchivedProjectAssignmentException;
import com.yem.hlm.backend.project.service.ProjectNameAlreadyExistsException;
import com.yem.hlm.backend.project.service.ProjectNotFoundException;
import com.yem.hlm.backend.property.service.InvalidPeriodException;
import com.yem.hlm.backend.property.service.InvalidPropertyTypeException;
import com.yem.hlm.backend.property.service.ImmeubleProjectMismatchException;
import com.yem.hlm.backend.property.service.PropertyNotFoundException;
import com.yem.hlm.backend.property.service.PropertyReferenceCodeExistsException;
import com.yem.hlm.backend.commission.service.CommissionRuleNotFoundException;
import com.yem.hlm.backend.task.service.TaskNotFoundException;
import com.yem.hlm.backend.document.service.DocumentNotFoundException;
import com.yem.hlm.backend.portal.service.PortalTokenInvalidException;
import com.yem.hlm.backend.payments.service.InvalidPaymentScheduleStateException;
import com.yem.hlm.backend.payments.service.PaymentInvalidAmountException;
import com.yem.hlm.backend.payments.service.PaymentScheduleItemNotFoundException;
import com.yem.hlm.backend.reservation.service.InvalidReservationStateException;
import com.yem.hlm.backend.reservation.service.PropertyNotAvailableForReservationException;
import com.yem.hlm.backend.reservation.service.ReservationNotFoundException;
import com.yem.hlm.backend.gdpr.service.GdprErasureBlockedException;
import com.yem.hlm.backend.gdpr.service.GdprExportNotFoundException;
import com.yem.hlm.backend.usermanagement.exception.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Global exception handler for all REST controllers.
 * Produces standardized ErrorResponse for all error scenarios.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle bean validation errors (@Valid on request bodies).
     * Returns 400 with field-level error details.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<com.yem.hlm.backend.common.error.FieldError> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new com.yem.hlm.backend.common.error.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage()
                ))
                .toList();

        ErrorResponse error = ErrorResponse.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.VALIDATION_ERROR,
                "Validation failed for request",
                request.getRequestURI(),
                fieldErrors
        );

        log.warn("Validation error on {}: {} field(s) invalid",
                request.getRequestURI(), fieldErrors.size());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle malformed JSON or invalid enum values in request bodies.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.VALIDATION_ERROR,
                "Malformed request body",
                request.getRequestURI()
        );

        log.warn("Unreadable request on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handle constraint violations (e.g., @Valid on method parameters).
     * Returns 400 with field-level error details.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<com.yem.hlm.backend.common.error.FieldError> fieldErrors = ex.getConstraintViolations()
                .stream()
                .map(cv -> new com.yem.hlm.backend.common.error.FieldError(
                        cv.getPropertyPath().toString(),
                        cv.getMessage()
                ))
                .toList();

        ErrorResponse error = ErrorResponse.withFieldErrors(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.VALIDATION_ERROR,
                "Constraint violation",
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== 404 Not Found ==========

    @ExceptionHandler({
            CommissionRuleNotFoundException.class,
            ContactNotFoundException.class,
            TaskNotFoundException.class,
            DocumentNotFoundException.class,
            ContactInterestNotFoundException.class,
            ContractNotFoundException.class,
            DepositNotFoundException.class,
            ImmeubleNotFoundException.class,
            MediaNotFoundException.class,
            NotificationNotFoundException.class,
            PaymentScheduleItemNotFoundException.class,
            ProjectNotFoundException.class,
            PropertyNotFoundException.class,
            ReservationNotFoundException.class,
            UserNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFound(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.getReasonPhrase(),
                ErrorCode.NOT_FOUND,
                ex.getMessage(),
                request.getRequestURI()
        );

        log.warn("Resource not found: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ========== 409 Conflict ==========

    @ExceptionHandler(ContactEmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleContactEmailExists(
            ContactEmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.CONTACT_EMAIL_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(UserEmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserEmailExists(
            UserEmailAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.USER_EMAIL_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ContactInterestAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleContactInterestExists(
            ContactInterestAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.CONTACT_INTEREST_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(DepositAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleDepositAlreadyExists(
            DepositAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.DEPOSIT_ALREADY_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PropertyAlreadyReservedException.class)
    public ResponseEntity<ErrorResponse> handlePropertyAlreadyReserved(
            PropertyAlreadyReservedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.PROPERTY_ALREADY_RESERVED,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(PropertyNotAvailableForReservationException.class)
    public ResponseEntity<ErrorResponse> handlePropertyNotAvailableForReservation(
            PropertyNotAvailableForReservationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.PROPERTY_NOT_AVAILABLE_FOR_RESERVATION,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReservationState(
            InvalidReservationStateException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.INVALID_RESERVATION_STATE,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidDepositStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDepositState(
            InvalidDepositStateException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.INVALID_DEPOSIT_STATE,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidStatusTransition(
            InvalidStatusTransitionException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.INVALID_STATUS_TRANSITION,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ========== 400 Bad Request ==========

    @ExceptionHandler(InvalidDepositRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDepositRequest(
            InvalidDepositRequestException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_DEPOSIT_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== 401 Unauthorized ==========

    @ExceptionHandler(PortalTokenInvalidException.class)
    public ResponseEntity<ErrorResponse> handlePortalTokenInvalid(
            PortalTokenInvalidException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorCode.PORTAL_TOKEN_INVALID,
                ex.getMessage(),
                request.getRequestURI()
        );
        log.warn("Portal token invalid on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorCode.ACCOUNT_LOCKED,
                "Account is locked until " + ex.getLockedUntil().toString(),
                request.getRequestURI()
        );
        log.warn("Account locked attempt on {}: locked until {}", request.getRequestURI(), ex.getLockedUntil());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                ErrorCode.UNAUTHORIZED,
                ex.getMessage(),
                request.getRequestURI()
        );

        log.warn("Authentication failed on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ========== 403 Forbidden ==========

    @ExceptionHandler(CrossSocieteAccessException.class)
    public ResponseEntity<ErrorResponse> handleCrossSocieteAccess(
            CrossSocieteAccessException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ErrorCode.FORBIDDEN,
                "Access denied to resource outside your société",
                request.getRequestURI()
        );

        log.warn("Cross-société access attempt: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ========== Property Errors ==========

    @ExceptionHandler(InvalidPropertyTypeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPropertyType(
            InvalidPropertyTypeException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_PROPERTY_TYPE,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(PropertyReferenceCodeExistsException.class)
    public ResponseEntity<ErrorResponse> handlePropertyReferenceCodeExists(
            PropertyReferenceCodeExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.PROPERTY_REFERENCE_CODE_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ProjectNameAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleProjectNameExists(
            ProjectNameAlreadyExistsException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.PROJECT_NAME_EXISTS,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ArchivedProjectAssignmentException.class)
    public ResponseEntity<ErrorResponse> handleArchivedProject(
            ArchivedProjectAssignmentException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.ARCHIVED_PROJECT,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(InvalidPeriodException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPeriod(
            InvalidPeriodException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_PERIOD,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ImmeubleProjectMismatchException.class)
    public ResponseEntity<ErrorResponse> handleImmeubleProjectMismatch(
            ImmeubleProjectMismatchException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_REQUEST,
                ex.getMessage(),
                request.getRequestURI()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== 403 Forbidden ==========

    /**
     * Handler for Spring Security authorization failures.
     * Returns 403 when user is authenticated but lacks required role/permission.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            Exception ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                ErrorCode.FORBIDDEN,
                "You don't have permission to access this resource",
                request.getRequestURI()
        );

        log.warn("Access denied on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ========== Contract Errors ==========

    @ExceptionHandler(PropertyAlreadySoldException.class)
    public ResponseEntity<ErrorResponse> handlePropertyAlreadySold(
            PropertyAlreadySoldException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.PROPERTY_ALREADY_SOLD,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidContractStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidContractState(
            InvalidContractStateException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.INVALID_CONTRACT_STATE,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(ContractDepositMismatchException.class)
    public ResponseEntity<ErrorResponse> handleContractDepositMismatch(
            ContractDepositMismatchException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.CONTRACT_DEPOSIT_MISMATCH,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== Media Errors ==========

    @ExceptionHandler(MediaTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleMediaTooLarge(
            MediaTooLargeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.MEDIA_TOO_LARGE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MediaTypeNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAllowed(
            MediaTypeNotAllowedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.MEDIA_TYPE_NOT_ALLOWED, ex.getMessage(), request.getRequestURI()));
    }

    // ========== Payment Errors ==========

    @ExceptionHandler(InvalidPaymentScheduleStateException.class)
    public ResponseEntity<ErrorResponse> handlePaymentConflict(
            RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.INVALID_PAYMENT_SCHEDULE_STATE, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(PaymentInvalidAmountException.class)
    public ResponseEntity<ErrorResponse> handlePaymentBadRequest(
            RuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.PAYMENT_INVALID_AMOUNT, ex.getMessage(), request.getRequestURI()));
    }

    // ========== GDPR Errors ==========

    @ExceptionHandler(GdprExportNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleGdprExportNotFound(
            GdprExportNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(),
                ErrorCode.GDPR_EXPORT_NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(GdprErasureBlockedException.class)
    public ResponseEntity<ErrorResponse> handleGdprErasureBlocked(
            GdprErasureBlockedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(
                HttpStatus.CONFLICT.value(), HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.GDPR_ERASURE_BLOCKED, ex.getMessage(), request.getRequestURI()));
    }

    // ========== Outbox / Messaging Errors ==========

    @ExceptionHandler(InvalidRecipientException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRecipient(
            InvalidRecipientException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.INVALID_RECIPIENT,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ContactChannelMissingException.class)
    public ResponseEntity<ErrorResponse> handleContactChannelMissing(
            ContactChannelMissingException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ErrorCode.CONTACT_CHANNEL_MISSING,
                ex.getMessage(),
                request.getRequestURI()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    // ========== 429 Too Many Requests ==========

    @ExceptionHandler(LoginRateLimitedException.class)
    public ResponseEntity<ErrorResponse> handleLoginRateLimited(
            LoginRateLimitedException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                429,
                "Too Many Requests",
                ErrorCode.LOGIN_RATE_LIMITED,
                ex.getMessage(),
                request.getRequestURI()
        );
        log.warn("Login rate limit exceeded on {}: retryAfter={}s", request.getRequestURI(), ex.getRetryAfterSeconds());
        return ResponseEntity.status(429)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .header("X-RateLimit-Remaining", "0")
                .body(error);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                429,
                "Too Many Requests",
                ErrorCode.RATE_LIMIT_EXCEEDED,
                ex.getMessage(),
                request.getRequestURI()
        );
        log.warn("Rate limit exceeded on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(429).body(error);
    }

    // ========== ResponseStatusException (pass-through) ==========

    /**
     * Handle ResponseStatusException thrown by controllers/services.
     * Maps the embedded status code to the appropriate ErrorCode.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ErrorCode code = switch (status) {
            case FORBIDDEN -> ErrorCode.FORBIDDEN;
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;
            default -> ErrorCode.INTERNAL_ERROR;
        };

        ErrorResponse error = ErrorResponse.of(
                status.value(),
                status.getReasonPhrase(),
                code,
                ex.getReason() != null ? ex.getReason() : status.getReasonPhrase(),
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(error);
    }

    // ========== 409 Concurrent Update ==========

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ErrorCode.CONCURRENT_UPDATE,
                "The resource was modified by another request. Please retry.",
                request.getRequestURI()
        );
        log.warn("Optimistic lock failure on {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ========== User Management Business Rule Errors ==========

    /**
     * Handles BusinessRuleException from the user management module.
     * Maps ErrorCode to the appropriate HTTP status.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case INVITATION_EXPIREE -> HttpStatus.GONE;
            case INVITATION_EN_COURS, DERNIER_ADMIN, QUOTA_UTILISATEURS_ATTEINT,
                 MEMBRE_DEJA_EXISTANT, CONCURRENT_UPDATE, COMPTE_DEJA_DEBLOQUE,
                 SOCIETE_ALREADY_EXISTS, QUOTA_BIENS_ATTEINT,
                 QUOTA_CONTACTS_ATTEINT, QUOTA_PROJETS_ATTEINT -> HttpStatus.CONFLICT;
            case SOCIETE_SUSPENDED, ROLE_ESCALATION_FORBIDDEN, INSUFFICIENT_ROLE -> HttpStatus.FORBIDDEN;
            case MEMBRE_NON_TROUVE -> HttpStatus.NOT_FOUND;
            case MOT_DE_PASSE_TROP_COURT, MOT_DE_PASSE_TROP_FAIBLE,
                 MOT_DE_PASSE_CONTIENT_EMAIL, INVALID_REQUEST, ROLE_INVALIDE,
                 CONSENT_REQUIRED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        ErrorResponse error = ErrorResponse.of(
                status.value(), status.getReasonPhrase(),
                ex.getErrorCode(), ex.getMessage(), request.getRequestURI()
        );
        log.warn("Business rule violation on {}: {} — {}", request.getRequestURI(), ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(status).body(error);
    }

    // ========== 500 Internal Server Error ==========

    @ExceptionHandler(ReservationPdfGenerationException.class)
    public ResponseEntity<ErrorResponse> handleReservationPdfGeneration(
            ReservationPdfGenerationException ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ErrorCode.INTERNAL_ERROR,
                ex.getMessage(),
                request.getRequestURI()
        );
        log.error("PDF generation failed on {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    /**
     * Catch-all handler for unexpected exceptions.
     * Logs full stack trace but returns safe message to client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request
    ) {
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred",
                request.getRequestURI()
        );

        log.error("Unexpected error on {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
