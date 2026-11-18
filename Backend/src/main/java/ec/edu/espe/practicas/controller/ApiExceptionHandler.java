package ec.edu.espe.practicas.controller;

import ec.edu.espe.practicas.service.exception.BusinessRuleViolationException;
import ec.edu.espe.practicas.service.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({org.springframework.core.task.TaskRejectedException.class,
            org.springframework.web.context.request.async.AsyncRequestTimeoutException.class})
    ProblemDetail handleReportBusy(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Consulta no disponible",
                "No se pudo completar la consulta. Intenta nuevamente o reduce el rango de fechas.", request);
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "Recurso no encontrado", exception.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ProblemDetail handleBusinessRule(BusinessRuleViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regla de negocio no cumplida",
                exception.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "Acceso denegado", exception.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Autenticación fallida",
                "Correo o contraseña incorrectos.", request);
    }

    @ExceptionHandler(org.springframework.security.authentication.CredentialsExpiredException.class)
    ProblemDetail handleExpiredSession(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.UNAUTHORIZED, "Sesión terminada", "La sesión terminó. Ingresa nuevamente.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "Conflicto de datos",
                "La operación entra en conflicto con un registro existente o una restricción de la base de datos.",
                request);
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleUnexpectedState(IllegalStateException exception, HttpServletRequest request) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo generar el archivo solicitado",
                exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct()
                .collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Solicitud inválida", detail, request);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    ProblemDetail handleParameterValidation(
            org.springframework.web.method.annotation.HandlerMethodValidationException exception,
            HttpServletRequest request) {
        String detail = exception.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .distinct().collect(Collectors.joining("; "));
        return problem(HttpStatus.BAD_REQUEST, "Datos inválidos", detail, request);
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ProblemDetail handleInvalidInput(Exception exception, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "Datos inválidos",
                "Revisa los datos ingresados y selecciona las opciones disponibles.", request);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
