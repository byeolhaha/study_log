## DB 스키마 생성 및 이해 
1. 스프링 배치 메타 데이터
   - 스프링 배치의 실행 및 관리를 위한 목적으로 여러 도메인들(Job, Step, JobParameters..)의 정보들을 저장, 업데이트, 조회할 수 있는 스키마 제공
   - 과거, 현재의 실행에 대한 세세한 정보, 실행에 대한 성공과 실패 여부 등을 일목요연하게 관리함으로서 배치운용에 있어 리스크 발생시 빠른 대처 가능
   - DB와 연동할 경우 필수 적으로 메타 테이블이 생성되어야 한다.
2. DB 스키마 제공
   - 위치 : /org/springframework/batch/core/schema-*.sql 문에서 찾아볼 수 있다.
   - *은 DB 유형임
3. 스키마 생성 설정
   - 수동 설정 - 앞서 /org/springframework/batch/core/schema-*.sql 에 있는 쿼리문들 그대로 복사해서 직접 실행
   - 자동 생성 : yml이나 properties에 spring.batch.jdbc.initialize-schema 항목을 설정
     - always : 서버를 띄울 때마다 스크립트 실행, rdbms 설정이 되어 있으면 내장 DB 보다 우선적으로 실행
     - embedded : 내장 DB일 때마 스키마가 자동 설정, 기본값
     - never : 스크립트 실행 안함, 운영에서는 수동으로 하고 이 설정을 never로 해야한다.

![image](https://github.com/user-attachments/assets/c03434d8-69b6-46ba-a57c-29f75baa929d)

## 테이블 
- Job 관련 테이블
  - BATCH_JOB_INSTANCE
    - Job이 실행될 때 JobInstance 정보가 저장되며 job_name과 job_key를 키로 하여 하나의 데이터가 저장된다.
    - job key는 job name과 job parameter의 해시값이다.
    - 동일한 job_name과 job_key로 중복 저장될 수 없다.
  - BATCH_JOB_EXECUTION
    - job의 실행 정보가 저장되며 job 생성, 시작, 종료 시간, 실행 상태, 메시지 등을 관리
  - BATCH_JOB_EXECUTION_PARAMS
    - job과 함께 실행되는 job parameter 정보를 저장
  - BATCH_JOB_EXECUTION_CONTEXT
    - job의 실행동안 여러가지 상태 정보, 공유 데이터를 직렬화해서 저장
    - step 간 서로 공유 가능하다. 
- Step 관련 테이블
  - BATCH_STEP_EXECUTION
    - step의 실행정보가 저장되며 생성, 시작, 종료 시간, 실행 상태, 메시지 등을 관리한다.
  - BATCH_STEP_EXECUTION_CONTEST
    - step의 실행 동안 여러가지 상태정보, 공유 데이터를 직렬화해서 저장
    - step 별로 저장되면 step간 서로 공유할 수 없다.

```sql
CREATE TABLE BATCH_JOB_INSTANCE  (
    JOB_INSTANCE_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT ,
    JOB_NAME VARCHAR(100) NOT NULL,
    JOB_KEY VARCHAR(32) NOT NULL,
    constraint JOB_INST_UN unique (JOB_NAME, JOB_KEY)
) ENGINE=InnoDB;
```
- JOB_INSTANCE_ID : 고유하게 식별할 수 있는 기본키
- VERSION : 업데이트 될 때마다 1씩 증가
- JOB_NAME : Job을 구성할 때 부여하는 Job의 이름
- JOB_KEY : Job_name과 job parameter를 합쳐 해싱한 값을 저장

```sql
CREATE TABLE BATCH_JOB_EXECUTION  (
    JOB_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT  ,
    JOB_INSTANCE_ID BIGINT NOT NULL,
    CREATE_TIME DATETIME(6) NOT NULL,
    START_TIME DATETIME(6) DEFAULT NULL ,
    END_TIME DATETIME(6) DEFAULT NULL ,
    STATUS VARCHAR(10) ,
    EXIT_CODE VARCHAR(2500) ,
    EXIT_MESSAGE VARCHAR(2500) ,
    LAST_UPDATED DATETIME(6),
    constraint JOB_INST_EXEC_FK foreign key (JOB_INSTANCE_ID)
    references BATCH_JOB_INSTANCE(JOB_INSTANCE_ID)
) ENGINE=InnoDB;
```
- JOB_EXECUTION_ID : BATCH_JOB_EXECUTION을 고유하게 식별할 수 있는 기본키, BATCH_JOB_EXECUTION : BATCH_JOB_INSTANCE = N : 1
- VERSION : 마찬가지로 업데이트될 때마다 1씩 증가
- JOB_INSTANCE_ID: BATCH_JOB_INSTANCE에 대한 외래키
- CREATE_TIME: 실행이 생성된 시점
- START_TIME: 실행이 시작된 시점
- END_TIME: 실행이 종료된 시점, Job 실행 도중 오류가 발생해서 Job이 중단된 경우 해당 값이 저장되지 않아 null일 수 있다.
- STATUS : 실행 상태를 저장(COMPLETED, FAILED, STOPPED..)
- EXIT_CODE : 실행 종료 코드를 저장(COMPLETED, FAILED..)
- EXIT_MESSAGE: STATUS가 실패인 경우 실패 원인 등의 내용을 저장
- LAST_UPDATED: 마지막 실행 시점

```sql
CREATE TABLE BATCH_JOB_EXECUTION_PARAMS  (
    JOB_EXECUTION_ID BIGINT NOT NULL ,
    PARAMETER_NAME VARCHAR(100) NOT NULL ,
    PARAMETER_TYPE VARCHAR(100) NOT NULL ,
    PARAMETER_VALUE VARCHAR(2500) ,
    IDENTIFYING CHAR(1) NOT NULL ,
    constraint JOB_EXEC_PARAMS_FK foreign key (JOB_EXECUTION_ID)
    references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;
```
- JOB_EXECUTION_ID : Job Execution 식별키, 외래키,
- BATCH_JOB_EXECUTION_PARAMS : BATCH_JOB_EXECUTION = N:1
- TYPE_CD : STRING, LONG, DATE, DOUBLE 타입 정보
- PARAMETER_TYPE : 파라미터 키 값
- PARAMETER_VALUE : 파라미터 값
- PARAMETER_TYPE : 파라미터 타입 (String, Long, Double, Date)
- IDENTIFYING : 식별 여부(TRUE, FALSE)

```sql
CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT  (
    JOB_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT ,
    constraint JOB_EXEC_CTX_FK foreign key (JOB_EXECUTION_ID)
    references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;
```
- JOB_EXECUTION_ID : JOB_EXECUTIOM 식별키, 외래키 , BATCH_JOB_EXECUTION_CONTEXT : BATCH_JOB_EXECUTION = 1:1
- SHORT_CONTEXT: Job의 실행 상태정보, 공유데이터 등의 정보를 뮨자열로 저장 -> BASE64로 인코딩된 값
- SERIALIZED_CONTEXT : 직렬화된 전체 컨텍스트

```sq;
CREATE TABLE BATCH_STEP_EXECUTION  (
    STEP_EXECUTION_ID BIGINT  NOT NULL PRIMARY KEY ,
    VERSION BIGINT NOT NULL,
    STEP_NAME VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID BIGINT NOT NULL,
    CREATE_TIME DATETIME(6) NOT NULL,
    START_TIME DATETIME(6) DEFAULT NULL ,
    END_TIME DATETIME(6) DEFAULT NULL ,
    STATUS VARCHAR(10) ,
    COMMIT_COUNT BIGINT ,
    READ_COUNT BIGINT ,
    FILTER_COUNT BIGINT ,
    WRITE_COUNT BIGINT ,
    READ_SKIP_COUNT BIGINT ,
    WRITE_SKIP_COUNT BIGINT ,
    PROCESS_SKIP_COUNT BIGINT ,
    ROLLBACK_COUNT BIGINT ,
    EXIT_CODE VARCHAR(2500) ,
    EXIT_MESSAGE VARCHAR(2500) ,
    LAST_UPDATED DATETIME(6),
    constraint JOB_EXEC_STEP_FK foreign key (JOB_EXECUTION_ID)
    references BATCH_JOB_EXECUTION(JOB_EXECUTION_ID)
) ENGINE=InnoDB;
```
- STEP_EXECUTION_ID : step의 실행정보를 고유하게 식별할 수 있는 기본키
- VERSION : 업데이트 될 때마다 1씩 증가
- STEP_NAME: Step을 구성할 때 부여하는 Step 이름
- JOB_EXECUTION_ID : JOB_EXECUTION의 기본키, 외래키 , BATCH_STEP_EXECUTION : BATCH_JOB_EXECUTION = N:1
- START_TIME : 실행 시작 시간
- END_TIME: 실행 종료 시간
- STATUS : 실행 상태를 저장 (COMPLETED, FAILED, STOPPED)
- COMMIT_COUNT : 트랜잭션 당 커밋되는 수를 기록
- READ_COUNT : 실행 시점에 Read한 Item 수를 기록
- FILTER_COUNT : 실행 도중 필터링된 Item 수를 기록
- WRITE_COUNT: 실행 도중 저장되고 커밋된 Item 수를 기록
- READ_SKIP_COUNT : 실행 도중 Read가 Skip된 Item 수를 기록
- WRITE_SKIP_COUNT : 실행 도중 Write가 Skip된 Item 수를 기록
- PROCESS_SKIP_COUNT : 실행 도중 Process가 Skip된 Item 수를 기록
- ROLLBACK_COUNT : 실행 도중 rollback이 일어난 수를 기록
- EXIT_CODE : 실행 종료 코드를 저장(COMPLETED, FAILED..)
- EXIT_MESSAGE : status가 실패일 경우 실패 원인 등의 내용을 저장
- LAST_UPDATED : 마지막 실행 시점

```sql
CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT  (
    STEP_EXECUTION_ID BIGINT NOT NULL PRIMARY KEY,
    SHORT_CONTEXT VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT ,
    constraint STEP_EXEC_CTX_FK foreign key (STEP_EXECUTION_ID)
    references BATCH_STEP_EXECUTION(STEP_EXECUTION_ID)
) ENGINE=InnoDB;
```
- STEP_EXECUTION_ID : step execution 식별키이자 이 테이블의 외래키 , BATCH_STEP_EXECUTION : BATCH_STEP_EXECUTION_CONTEXT = 1:1
- SHORT_CONTEXT : Job의 실행 상태 정보, 공유데이터 등의 정보를 문자열로 저장
- SERIALIZED_CONTEST : 직렬화된 전체 컨텍스트
