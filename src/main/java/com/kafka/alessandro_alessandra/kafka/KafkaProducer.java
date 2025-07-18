package com.kafka.alessandro_alessandra.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.alessandro_alessandra.model.MessageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class KafkaProducer {

    @Value("${app.kafka.topic.mail-service}")
    private String mailServiceTopic;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void sendMail(String email, MessageRequest messageRequest) throws JsonProcessingException {
        String messageRequestJson = objectMapper.writeValueAsString(messageRequest);

        String message = String.format("EMAIL:%s|DATA:%s", email, messageRequestJson);
        
        log.info("Producing formatted message to topic {}: {}", mailServiceTopic, message);
        this.kafkaTemplate.send(mailServiceTopic, message);
    }
}