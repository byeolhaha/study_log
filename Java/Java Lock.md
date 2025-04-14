Baeldung 정리 ( https://www.baeldung.com/java-concurrent-locks)
자바에서는 모니터를 어떻게 지원하는가를 찾아보다가 Java의 Lock에 대해서 정리한다.
이후에는 sychronize의 경량락과 중량락을 알아보고 reentrant lock과 비교해보려고 한다.

## 개요

잠금은 표준 동기화 블록보다 더 유연하고 정교한 스레드 동기화 메커니즘이다.

Lock interface는 1.5부터 제고하고였고 java.util.concurrent.lock 페키지 내부에 정의되어져 있다.

## Lock과 Synchronized의 차이

- Synchronized block은 메서드 내부에 선언된다. Lock은 메서드와 분리된 lock(), unlock() 메서드를 가질 수 있다.
- Synchronized block은 공정성을 가질 수 없다. 스레드는 lock이 release되어야 lock을 얻을 수 있으며 선호도를 지정할 수 없다. 하지만 Lock API는 공정성 상태값을 지정할 수 있어서 오랫동안 기다린 스레드에게 lock을 획득하게 할 수 있다.
- 어떤 스레드가 Synchronized block에 들어가려고 하는데 이미 다른 스레드가 해당 객체의 모니터 락을 가지고 있으면 이 스레드는 block 상태로 대기한다. 자바의 Lock API는 tryLock()이라는 특별한 메서드가 존재하는데 tryLock()을 사용하면 다른 스레드가 Lock을 가지고 있지 않을 때만 Lock을 얻는다. 락이 이미 사용중이면 그냥 실패로 넘어간다. 즉 기다리지 않는다 .

```java
Lock lock = new ReentrantLock();

if(lock.tryLock()) {
   try{
     //lock 얻고 할 일
   }finally{
     lock.unlock();
   }
}else{
  //락을 얻지 못할 때 할 일
}
```

- Synchronized Block에 진입하기 위해 대기중인 thread는 해당 스레드에 인터럽트가 발생해도 반응하지 않는다.(응답 없는 스레드 발생, 정리가 안되고, 문제 추적이 어렵다.) ⇒ 죽을 때까지 락을 기다린다. 반면에 Lock API는 lockInterruptly()를 제공해서 락을 얻기 위해 기다리는 와중에 인터럽트가 발생하면 예외를 던지고 빠져나올 수 있다.

## Lock API

method를 정리해본다.

- void lock()
    - 락이 비어있으면 즉시 획득하고 다른 스레드가 이미 락을 가지고 있으면 현재 스레드는 락이 해제될 때까지 대기한다. = synchronized
- ***void lockInterruptibly()***
    - lock()처럼 락을 얻으려고 시도하지만 락을 기다리는 와중에 interrupt()가 호출되면 락 획득을 멈추고 예외를 던지고 빠져나올 수 있다.
- boolean tryLock()
    - 락을 즉시 한 번 시도한다. 락 획득을 성공하면 true 반환, 실패하면 false를 반환한다. 락이 잠겨 있으면 포기하고 다른 일을 시도한다.
- boolean tryLock(long timeout, TimeUnit timeuit)
    - 락을 획득하려 정해진 시간동안 기다린다. 시간 안에 락을 얻으면 true, 얻지 못하면 false를 반환한다.
- void unlock()
    - 현재 스레드가 얻은 락을 해제 - 반드시 필요한 작업
    - 따라서 try/catch and finally 코드를 추천한다.
    

Lock 인터페이스 외에도 ReadWriteLock이라는 인터페이스가 존재한다.

이건 락을 두개 관리한다. 하나는 읽기 전용, 하나는 쓰기 전용이다.

- Read Lock : 여러 스레드가 동시 접근 가능(단, 쓰기 중일 때는 안된다.)
- Write Lock : 단 하나의 스레드만 획득 가능, 읽기 락도 모두 막힌다.

## Lock implements

### Reentrant Lock

```java
public class SharedObjectWithLock {
    //...
    ReentrantLock lock = new ReentrantLock();
    int counter = 0;

    public void perform() {
        lock.lock();
        try {
            // Critical section here
            count++;
        } finally {
            lock.unlock(); //반드시 락을 해제할 것 안그러면 deadlock이 발생한다.
        }
    }
    //...
}
```

```java
public void performTryLock(){
    //...
    boolean isLockAcquired = lock.tryLock(1, TimeUnit.SECONDS);
    
    if(isLockAcquired) {
        try {
            //Critical section here
        } finally {
            lock.unlock();
        }
    }
    //...
}
```

- 락을 1초동안 기다림
- 그 안에 락을 얻지 못하면 false 반환 → 작업 안함, 그냥 지나침
- 무한 대기 없이 유연하게 대응 가능

### Reentrant Read Write Lock

*ReentrantReadWriteLock* 인터페이스를 구현한 클래스

- Read Lock : 다른 스레드가 쓰기 락을 잡고 있거나 요청중이 아니면 여러 스레드가 동시에 읽기 락을 획득할 수 있다.
- Write Lock : 누구도 읽고 있지 않고, 누구도 쓰고 있지 않을 때에만 오직 하나의 스레드가 읽기 락을 획득할 수 있다.

```java
public class SynchronizedHashMapWithReadWriteLock {

    Map<String,String> syncHashMap = new HashMap<>();
    ReadWriteLock lock = new ReentrantReadWriteLock();
    // ...
    Lock writeLock = lock.writeLock();

    public void put(String key, String value) {
        try {
            writeLock.lock();
            syncHashMap.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
    ...
    public String remove(String key){
        try {
            writeLock.lock();
            return syncHashMap.remove(key);
        } finally {
            writeLock.unlock();
        }
    }
    //...
}
```

```java
Lock readLock = lock.readLock();
//...
public String get(String key){
    try {
        readLock.lock();
        return syncHashMap.get(key);
    } finally {
        readLock.unlock();
    }
}

public boolean containsKey(String key) {
    try {
        readLock.lock();
        return syncHashMap.containsKey(key);
    } finally {
        readLock.unlock();
    }
}
```

### Stamp Lock

Java 8에서 도입한 락 클래스

읽기 락과 쓰기 락 모두 지워

그런데 이 락은 lock()을 모두 호출하면 스탬프라는 Long 값을 반환한다. 이 값을 사용하면 나중에 락을 해제하거나 유효성 검사를 할 수 있다.

```java
public class StampedLockDemo {
    Map<String,String> map = new HashMap<>();
    private StampedLock lock = new StampedLock();

    public void put(String key, String value){
        long stamp = lock.writeLock();
        try {
            map.put(key, value);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public String get(String key) throws InterruptedException {
        long stamp = lock.readLock();
        try {
            return map.get(key);
        } finally {
            lock.unlockRead(stamp);
        }
    }
}

```

- 왜 long 값을 반환하는가?
    
    `StampedLock`은 내부적으로 **락의 상태를 숫자(stamp)로 표현**합니다. 이 값은 락을 획득할 때마다 **고유한 값**으로 증가하며, 이 스탬프를 통해 락을 해제할 때 정확히 어떤 락을 해제해야 하는지 식별할 수 있다.
    
    📌 예를 들어 설명하면
    
    ```java
    long stamp = lock.writeLock();
    ```
    
    - 이 시점에서 `stamp`는 어떤 쓰기 락이 획득되었는지 나타내는 고유 식별자
    - 이후 해제할 때도 이 값을 정확히 전달해야:
        
        ```java
        lock.unlockWrite(stamp);
        ```
        
        → 락이 제대로 해제된다
        
- 왜 그냥 boolean이나 void가 아닌가?
    
    다른 락 (예: `ReentrantLock`)은 락 자체의 상태만 관리하면 되지만,
    
    `StampedLock`은 다음과 같은 고급 기능을 지원한다.
    
    1. **낙관적 락 (`tryOptimisticRead()`)**
        - 실제로 락을 걸지 않고 일단 읽고 나서, 이 스탬프가 여전히 유효한지 확인한다.
        - 스탬프가 변하지 않았으면 데이터가 변경되지 않았다고 간주한다.
    2. **업그레이드 기능 (`tryConvertToWriteLock()`)**
        - 현재 보유한 스탬프를 기반으로 락의 종류를 변경할 수 있다.
    
    이처럼 락의 상태를 정밀하게 제어하고 확인해야 하기에, **스탬프 값을 리턴해서 관리**하게 된다.
    

Stamp Lock은 낙관적 락이라는 기능도 제공한다.

락을 굳이 걸 필요 없이 **읽고 난 후에 "쓰기 없었는지"만 확인**하면 대부분의 경우 정합성을 지키면서도 성능까지 챙길 수 있다.

```java
public String readWithOptimisticLock(String key) {
    long stamp = lock.tryOptimisticRead(); // 100
    String value = map.get(key); // alick

    if(!lock.validate(stamp)) { // 101로 바뀜
        stamp = lock.readLock(); // lock 드디어 획득
        try {
            return map.get(key); // bob
        } finally {
            lock.unlock(stamp);               
        }
    }
    return value; // bob
}
```

## Working With Conditions

Condition 클래스는 스레드가 락을 잡은 상태에서 어떤 조건이 만족될 때까지 기다릴 수 있는 기능을 제공한다.

즉 synchronized
