package com.producer.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;
import java.util.concurrent.Future;

/**
 * 1. 파이어 앤 포켓
 *    -> 메세지를 서버에 전송하고 결과를 신경쓰지 않음
 * 2. 동기적 전송
 *    -> 카프카 프로듀서는 언제나 비동기적으로 작동, 메시지 보내는 send() 메서드는 Future 객체를 리턴한다. 하지만 메시지를 다음 전송하기 전 get() 메서드를 호출해서 작업 완료 전까지 기다림
 * 3. 비동기 전송
 *    -> 콜백 함수와 함께 send() 메서드를 호출하면 카프카로부터 응답을 받는 시점에서 자동으로 콜백함수가 호출됨
 */
@Configuration
public class DirectKafkaProducer {

    private final KafkaProducer<String, String> producer;
    private final ProducerCallback producerCallback;
    private final String topic = "test-topic";

    public DirectKafkaProducer(ProducerCallback producerCallback) {
        this.producerCallback = producerCallback;
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:10004");
        props.put("key.serializer", StringSerializer.class.getName());
        props.put("value.serializer", StringSerializer.class.getName());

        props.put("client.id", "producer-client-1");// 클라이언트 식별 이름
        props.put("acks", "all");

        this.producer = new KafkaProducer<>(props);
    }

    public void send(String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        Future<RecordMetadata> future = producer.send(record, (metadata, exception) -> {
            if (exception == null) {
                System.out.println("Sent record to " + metadata.topic() +
                        " partition " + metadata.partition() +
                        " at offset " + metadata.offset());
            } else {
                exception.printStackTrace();
            }
        });
    }

    public void sendSync(String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        try {
            producer.send(record).get();
            //재시도 가능한 예외 : 연결이나 리더가 아닌 브로커에 간 경우
            // 재시도가 불가능한 에러 : 메시지가 큰 경우 -> 재시도 없이 그냥 예욀르 뱉는다.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendASyncWithCallback(String key, String value) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        try {
            producer.send(record, producerCallback);
            //재시도 가능한 예외 : 연결이나 리더가 아닌 브로커에 간 경우
            // 재시도가 불가능한 에러 : 메시지가 큰 경우 -> 재시도 없이 그냥 예욀르 뱉는다.
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

