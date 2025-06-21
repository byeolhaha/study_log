ㅋ## 네트워크 IO와 자원 효율
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
   
