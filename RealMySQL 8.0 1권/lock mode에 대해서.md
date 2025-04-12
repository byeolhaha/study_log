lock mode를 설정한다는 말이 격리 수준을 설정한다는 말이었다.

격리 수준이란, 트랜잭션들이 공통된 자원에 대해서 어떻게 바라볼 것인지를 결정하는 것이다.
uncommited read, commited read, repeatable read, serializable read 총 4가지가 있다.
나는 mysql innodb 스토리지 엔진 관점에서 말을 할 것이고 이에 따라 repeatable read에서 팬텀리드가 왜 mysql에서는 등장하지 않는지 그것도 함께 살펴보고자 한다.

- uncommited read
  ![image](https://github.com/user-attachments/assets/0b1aaa02-032f-42fb-9334-db533f893a0e)
  결과가 있다가 사라짐 commit되지 않은 데이터를 읽는다.

  session 1
  ```
  SET autocommit = 0;
  SET session TRANSACTION ISOLATION LEVEL read uncommitted; #1
  start transaction; #2
  insert into users(name) values ('홍길동'); #3
  rollback;#8
  ```
  session 2
  ```
  set session transaction isolation level uncommitted read;#4
  SELECT @@transaction_isolation;#5   
  START TRANSACTION; #7
  select * from users where id = 22; #7 결과 있음
  select * from users where id = 22; #9 결과 없음
  commit;
  ```
- commited read
  ![image](https://github.com/user-attachments/assets/e7d4b4df-70c8-4ec5-bcd3-6684e0c272b9)
  commit된 데이터를 읽지만
  ![image](https://github.com/user-attachments/assets/256cc175-95fc-4cb3-b67f-7f800a7b118c)
  반복된 읽기의 경우 결과가 없다가 나타날 수 있다.

  session 1
  ```
  SET autocommit = 0;
  SET session TRANSACTION ISOLATION LEVEL read committed; #1
  start transaction; #2
  insert into users(name) values ('홍길동'); #3
  rollback;#8
  ```
  session 2
  ```
  set session transaction isolation level committed read;#4
  SELECT @@transaction_isolation;#5   
  START TRANSACTION; #7
  select * from users where id = 22; #7 결과 없음
  select * from users where id = 22; #9 결과 없음
  commit;
  ```
  그러나 아래의 경우 결과가 다시 나타남

  session 1
  ```
  SET autocommit = 0;
  SET session TRANSACTION ISOLATION LEVEL read committed; #1
  start transaction; #2
  insert into users(name) values ('홍길동'); #3
  commit;#8
  ```
  session 2
  ```
  set session transaction isolation level committed read;#4
  SELECT @@transaction_isolation;#5   
  START TRANSACTION; #7
  select * from users where id = 22; #7 결과 없음
  select * from users where id = 22; #9 결과 있음
  commit;
  ```
- repeatable read
  REPEATABLE READ에서는, "트랜잭션이 시작될 때 이미 커밋된 것"만 읽을 수 있다.
  **트랜잭션 시작 후에 커밋된 것은 아무리 trx_id가 낮아도 절대 안 보인다!!!**
  **즉 내 트랜잭션 아이디보다 작은 트랜잭션이 커밋한 내용만 보인다.**
  따라서 trx_id가 큰 것은 더더욱 안보임 => 따라서 팬텀리드 발생하지 않음

  session 1
  ```
  SET autocommit = 0;
  SET session TRANSACTION ISOLATION LEVEL read committed; #1
  start transaction; #2
  insert into users(name) values ('홍길동'); #3
  commit;#8
  ```
  session 2
  ```
  set session transaction isolation level committed read;#4
  SELECT @@transaction_isolation;#5   
  START TRANSACTION; #7
  select * from users where id = 22; #7 결과 없음
  select * from users where id = 22; #9 결과 없음
  commit;
  ```
 
  하지만 lock을 사용하는 경우는 언두로그를 사용하지 않고 일반 RDBMS는 테이블 자체를 참조하기 때문에 팬텀리드가 발생할 수 있다.
  그러나 mysql의 경우에는 갭락이 있어서 랜텀리드가 발생하지 않는다.

  session 1
  ```
  set session transaction isolation level repeatable read; #1
  SELECT @@transaction_isolation;#2   
  start transaction; #3
  select * from users where id >= 22 for update; #4 # 1건
  select * from users where id >= 22 for update; #9 # 1건
  commit; # 10
  ```
  session 2
  ```
  set session transaction isolation level repeatable read;#5
  SELECT @@transaction_isolation; #6
  START TRANSACTION; #7
  insert into users (name) values ('김별'); #8
  commit; # 11
  ```
  팬텀리드가 발생하지 않는다 모두 결과가 1건이다. 
- serializable read
  가장 엄격한 격리수준으로 순수한 읽기에서조차 갭락과 넥스트 키락이 걸려서 추가, 수정, 삭제가 되지 않는다.

  session 1
  ```
  set session transaction isolation level serializable;#1
  SELECT @@transaction_isolation;  #2
  start transaction; #3
  select * from users where name = '홍길동'; #4
  ```
  session 2
  ``
  set session transaction isolation level serializable;#5
  SELECT @@transaction_isolation;  #6
  START TRANSACTION; #7
  INSERT INTO users (name) VALUES ('홍길동'); # 넥스트 키락이 걸려서 아무런 작업을 하지 못한 채 커넥션이 끊긴다. #8
  ```
  아래 결과를 보면 로딩이 걸리고 결국 커넥션이 끊긴다.
![image](https://github.com/user-attachments/assets/85b323c4-e93d-40ef-9abb-6c9fdd9e85f0)
