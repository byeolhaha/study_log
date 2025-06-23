 ## 네트워크 IO와 자원 효율
- 서버는 다양한 구성 요소(DB, 레디스, 외부 API)와 네트워크를 통해서 데이터를 주고받는다.
- 데이터 입출력이 완료될 때까지 스레드는 아무런 작업도 하지 않은 채 기다린다 이는 CPU가 아무것도 하지 않는 시간이 발생한다는 의미이다.
- 생각의 발전 1 : 그렇다면 스레드를 많이 만들어서 CPU가 쉬지 못하도록 하자 (요청 당 스레드 방식으로 구현한 서버가 이 방식에 해당된다.)
  - 하지만, 스레드를 생성하는 것에 한계가 존재, 스레드도 메모리를 사용하기 때문이다.
  - 생각의 발전 1-1 : 그렇다면 메모리를 늘려서 사용할 스레드를 늘리자.
    - 하지만 메모리를 늘린다고 해도 스레드가 많아지면 컨텍스트 스위칭이 자주 발생하게 될 것이다. 왜냐하먄 cpu core 수는 고정되어져 있기 때문이다.
  - 그래서 트래픽이 많아진다면 아래와 같은 상황이 만들어진다.
    - IO 대기와 컨텍스트 스위칭에 따른 CPU 낭비
    - 요청마다 스레드를 할당하기 때문에 그것에 따른 메모리 낭비
  - 하지만 톰캣과 같은 요청 당 스레드를 만드는 방식이 비효율로 보일 수 있지만 다수의 서버에서는 문제가 없다


- 자원을 효율적으로 사용하는 방법 (메모리나 CPU 자원을 늘리지 않고도 더 많은 트래픽을 처리하는 방법)
  - 가상 스레드나 고루틴 같은 경량 스레드 사용하기
  - 논블로킹 또는 비동기 IO 사용하기

## 가상 스레드로 자원 효율 높이기
![image](https://github.com/user-attachments/assets/9992bfae-243e-4597-9814-e9c1a81b6d61)

- 플랫폼 스레드는 OS와 1:1로 매핑되기 때문에 OS 레벨의 스레드이다. 따라서 cpu 스케줄러에 의해서 관리된다.
- 가상 스레드는 JVM 스케줄러가 플랫폼 스레드를 어떤 가상 스레드에 매칭시킬 것인지 결정한다.
- 만약에 네트워크 IO가 있는 작업을 가상 스레드에서 실행한다고 가정했을 때 플랫폼 스레드1은 블로킹이 될 때까지 일을 하다가, 이를 기다리지 않고 다른 작업을 하러 간다. 즉 언마운트된 플랫폼 스레드는 실행 대기 중인 다른 가상 스레드와 연결된다. 그리고 그 블로킹 작업이 완료되면 쉬고 있던 다른 플랫폼 스레드가 이를 이어 받는다.
- 자바의 경우 플랫폼 스레드는 ForkJoinPool에서 가져오는 거 같다. 이 ForkJoinPool은 전용 스레드풀이 아니기 때문에 모두 공유한다. 따라서 가상스레드를 너무 많은 곳에서 사용하는 경우 경쟁상태가 발생할 거 같다. 그리고 가상 스레드가 플랫폼 스레드에 비해서 메모리를 적게 자치한다고 해도 이 또한 무제한으로 생성하면 메모리를 많이 차지할 것이다. 
- 이 시점에서 어떻게 컨텍스트 스위칭이 발생하고 인터럽트가 발생하는가? 즉 완료된 시점을 어떻게 알고 다른 플랫폼 스레드에게 컨텍스트를 주는가에 대한 궁금증이 존재했다. 일단 컨텍스트는 JVM 메모리 영역에 저장된다고 한다. 그리고 인터럽트는 epoll 등의 개념을 사용하여 알려준다고 한다. 이 개념은 추후에 더 깊게 들어가봐야할 거 같다.
- 코드를 통해서 구현해보니 아래와 같이 출력되었다.
  ```java
  package com.example.demo.controller;

  import org.springframework.web.bind.annotation.GetMapping;
  import org.springframework.web.bind.annotation.RestController;

  @RestController
  public class TomcatVirtualThreadController {

    @GetMapping("/test1")
    public String test1() throws InterruptedException {
        String tomcatThreadName = Thread.currentThread().toString();
        System.out.println("[톰캣 스레드 시작] " + tomcatThreadName);

        for (int i = 0; i < 5; i++) {
            final int n = i;
            Thread.startVirtualThread(() -> {
                System.out.println(n+"번  [가상 스레드 시작] " + Thread.currentThread());
                try {
                    Thread.sleep(1000);  // I/O blocking처럼 보이게
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println(n+"번  [가상 스레드 종료] " + Thread.currentThread());
            });
        }

        Thread.sleep(3000); // 톰캣 스레드가 죽지 않도록 유지
        System.out.println("[톰캣 스레드 종료] " + tomcatThreadName);
        return "ok";
      }
  }
  ```
  ```
  [톰캣 스레드 시작] Thread[#63,http-nio-1010-exec-6,5,main]
  0번  [가상 스레드 시작] VirtualThread[#93]/runnable@ForkJoinPool-1-worker-7
  1번  [가상 스레드 시작] VirtualThread[#95]/runnable@ForkJoinPool-1-worker-8
  3번  [가상 스레드 시작] VirtualThread[#97]/runnable@ForkJoinPool-1-worker-8
  4번  [가상 스레드 시작] VirtualThread[#98]/runnable@ForkJoinPool-1-worker-8
  2번  [가상 스레드 시작] VirtualThread[#96]/runnable@ForkJoinPool-1-worker-9
  0번  [가상 스레드 종료] VirtualThread[#93]/runnable@ForkJoinPool-1-worker-11
  1번  [가상 스레드 종료] VirtualThread[#95]/runnable@ForkJoinPool-1-worker-9
  4번  [가상 스레드 종료] VirtualThread[#98]/runnable@ForkJoinPool-1-worker-12
  2번  [가상 스레드 종료] VirtualThread[#96]/runnable@ForkJoinPool-1-worker-8
  3번  [가상 스레드 종료] VirtualThread[#97]/runnable@ForkJoinPool-1-worker-10
  [톰캣 스레드 종료] Thread[#63,http-nio-1010-exec-6,5,main]
  ```

- 책에 따르면 자바 버전에 따라서 위에서 배운 블로킹 작업을 만나 언마운트된 플랫폼 스레드가 대기 중인 가상 스레드의 작업을 실행하지 못하고 기존의 가상 스레드에 의해서 고정되는 상태가 발생할 수 있다고 한다.
  이는 자바 23 또는 그 이전 버전에서 synchronized로 인해 블로킹되는 경우에 해당된다. 이 외에도 JNI 호출 등 가상 스레드가 플랫폼 스레드에 고정되는 경우가 있는데 가상 스레드가 플랫폼 스레드를 고정시키면 CPU 효율을 높일 수 없다.'

### 가상 스레드와 성능
- 가상 스레드의 효과를 볼 수 있는 것이 CPU 중심 작업 보다 IO 중심 작업이다. IO는 가상 스레드가 지원하는 블로킹 연산이므로 IO 중심 작업일 때 플랫폼 스레드가 CPU 낭비 없이 효율적으로 여러 가상 스레드를 실행할 수 있다.
- 그렇다고 해서 모든 IO 중심 작업에 효과적인 것은 아니다. 가상 스레드의 개수보다 플랫폼 스레드의 개수가 더 적을 때 효과가 있다. 플래폿 스레드의 개수 < 가상 스레드의 수
- 아래의 같은 상황이다.
  - 환경
   - CPU 코어 수 16
   - TPS 500
   - 1개의 요청을 처리하는 데 소요되는 시간은 20밀리초
   - 모든 요청은 IO 중심 작업
 - 계산
   - 1개의 가상 스레드는 1초(1000ms)에 50개의 요청을 처리할 수 있다.
   - 500 TPS를 처리하려면 가상 스레드는 10개가 필요
   - 플랫폼 스레드는 cpu코어 수만큼 생성되므로 16개
  - 설명
    - 만약 10개의 가상 스레드가 동시에 IO 작업에 들어가면 나머지 플랫폼 스레드들은 할일이 없다. 대기하고 있는 가상 스레드가 없기 때문이다.
  - 효율적으로 사용하기 위해서
    - cpu 코어 수를 줄이거나
    - 트래픽이 더 많아지거나
- 가상 스레드의 이점
  - 처리량을 높일 수 있다.
  - 실행속도나 플랫폼 스레드보다 더 빨라지는 것은 아니다.

## 논블로킹 IO 성능 더 높이기
- 가상 스레드와 고루틴과 같은 경량 스레드를 사용하면 IO 중심 작업을 하는 경우 서버 처리량을 높일 수 있다.
- 그러나 경량 스레드 자체도 메모리를 사용하고 스케줄링이 필요하다. (일반 OS 스레드보다 작다고 하더라도 메모리 차지하기는 한다. 그리고 경량 스레드는 혼자 작업을 진행하지 못하고 플랫폼 스레드를 사용해야만 하며 IO 작업에 플랫폼 스레드가 대기하지 못하도록 대기하는 다른 경상 스레드를 찾도록 하는 스케줄링이 필요하다)
- 따라서 사용자가 폭발적으로 증가하면 어느 순간 경량 스레드만으로도 한계에 부딪힐 수 잇다.
=> 이 때 서버의 IO 구현 방식으로 구조적으로 변경해야 한다. 바로 논블러킹 IO를 사용하는 것이다.
### 논블로킹IO 동작 개요
논블러킹 IO는 입출력이 끝날 때까지 스레드가 대기하지 않는다. 
```
//channel : SocketChannel, buffer : ByteBuffer
int byteReads = channel.read(buffer);//데이터를 읽을 때까지 대기하지 않는다.
```
- 위 코드 설명
  - 조회했는지 여부에 상관없이 바로 다음 코드 실행 따라서 데이터를 조회했다는 가정하에 코드를 작성할 수 없다.
```
while(true) {
  //channel : SocketChannel, buffer : ByteBuffer
  int byteReads = channel.read(buffer);//데이터를 읽을 때까지 대기하지 않는다.
  if(byteReads > 0) { // 조회 데이터가 있는 경우
   // 실행한 코드
  }
}
```
- 위 코드 설명
  - 데이터를 조해했다는 가정하에 코드를 작성할 수 있다.
  - 하지만 CPU낭비가 심하다. 읽을 데이터가 없어도 계속 while loop를 돈다.

실제로 논블러킹 IO 사용할 때는 데이터 읽기를 바로 시도하기 보다는 어떤 연산을 수행할 수 있는지 확인하고 해당 연산을 실행한다.
실행 흐름 요약
1. 실행 가능한 IO 연산 목록을 구한다. (실행 가능한 연산을 구할 때까지 대기)
2. 1에서 구한 IO 연산 목록을 차례대로 수행한다.
  - 각 IO 연산을 처리한다.
3. 이 과정을 반복한다.
```
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class NioEchoServer {

    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open(); // ① Selector 생성

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(5000));
        serverSocketChannel.configureBlocking(false); // 논블로킹 모드 설정
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT); // ② 연결 수락 이벤트 등록

        System.out.println("📡 NIO Echo 서버 시작 (port: 5000)");

        while (true) {
            // 1. 실행 가능한 IO 연산 목록을 구한다. (없으면 blocking 상태로 대기)
            selector.select();

            // 2. 실행 가능한 키(이벤트들)를 가져온다.
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            // 3. 실행 가능한 IO 연산을 하나씩 처리한다.
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                if (key.isAcceptable()) {
                    // 클라이언트 연결 수락
                    ServerSocketChannel server = (ServerSocketChannel) key.channel();
                    SocketChannel client = server.accept();
                    client.configureBlocking(false);
                    client.register(selector, SelectionKey.OP_READ);
                    System.out.println("클라이언트 연결됨: " + client.getRemoteAddress());

                } else if (key.isReadable()) {
                    // 클라이언트로부터 데이터 읽기
                    SocketChannel client = (SocketChannel) key.channel();
                    ByteBuffer buffer = ByteBuffer.allocate(1024);

                    int bytesRead = client.read(buffer);
                    if (bytesRead == -1) {
                        System.out.println("클라이언트 연결 종료");
                        client.close();
                    } else {
                        buffer.flip();
                        client.write(buffer); // 그대로 echo 응답
                        buffer.clear();
                    }
                }

                // 4. 해당 키는 이제 처리했으니 제거
                keyIterator.remove();
            }
        }
    }
}

```
- 논블로킹 IO를 1개 스레드로 구현하면 동시성이 떨어진다. 1개 채널에 대한 읽기 처리가 끝나야 다음 채널에 대한 읽기 처리를 실행한다. 즉 두 개 채널에 대한 읽기 연산이 가능해도 한번에 1개 채널에 대해서 처리가 가능하다.
- 논블러킹 IO에서 동시성을 높이기 위해서 사용하는 방법은 채널들을 N개 그룹으로 나누고, 각 그룹마다 스레드를 생성하는 것이다. 보통은 CPU 개수만틈 그룹을 나누고 각 그룹마다 입출력을 처리할 수 있는 스레드를 할당하다. 


여기서 이 부분이 헷갈렸다.
"여기서 실행 가능한 연산을 구할 때까지 대기한다."
→ 이 말은 Selector.select() 같은 메서드가 I/O 이벤트가 발생할 때까지 블로킹된다는 의미이다.
→ 하지만 이 블로킹은 단 하나의 스레드가 수천 개의 채널을 감시하기 위한 효율적인 블로킹이며,
→ 요청 하나당 하나의 스레드가 필요한 전통적인 블로킹 I/O와는 다르다.


그래서 아래와 같이 정리해보았다.
### 가상 스레드와 논블러킹 IO
가상 스레드(Virtual Thread)
- 요청 N개 → 가상 스레드 N개 생성 가능
- 각 가상 스레드는 동기 코드처럼 read(), send() 등 블로킹 I/O 호출을 사용
- 하지만 I/O 작업 중에는 실제 플랫폼 스레드를 반납(park) → 즉, 자원을 낭비하지 않음
- 결과적으로 논블로킹 구조만큼 자원 효율적이면서, 동기식 코드 작성이 가능
  -  장점: 동기 코드의 간결함 + 논블로킹의 자원 효율성
  -  단점: 내부는 여전히 블로킹 기반이므로 완전한 논블로킹 네트워크 처리에는 적합하지 않을 수 있음

논블로킹 I/O (Selector 기반, Netty, WebFlux 등)
- 요청 N개 → 스레드는 1~몇 개면 충분
- 소켓 채널을 모두 Selector에 등록
- 하나의 스레드가 select()로 I/O 이벤트를 감지하고, 이벤트가 발생한 채널만 처리
- 진짜로 아무 것도 기다리지 않음. 이벤트가 있을 때만 반응
  - 장점: 수만 개의 연결도 몇 개의 스레드로 처리 가능
  - 단점: 코드가 콜백 지옥 or 리액티브 스트림으로 복잡해짐


나는 이 두개가 처리량을 언급할 때 항상 같이 언급되었는데 어떤 상황에서 사용해야 하는지 이해하지 못했었다.
그런데 이제는 정리가 되었다.
가상 스레드는 플랫폼 스레드는 반납하기 때문에 자원을 효율적으로 사용할 수 있다. 그렇지만 메모리를 많이 차지할 수 있다.
논블러킹 IO는 IO작업에 대해서 적은 스레드를 사용한다. 하지만 코드가 복잡해진다. 

### 직접 실행한 코드
그래서 정말 그럴까? 정말 논블러킹 IO가 메모리를 적게 차지하고 정말 적은 수의 스레드를 만들까?
실제로 인텔리제이 프로파일을 통해서 비교해보았다.

코드는 아래와 같다.
- ```/test-non-blocking``` : 논블러킹 IO를 이용해서 내부에 있는 /delay API 호출
- ```/test-virtual-thread``` : 가상 스레드를 이용해서 내부에 있는 /delay API 호출
- 그리고 이 둘의 API를 같은 부하를 주고 인텔리제이 프로파일로 비교해보자

```java
@RestController
@RequiredArgsConstructor
public class WebClientVsVirtualThreadController {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://localhost:1010")
            .build();

    // HttpClient를 필드로 한 번만 생성해서 재사용
    private final HttpClient sharedHttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1) // HTTP/1.1 강제 설정 (커넥션 재사용 보장)
            .build();

    @GetMapping("/test-non-blocking")
    public ResponseEntity<String> testNonBlocking() {
        for (int i = 0; i < 100; i++) {
            int finalI = i;
            webClient.get()
                    .uri("/delay")
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(res -> System.out.println("[WebClient] " + finalI + " => " + Thread.currentThread().getName()))
                    .subscribe();
        }
        return ResponseEntity.ok(" WebClient 요청 전송 완료");
    }

    @GetMapping("/test-virtual-thread")
    public ResponseEntity<String> testVirtualThread() {
        var executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());

        for (int i = 0; i < 100; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:1010/delay"))
                            .GET()
                            .build();

                    HttpResponse<String> response = sharedHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("[VirtualThread] " + finalI + " => " + Thread.currentThread());
                } catch (Exception e) {
                    System.err.println("[ 오류 발생] " + finalI);
                    e.printStackTrace();
                }
            });
        }

        return ResponseEntity.ok(" 가상 스레드 요청 전송 완료");
    }
}
```

```java
@RestController
class DelayServerController {
    @GetMapping("/delay")
    public String delay() throws InterruptedException {
        Thread.sleep(100); // 3초 지연
        return "Done";
    }
}
```

Artillery로 실행한 스크립트는 아래와 같다.
```
config:
  target: "http://localhost:1010"
  phases:
    - duration: 100
      arrivalRate: 10
scenarios:
  - name: "Virtual Thread Test with System Stats"
    flow:
      - get:
          url: "/test-non-blocking"
```

```
config:
  target: "http://localhost:1010"
  phases:
    - duration: 100
      arrivalRate: 10
scenarios:
  - name: "Virtual Thread Test with System Stats"
    flow:
      - get:
          url: "/test-virtual-thread"
```

#### 실행 결과
메모리 사용률은 Heap Used로 비교했는데 그 중 최대값
- 가상 스레드 : 89Mib
- 논블러킹 IO : 79Mib

생성한 스레드 
- 가상 스레드 : 여러개(약 세자리수까지)의 플랫폼 스레드를 만들었다. 해당 코드에서는 플랫폼 스레드로 HttpClient-1-Worker류의 스레드를 여러개 생성 했다. 최대 92번까지 생성했다.
- 논블러킹 IO: 소수의 reactor-http-nio류만 만들어 사용해서 처리했다. 아래 사진은 100개 이벤트만 보여줘서 1만 나와있는데 실제로는 6이내까지 만들었다. 
![image](https://github.com/user-attachments/assets/b5efc6c7-d9f0-4404-a0e3-92ae04c28ba8)

### 리액터 패턴
- 논블러킹 IO를 이용해서 구현할 때 사용하는 패턴 중 하나
- 동시에 들어오는 여러 이벤트를 처리하기 위한 이벤트 처리 방법이다. 리액터 패턴은 크게 리액터와 핸들러 두 요소로 구성된다.
- 리액터는 이벤트까 발생할 때까지 대기하다가 이벤트가 발생하면 알맞는 헨들러에 이벤트를 전달한다. 이벤트를 받은 헨들러는 필요한 로직을 수행한다.
   ```java
   while(running) { // 이벤트 루프 
     List<Event> events = getEvents(); // 이벤트가 발생할 때까지 대기
     for(Event event : events) {
       Handler handler = getHandler(event);// 이벤트를 처리할 핸들러를 구한다.
       handler.handle(event);
     }
   }
   ```
- 해당 이벤트 루프는 단일 스레드로 실행된다. 따라서 멀티 코어를 가진 서버에서 단일 스레드만을 사용하면 처리량을 최대한 낼 수 없다. 또 핸들러에서 CPU 연산이나 블로킹을 유발하는 연산을 수행하면 그 시간만큼 전체 이벤트 처리 시간이 지연된다.
- 그러나 Reactor Netty는 HttpClient 혹은 HttpServer를 만들 때 내부적으로 Netty의 NioEventLoopGroup을 사용하며, 이 이벤트 루프 그룹은 기본적으로 Runtime.getRuntime().availableProcessors() 수만큼의 스레드를 생성한다고 한다. 그냥 쉽게 말해서 알아서 코어를 잘 사용하도록 유동적으로 증가시키는 거 같다.
- 이러한 한계를 보완하기 위해서 핸들러나 블로킹 연산을 별도의 스레드 풀에서 실행하기도 한다.
  - before
     ```java
         @GetMapping("/test-non-blocking")
    public ResponseEntity<String> testNonBlocking() {
        for (int i = 0; i < 100; i++) {
            int finalI = i;
            webClient.get()
                    .uri("/delay")
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(res -> System.out.println("[WebClient] " + finalI + " => " + Thread.currentThread().getName()))
                    .subscribe();
        }
        return ResponseEntity.ok("✅ WebClient 요청 전송 완료");
    }
     ```
     ![image](https://github.com/user-attachments/assets/b5efc6c7-d9f0-4404-a0e3-92ae04c28ba8)
  - after
    ```java
        @GetMapping("/test-non-blocking")
    public ResponseEntity<String> testNonBlocking() {
        for (int i = 0; i < 100; i++) {
            int finalI = i;
            webClient.get()
                    .uri("/delay")
                    .retrieve()
                    .bodyToMono(String.class)
                    .publishOn(Schedulers.boundedElastic())//추가 코드
                    .doOnNext(res -> System.out.println("[WebClient] " + finalI + " => " + Thread.currentThread().getName()))
                    .subscribe();
        }
        return ResponseEntity.ok("✅ WebClient 요청 전송 완료");
    }
    ```
    - 위 코드는 블로킹 IO 연산이 있어서 해당 연산을 별도의 스레드 풀로 분리하였다. 그랬더니 아래와 같이 별도의 스레드 풀이 생성된 것을 확인할 수 있다.
      ![image](https://github.com/user-attachments/assets/20e69525-a90a-4ab0-9369-8e0fa4e0dcd4)

  - 부하테스트틑 진행하였다. 
    - 부하테스트 스크립트
       ```java
       config:
         target: "http://localhost:1010"
           phases:
             - duration: 100
               arrivalRate: 10
       scenarios:
         - name: "Virtual Thread Test with System Stats"
           flow:
             - get:
                 url: "/test-non-blocking"
       ```
    - 결과
      - before : 별도 스레드 풀을 만들지 않은
         - p99 : 12.1ms
       - after : 별도 스레드 풀을 만든
         - p99 : 10ms
    - 별도의 스레드 풀에 대한 고찰
      - 별도의 스레드 풀을 만든다는 것은 무거운 작업을 위임해 이벤트 루프를 보호하기 위함이다.
      - 아래 이미지에서 보이듯, 대부분의 스레드가 sleep 상태라면 여유가 있는 상황이지만, 만약 block이나 wait 상태로 전환된다면 이 스레드 풀 자체가 병목 구간이 될 수 있다.
      - 즉, 스레드 풀을 도입하면 이벤트 루프의 처리 효율은 향상되지만, 스레드 풀의 상태에 따라 전체 처리량이 제한될 수 있기 때문에, 풀 자체도 지속적인 모니터링과 튜닝이 필요할 거 같다. 
      - ![image](https://github.com/user-attachments/assets/c4153a99-7a7f-4f1c-9608-d61f36f88ce6)

### 논블러킹/비동기 Io와 성능

