package com.producer.demo.consumer;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.RangeAssignor;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers = "localhost:10002";

    @Bean
    public Map<String, Object> consumerConfig() {
        // 기본속성
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "CountryCounter");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        // 컨슈머가 브로카로부터 레코드를 얻어올 때 받는 데이터의 최소량(바이트) - 기본 1바이트
        // 컨슈머로부타 레코드를 요청을 받았는데 새로 보낼 레코드의 양이 설정된 값보다 작으면 기다렸다가 채워서 레코드를 보낸다.
        // 따라서 만약 이 값이 크고, 읽어올 데이터가 그리 많지 않다면 이 값을 채우기 위해 기다리기 때문에 지연 또한 증가할 수 있다.
        consumerProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);

        // fetch.min.bytes는 충분할 데이터가 설정된 값만큼 모일 때까지 기다린다면
        // fetch.max.wait.ms는 얼마나 오래 기다릴 것인지를 결정 - 기본 500ms
        consumerProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500);

        // 이 속성은 컨슈머가 브로커를 폴링할 때 카프카가 리턴하는 최대 바이트 수를 지정한다 - 기본값은 50MB
        // 컨슈머가 서버로부터 받은 데이터를 저장하기 위해 사용하는 메모리 양을 제한하기 위해 사용
        // 브로커가 컨슈머에게 레코드를 보낼 때 배치 단위로 보내는데 만약에 첫 요청부터 이 설정의 크기를 초과한다면 그래도 첫 요청에서 만큼은
        // 이 설정을 무시하고 보낸다.
        // 카프카 자체에도 보낸 배치의 크기를 제한하기 때문에 (부하를 막기 위해서) 브로커 설정을 사용할 수도 있다.
        consumerProps.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 50 * 1024 * 1024);

        // poll()을 호출할 때마다 리턴되는 최대 레코드 수 - 기본값 500
        consumerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);

        // 일단 설정하기 까다로운 설정인 것을 알아두자
        // 서버가 파티션별로 리턴하는 최대 바이트 수를 결정 - 기본값 1MB
        // poll()가 레코드를 반환할 때 메모리 상에 저장된 레코드 객체의 크기를 파티션별로 지정한 것이다.
        // 하지만 이는 예측하기 까다롭기 때문에 FETCH_MAX_BYTES_CONFIG를 이용한다.
        consumerProps.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576);

        // settion.timeout.ms : 컨슈머가 브로커와 신호를 주고받지 않고도 살아있는 것으로 판정하는 시간 - 기본값 45 ( 버전 3.0 이전은 10초)
        // 해당 시간이 지나면 죽은 것으로 간주하고 리밸런스 실행
        // 이는 heartbeat.interval.ms와 밀접한 연관이 있어서 대게 session.timeout.ms/3 = heartbeat.interval.ms
        // session.timeout.ms가 작으면 빠르게 죽은 컨슈머를 찾을 수 있지만 원치 않게 자주 리벨런싱이 발생할 수 있다. 반대의 트레이드 오프도 생각해보자.
        consumerProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 1000 * 60 * 45);
        consumerProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 1000 * 60 * 5); // 하지만 기본값은 1/3이 아님

        // 컨슈머가 폴링을 하지 않고도 죽은 것으로 판정되지 않을 수 있는 최대 시간 지정 - 기본값 5분
        // 하트비트는 백그라운드 스레드로 작동하기 때문에 메인 스레드가 데드락에 걸렸지만 해당 백그라운드 스레드는 정상 동작할 수도 있다.
        // 따라서 가장 쉬운 방법은 추가로 poll()을 또 요청하는지 확인하는 것이지만 이 것은 예측하기가 까다롭다.
        // 그래서 max.poll.records와 max.poll.interval.ms를 함께 사용하여 메인 스레드의 멈춤 여부를 확인하자.
        // 이 값은 정상 작동 중인 컨슈머가 매우 드물게 도달하도록 크게, 하지만 정지한 컨슈머로 인한 영향이 나타내도록 작게 설정한다.
        consumerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 1000 * 60 * 5);

        // API 호출에 대한 명시적인 타임 아웃을 지정하지 않는 한, 거의 모든 컨슈머의 타임아웃 값 - 기본 1분
        // 이 값이 요청 타임 아웃 값보다 크기 때문에 이 시간 안에 재시도를 할 수 있다.
        // 이 값이 적용되지 않는 중요한 예외는 poll()이다. 이 메서드는 반드시 명시적으로 타임아웃을 지정해야 한다.
        consumerProps.put(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 1000 * 60);

        // 컨슈머가 브로커로부터의 응답을 기다릴 수 있는 최대 시간  - 기본 30초
        // 클라이언트는 이 시간이 자나면 다시 재연결을 시도한다.
        // 기본값보다 작게 하는 것은 비추천 -> 브로커에게 요청을 처리할 충분한 시간을 주자, 그리고 더 짧게 한다고 해서 이미 과부하된 브로커로부터 얻을 게 없다.
        // 오히려 오버헤드만 추가된다.
        consumerProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000 * 30);

        // 컨슈머가 예전에 오프셋을 커밋한 적이 없거나, 커밋된 오프셋이 유효하지 않을 때, 파티션을 읽기 시작할 때의 작동을 설정 - 기본값 latest
        // latest : 만약 유효한 오프셋이 없을 경우 컨슈머는 가장 최신의 레코드부터 읽는다. = 컨슈머가 작동하기 시작한 다음부터 쓰여진 레코드
        // earliest : 유효한 오프셋이 없을 경우 파티션의 맨 처음부터 모든 데이터를 읽는다.
        // none : 유효하지 않은 오프셋부터 읽으려 할 경우 예외 발생
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        // 컨슈머가 자동으로 오프셋을 커밋할지의 여부 - 기본 true
        // 언제 커밋할지 커스텀하고 싶다면 false로 하자
        // true로 할 경우 auto.commit.interval.ms를 사용해서 얼마나 자주 오프셋이 커밋될지를 제어할 수 있다.
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

        /**
         * 어느 컨슈머에게 어느 파티션이 할당될지를 결정하는 역할
         * 할당 전략
         * Rage - 기본값
         * 컨슈머가 구독하는 각 토픽의 파티션들을 연속된 그룹으로 나눠서 할당
         * 만약 C1, C2의 컨슈머가 토픽 T1, T2를 모두 구독하고 파티션이 각각 3개라면
         * C1이 T1, T2에 대한 0번과 1번 파티션, C2가 T1, T2에 대한 2번 파티션을 할당 받는다.
         * 각 토픽의 홀수 개의 파티션을 가지고 있는 경우 -> 처음 컨슈머가 더 많은 파티션을 할당받게 된다.
         *
         * RoundRobin
         * 모든 구독된 토픽의 모든 파티션을 가져다 순차적을 하나씩 컨슈머에게 할당
         * 만약 C1, C2의 컨슈머가 토픽 T1, T2를 모두 구독하고 파티션이 각각 3개라면
         * C1이 T1는 0번과 1번 파티션, T2는 2번 파티션, C2는 T1에 대한 2번 파티션, T2에 대한 0번,1번 파티션을 할당 받는다.
         * 모두 동일한 토픽들을 구독한다면 할당받은 파티션의 수는 동일하다.
         *
         * Sticky
         * 목표 2개
         * - 파티션들을 가능한 한 균등하게
         * - 리밸런싱이 발생했을 때 가능하면 많은 파티션들이 같은 컨슈머에게 할당되게 하는 것
         * 컨슈머들이 서로 다른 토픽을 구독하는 경우 라운드로비보다 더 균형있게 배정함
         *
         * Cooperative Sticky
         * 컨슈머가 재할당되지 않은 파티션으로부터 레코드를 계속해서 읽어오는 협력적 리밸런스 기능을 지원
         **/
        consumerProps.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, RangeAssignor.class);

        // 브로커가 요청을 보낸 클라이언트를 식별하는데 사용
        consumerProps.put(ConsumerConfig.CLIENT_ID_CONFIG, "CountryCounter");

        // 컨슈머는 각 파티션의 리더 레플리카로부터 메시지를 읽어오지만 클러스터가 다수의 데이터 센터 혹은 다수의 클라우드 가용 영역에 있는 경우
        // 가장 가까운 레플리카로부터 메시지를 읽어오도록 하는 것
        // 따라서 자신에게 가까은 랙 설정을 잡아주고 브러커의 replica.selector.class 기본 설정을 RackAwareReplicaSelector로 잡아준다.
        consumerProps.put(ConsumerConfig.CLIENT_RACK_CONFIG, "rack-1");

        // 정적 그룹 아이디 설정
        consumerProps.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "countryCounter-1");

        // 데이터를 읽거나 쓸 때 소켓이 사용하는 TCP의 수신 및 수신 버퍼의 크기를 설정
        // -1로 잡아두면 운영체제 기본값
        // 다른 데이터센터에 있는 브로커와 통신하는 프로튜서나 칸수머의 경우 이 값을 올려잡아주면 좋다
        consumerProps.put(ConsumerConfig.RECEIVE_BUFFER_CONFIG, -1);
        consumerProps.put(ConsumerConfig.SEND_BUFFER_CONFIG, -1);

        // offsets.retention.minutes : 브로커 설정
        // 현재 돌아가고 있는 컨슈머들이 있는 한 컨슈머 그룹이 각 파티션에 대해 커밋한 마지막 오프셋값을 카프카에 보존함 그래서 재할당이 발생하거나 재시작하면 사용
        // 그러나 그룹이 비게 된다면 카프카는 이 설정값에 지정된 기간 동안만 보존
        // 이 기간 이후에 다시 그룹이 활동하면 과거에 수행했던 이력은 사라진다.
        return consumerProps;
    }


    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // 하나의 스레드에서 동일한 그룹 내에 여러 개의 컨슈머를 생성할 수는 없다.
        // 하나의 스레드당 하나의 컨슈머가 원칙이다.
        // 애플리케이션에서 동일한 그룹에 속하는 여러 개의 컨슈머를 운용하고 싶다면 스레드를 여러 개 띄워서 각각의 컨슈를 하나씩 돌리는 수밖에 없다.
        // 아래 설정이 그러하다.
        factory.setConcurrency(2);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfig());
    }
}
