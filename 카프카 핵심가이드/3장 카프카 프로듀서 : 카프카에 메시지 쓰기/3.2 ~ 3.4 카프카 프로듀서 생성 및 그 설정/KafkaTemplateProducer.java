package com.producer.demo;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.util.concurrent.ExecutionException;



@Service
public class KafkaTemplateProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic = "test-topic";

    public KafkaTemplateProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // 비동기 전송 (콜백 포함)
    public void sendAsyncWithCallback(String key, String value) {
        ListenableFuture<SendResult<String, String>> future = kafkaTemplate.send(topic, key, value);

        future.addCallback(new ListenableFutureCallback<SendResult<String, String>>() {
            @Override
            public void onSuccess(SendResult<String, String> result) {
                System.out.println("Sent record to " + result.getRecordMetadata().topic() +
                        " partition " + result.getRecordMetadata().partition() +
                        " at offset " + result.getRecordMetadata().offset());
            }

            @Override
            public void onFailure(Throwable ex) {
                System.err.println("Failed to send message: " + ex.getMessage());
            }
        });
    }

    // 동기 전송
    public void sendSync(String key, String value) {
        try {
            SendResult<String, String> result = kafkaTemplate.send(topic, key, value).get();
            System.out.println("Sent record to " + result.getRecordMetadata().topic() +
                    " partition " + result.getRecordMetadata().partition() +
                    " at offset " + result.getRecordMetadata().offset());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

}
