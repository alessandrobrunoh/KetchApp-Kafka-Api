package com.kafka.alessandro_alessandra.routes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.kafka.alessandro_alessandra.kafka.KafkaProducer;
import com.kafka.alessandro_alessandra.model.ErrorResponse;
import com.kafka.alessandro_alessandra.model.MessageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @Operation(
        summary = "Send a formatted message to a specified email address",
        description = "This endpoint sends a formatted study session summary email to the specified recipient. " +
        "The email will include a detailed breakdown of Pomodoro sessions, study and pause times, and statistics. " +
        "The request body must contain a valid MessageRequest object with all required session and subject data. " +
        "Returns a success response with the email and data sent, or an error response if the operation fails."
    )
    @ApiResponses(
        value = {
            @ApiResponse(
                responseCode = "200",
                description = "Message sent successfully",
                content = @Content(mediaType = "application/json")
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Bad Request - Validation failed",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)
                )
            ),
            @ApiResponse(
                responseCode = "500",
                description = "Internal Server Error - Failed to send message",
                content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ErrorResponse.class)
                )
            ),
        }
    )
    @PostMapping("/{mail}")
    public ResponseEntity<Object> sendFormattedMessage(
        @PathVariable("mail") String email,
        @RequestBody @Valid MessageRequest messageRequest
    ) throws JsonProcessingException {
        kafkaProducer.sendMail(email, messageRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message sent to Kafka");
        response.put("email", email);
        response.put("data", messageRequest);
        return ResponseEntity.ok(response);
    }
}
