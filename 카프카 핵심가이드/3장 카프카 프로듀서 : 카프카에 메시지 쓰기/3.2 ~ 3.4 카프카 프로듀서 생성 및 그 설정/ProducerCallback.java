package com.producer.demo;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ProducerCallback implements Callback {
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    /**
     * 콜백은 프로듀서의 메인 스레드에서 실행된다.
     * 만약 우리가 두 개의 메시지를 동일한 파티션에 전송한다면, 콜백 역시 우리가 보낸 순서대로 실행된다.
     * 하디만 이는 뒤집어 생각하면, 전송되어야 할 메시지가 전송이 안되고 프로듀서가 지연되는 상황을 막기 위해서는 콜백이 충분히 빨라야 한다는 의미이기도 한다
     * 콜백 안에서 블로킹 작업을 수행하는 것 역시 권장되지 않는다.
     * 대신, 블로킹 작업을 ㄷㅇ시에 수행하는 다른 스레드를 사용해야 한다.
     */
    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
        if (e != null) {
            e.printStackTrace();
        } else {
            executorService.submit(() -> processSuccess(recordMetadata));
        }
    }

    private void processSuccess(RecordMetadata metadata) {
        System.out.println("Message sent successfully to topic: " + metadata.topic() +
                ", partition: " + metadata.partition() +
                ", offset: " + metadata.offset());

        // 블로킹 작업 (예: DB 저장, 로그 파일 작성 등)
        saveToDatabase(metadata);
    }

    private void saveToDatabase(RecordMetadata metadata) {
        try {
            // 예시: 데이터베이스에 오프셋 기록
            Thread.sleep(1000); // 블로킹 작업 시뮬레이션
            System.out.println("Metadata saved to DB for offset: " + metadata.offset());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            System.err.println("Failed to save metadata to DB");
        }
    }

}
