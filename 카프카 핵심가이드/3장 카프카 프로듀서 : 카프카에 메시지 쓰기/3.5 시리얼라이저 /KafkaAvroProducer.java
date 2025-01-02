package com.producer.demo;


import org.springframework.kafka.annotation.EnableKafka;

import org.apache.avro.generic.GenericRecord;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.Schema.Parser;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@EnableKafka
public class KafkaAvroProducer {

    private final KafkaTemplate<String, GenericRecord> kafkaTemplate;

    public KafkaAvroProducer(KafkaTemplate<String, GenericRecord> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendAvroMessage() {
        try {
            // Avro 스키마를 로드
            ClassPathResource resource = new ClassPathResource("customer.avsc");
            String schemaStr = new String(Files.readAllBytes(resource.getFile().toPath()), StandardCharsets.UTF_8);
            Schema schema = new Parser().parse(schemaStr);

            // GenericRecord 생성
            GenericRecord record = new GenericData.Record(schema);
            record.put("customerID", 1);
            record.put("customerName", "byeol");


            // Kafka 메시지에 헤더 추가
            Message<GenericRecord> message = MessageBuilder
                    .withPayload(record)
                    .setHeader(KafkaHeaders.TOPIC, "my-avro")  // 토픽 이름 설정
                    .setHeader("customHeader", "customValue") // 사용자 정의 헤더 추가
                    // header key는 String이고 밸류값은 아무 직렬화된 객체라도 상관없다.
                    .build();

            // Kafka로 Avro 메시지 전송
            kafkaTemplate.send(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
