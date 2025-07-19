package com.kafka.alessandro_alessandra.routes;

import com.kafka.alessandro_alessandra.kafka.KafkaProducer;
import com.kafka.alessandro_alessandra.model.ErrorResponse;
import com.kafka.alessandro_alessandra.model.MessageRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mail")
@Validated
@Slf4j
public class KafkaRoutes {

    private final KafkaProducer kafkaProducer;

    @Autowired
    public KafkaRoutes(KafkaProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    /**
     * Sends a formatted message to a Kafka topic using the provided email as a path variable.
     *
     * @param email          the recipient's email address extracted from the path variable
     * @param messageRequest the message payload to be sent, validated via @Valid
     * @return ResponseEntity containing the result of the operation (success or error)
     */
    @PostMapping("/{mail}")
    public ResponseEntity<Object> sendFormattedMessage(
            @PathVariable("mail") String email,
            @RequestBody @Valid MessageRequest messageRequest) {
        try {
            kafkaProducer.sendMail(email, messageRequest);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Message sent to Kafka");
            response.put("email", email);
            response.put("data", messageRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            ErrorResponse errorResponse = new ErrorResponse("error", "Failed to send message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Handles exceptions thrown during request processing, such as validation errors.
     *
     * @param ex the exception that was thrown
     * @return ResponseEntity containing an ErrorResponse with error details and HTTP 400 status
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(Exception ex) {
        ErrorResponse errorResponse = new ErrorResponse("error", "Validation failed", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
}
