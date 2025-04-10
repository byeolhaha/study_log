lock mode를 설정한다는 말이 격리 수준을 설정한다는 말이었다.

격리 수준이란, 트랜잭션들이 공통된 자원에 대해서 어떻게 바라볼 것인지를 결정하는 것이다.
uncommited read, commited read, repeatable read, serializable read 총 4가지가 있다.
나는 mysql innodb 스토리지 엔진 관점에서 말을 할 것이고 이에 따라 repeatable read에서 팬텀리드가 왜 mysql에서는 등장하지 않는지 그것도 함께 살펴보고자 한다.

- uncommited read
  ![image](https://github.com/user-attachments/assets/0b1aaa02-032f-42fb-9334-db533f893a0e)
  결과가 있다가 사라짐 commit되지 않은 데이터를 읽는다.
- commited read
  ![image](https://github.com/user-attachments/assets/e7d4b4df-70c8-4ec5-bcd3-6684e0c272b9)
  commit된 데이터를 읽지만
  ![image](https://github.com/user-attachments/assets/256cc175-95fc-4cb3-b67f-7f800a7b118c)
  반복된 읽기의 경우 결과가 없다가 나타날 수 있다.
- repeatable read
  REPEATABLE READ에서는, "트랜잭션이 시작될 때 이미 커밋된 것"만 읽을 수 있다.
  트랜잭션 시작 후에 커밋된 것은 아무리 trx_id가 낮아도 절대 안 보인다!!!
  따라서 trx_id가 큰 것은 더더욱 안보임 => 따라서 팬텀리드 발생하지 않음
  팬텀리드가 발생하는 lock이 있는 쓰기를 했을 때인데 그 이유는 mvcc가 아닌 테이블 자체를 바라보기 때문에 그러나 mysql innodb 스토리지 엔진은 레코드락이 걸려서 괜찮다. 

  
- serializable read
