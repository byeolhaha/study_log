package com.producer.demo;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 코드를 고치지 않으면서 그 작동을 변경해야 하는 경우
 * 모든 애플리케이션 동작에 동일한 작도을 넣거나 원래 코드를 사용할 수 없는 상황
 * 일반적인 사용 사례 : 모니터링, 정보 추적, 표쥰 헤더 삽입
 */
public class CountingProducerInterceptor implements ProducerInterceptor {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    static AtomicLong numSent = new AtomicLong();
    static AtomicLong numAcked = new AtomicLong();

    /**
     * 다른 메서드가 호출되기 전에 설정이 가능
     */
    @Override
    public void configure(Map<String, ?> map) {
        Long windowSize = Long.valueOf((String) map.get("counting.interceptor.window.size.ms"));
        executor.scheduleAtFixedRate(CountingProducerInterceptor::run, windowSize, windowSize, TimeUnit.MILLISECONDS);
    }
    /**
     * 프로듀서가 레코드를 브로커에 보내기 전 직렬화되기 직전에 호출
     * 재정의할 때는 보내질 레코드에 담긴 정보를 볼 수 있고 이를 고칠 수도 있다.
     */
    @Override
    public ProducerRecord onSend(ProducerRecord producerRecord) {
        numSent.incrementAndGet();
        return producerRecord;
    }

    /**
     * 카프카 브로커가 응답을 클라이언트가 받았을 때 호출, 응답을 변경할 수는 없지만 그 안에 담긴 정보는 읽을 수 있다.
     */
    @Override
    public void onAcknowledgement(RecordMetadata recordMetadata, Exception e) {
        numAcked.incrementAndGet();
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    public static void run() {
        System.out.println(numSent.getAndSet(0));
        System.out.println(numAcked.getAndSet(0));
    }
}
