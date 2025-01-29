## 일단 Avro를 왜 사용하는가? 
내가 찾아보고 실제로 경험해보았을 때를 생각하여 정리해보면 아래와 같다.
- 스키마에 의존적인 코드를 작성했더니 스키마가 변경되는 경우 이를 생성하고 소비하는 쪽에서 해당 객체를 모두 바꿔줘야 한다. 그런데 이 객체에 대한 코드가 널리 퍼져 있다면? 모두 다 바꿔줘야 한다.
- 스키마가 변경되었다고 하자, 하지만 처리되지 않고 남아있는 메시지가 예전 버전을 가지고 있는 경우라면? 이 메세지를 유실되어도 되는걸까? 서비스 정책에 따라 다르겠지만 유실을 막아야 하는 서비스라면 구 버전에 대한 메시지 처리도 필요할 것이다
  - 책에서 예를 든 것이 있었는데 예를 들어 팩스 번호가 포함된 메시지가 있다고 하면 지금은 팩스 번호를 잘 사용하지 않기 때문에 팩스 번호를 삭제하고 휴대폰 번호를 필드에 추가했다고 가정하면
  - Avro를 사용한다면 구버전 메시지에 대해서 팩스 번호에 대한 get이 호출되었을 때 null을 반환한다. 그리고 휴대폰 번호에 대한 get도 null을 반환할 것이다.
- 따라서 코드에 의존적이지 않으면서 버전 관리가 필요한 무언가가 필요한데 그것이 Avro이다.
- Avro의 버전관리는 Registry라는 저장소를 통해서 관리된다.

## 그래서 어떻게 사용되는 것인가?
나는 Spring Boot와 Java에서 진행했고
진행하면서 마치 QueryDsl의 Qclass를 생성하는 것과 같은 특징을 볼 수 있었다. 그리고 방법은 아래와 같다.
1. 가장 먼저는 토픽을 브로커에 등록한다. (생성되는 메시지의 토픽을 자동으로 등록하는 브로커가 아니라면 1번 과정이 필요하다. 자동 등록은 권장되지 않는다는 점을 기억하자)
2. 그리고 Schema Registry에 스키마를 등록한다. 나는 Confluent에 제공하는 Schema Registry를 이용했다.
   - Docker를 이용해서 내 컴퓨터에 레지스트를 만들어두었다.
   ![image](https://github.com/user-attachments/assets/875ecce0-5847-4659-bf53-1fad0cc3a54b)
   - 레시트리 내부에 접속해서 아래 curl요청을 날린다.
   ```
   curl -X POST -H "Content-Type: application/vnd.schemaregistry.v1+json"
   --data '{"schema": "{\"type\":\"record\",\"name\":\"Customer\",\"namespace\":\"com.producer.demo.avro\",\"fields\":[{\"name\":\"customerID\",\"type\":\"int\"},{\"name\":\"customerName\",\"type\":\"string\"}]}"}' http://localhost:8081/subjects/my-avro-value/versions
   ```
4. 등록을 완료했다면 이제 build.gradle에 Schema Registry에 등록한 스키마를 코드로 자동생성해주는 의존성을 심어줘야 한다.
   ```
   plugins {
   ...
	id "com.github.davidmc24.gradle.plugin.avro" version "1.9.1"
   }

   ...

   dependencies {
   ...

	  // Kafka Avro Serializer 의존성
	  implementation 'io.confluent:kafka-avro-serializer:6.2.0'  
  
	  // Avro 의존성
	  implementation "org.apache.avro:avro:1.12.0"

  	// Kafka Schema Registry Client 의존성
	  implementation 'io.confluent:kafka-schema-registry-client:6.2.0'  

   }
   repositories {
	    mavenCentral()
	    maven {
		    url 'https://packages.confluent.io/maven/'
	    }
    }

   avro{
	    outputCharacterEncoding = "UTF-8"
   }
   ```
5. 그리고 나서 main>avro>내가_등록하고자하는_스키마명.avsc에 스키마 파일을 올린다.
   ```
   {
   "type": "record",
   "name": "Customer",
   "namespace": "com.producer.demo",
   "fields": [
      { "name": "customerID", "type": "int" },
      { "name": "customerName", "type": "string" }
    ]
   }
   ```
   ![image](https://github.com/user-attachments/assets/7406e1ad-3c24-4d6b-9cfd-b0f36a0d2475)
6. 그리고 나서 빌드를 진행하면 build>generated-main-avro-java>com.producer.demo에 Customer 객체가 생성된다.
   ![image](https://github.com/user-attachments/assets/68e959c1-a47a-49cb-b216-092086394f31)

## Avro를 통한 시리얼라이즈와 디시얼라이즈 진행하기
- Producer Config
```java
...
 // Avro 직렬화를 위한 설정
 props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
 props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
// Schema Registry 설정
 props.put("schema.registry.url", "http://localhost:8081");  // Schema Registry URL
```
- Consumer Config
```java
...
consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
consumerProps.put("schema.registry.url", "http://localhost:8081");
```
![image](https://github.com/user-attachments/assets/cb6540b2-21fa-40c1-bbbb-b3475f48d802)

잘 읽힌다.



