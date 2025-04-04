package com.producer.demo;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers = "localhost:10002";

    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Avro 직렬화를 위한 설정
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, io.confluent.kafka.serializers.KafkaAvroSerializer.class);
        // Schema Registry 설정
        props.put("schema.registry.url", "http://localhost:8081");  // Schema Registry URL

        //props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        //props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        props.put(ProducerConfig.CLIENT_ID_CONFIG, "template-producer-client-1");  // client.id 설정

        // 0일 때 높은 처리량 하지만 리더에 쓰였는지도 점검할 수 없음
        // 1일 때 리더 레플리카가 메시지를 받는 순간 브로커로부터 성공했다는 응답을 받음 -> 리더가 못 받으면 재전송/ 하지만 리더가 크래시가 난 상태에서 해당 메시지가 복제가 안된다면 유실
        // all 일 때는 in-sync-replica에 전달된 뒤에야 브로커로부터 성공했다는 응답을 받음 따라서 해당 설정값과 반드시 함께 쓰임  최소 2개 이상이 메시지를 가지고 있어야 하며 따라서 크래시가 나도 유실되지 않는다
        //   하지만 지연 시간이 더 발생함
        // 그러나 이 셋 설정에서 컨슈머가 해당 메시지를 받아보는 시간은 동일하다 왜냐하면 ISR에 설장된 복제셋에 모든 메시지가 복제되어야만 컨수머가 읽을 수 있기 때문이다.
        // 다만 이 설정은 그냥 프로듀서가 언제를 성공으로 간주하느냐에 달린 설정인 것 뿐이다.
        // min.insync.replicas와 헷갈릴 수 있는 이 설정은 브로커 측 설정으로 현재 정상적으로 복제 중인 replica가 몇 개 이상 있어야 메시지 수락할지에 대한 설정이다.
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        // 메시지 전달 시간
        // 메시지 전송을 기다리는 최대 시간 (단위: ms)
        // 프로듀서의 전송 버퍼가 가득 차거나 메타 데이터가 아직 사용 가능하지 않을 때 기다리는데 그 기다리는 시간
        // send()를 호출하기 까지 기다리는 시간
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000);
        // 레코드 전송 준비가 완료된 시점(send()가 문제없이 리턴되고, 레코드가 배치에 저장된 시점) ~ 브로커의 응답을 받거나 전송을 포기하게 되는 시점까지
        // linger.ms(배치와 request.timeout.ms보다 커야 한다.
        // 만약에 제시도를 하는 도중 이 값이 초과 된다면 마지막 에러 메시지와 함께 콜백이 호출됨
        // 가장 일반적인 설정은 리더 선출에 대략 30초가 걸리므로 이에 따라 120초로 설정
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);  // 메시지 전송 완료까지의 최대 시간 (단위: ms)
        // 메세지를 보내고 응답을 받기까지의 시간
        // ACK를 기다리는 시간
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);  // 요청에 대한 응답을 기다리는 최대 시간 (단위: ms)

        // 현재 배치가 전송하기 전까지 대기하는 시간
        // 기본적으로 전송할 수 있는 스레드가 있을 때 곧바로 전송되지만 해당 시간 만큼 대기해서 한번 보낼 때 더 많이 보내 처리량을 늘릴 수 있다.
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);

        // 프로듀서가 메시지를 전송하기 전에 매시지를 대기시키는 버퍼의 크기
        // 만약 애플리케이션이 서버에 전달 가능한 속도보다 더 빠르게 메세지를 전송한다면 버퍼 메모리가 빠르게 가득 차서 추후에 호출되는 send()가 max.block.ms까지 기다리다가
        // 버퍼 공간이 확보되지 않아 예외를 발생시킬 수 있음
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        // 메세지를 압축해서 보내는 알고리즘을 선택
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"); // 기본은 none임

        // 같은 파티션에 다수의 레코드가 전송될 경우 프로듀서는 이것들을 배치 단위로 모아서 한꺼번에 전송 -> 그 때의 사이즈 설정
        // 배치가 가득차면 전송, 하지만 찰 땨까지 기다리는 것은 아니고 하나여도 보낸다.
        // 그래서 지나치게 큰 값을 설정한다고 해서 지연으로 연결되지 않음 다만 너무 작으면 자주 메시지를 전송해야 해서 오버헤드 발생
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16_384);

        // 한 브로커와의 연결에 대해 동시에 보낼 수 있는 요청(배치)의 개수
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);

        // 메시지의 최대 크기 및 매시지의 개수도 제한
        // 1MB이고 메시지 하나당 1KB이면 1024개를 리스트로 만들어 브로커에 보낼 수 있음
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576);  // 1MB (1,048,576 bytes)

        // 데이터를 읽거나 쓸 때 소켓이 사용하는 TCP 송수신 버퍼의 크기를 결정
        // 각각의 값이 -1일 경우에는 운영체제의 기본값 사용
        props.put(ProducerConfig.RECEIVE_BUFFER_CONFIG, 16384);
        props.put(ProducerConfig.SEND_BUFFER_CONFIG, 16384);

        // 멱등성 프로듀서 기능 활성화
        // 프로듀서는 레코드를 보낼 때마다 순차적인 번호를 붙여서 보내게되어 있어 브로커가 동일한 번호를 가진 레코드를 2개 이상 받을 경우 하나만 저장
        // 프로듀서에게 메시지 저장에 성공했다는 응답을 보내기 전에 크래시 되었고 이에 따라 request.timeout.ms를 초과해서 프로듀서가 재시도를 하게됨
        // 하지만 이 설정으로 인해서 보내지 않고 DuplicateSequenceException을 받게 된다.
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

}
