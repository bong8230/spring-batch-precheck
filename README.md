# spring-batch-precheck

외부 기관에서 받은 파일을 적재하고, **선행 파일이 모두 수신됐는지 먼저 검증한 뒤** 일별 정산을 실행하는 Spring Batch 예제입니다.

> 실무에서 수작업으로 돌리던 배치 19종을 Spring Batch Job으로 옮기며 쓴 설계(선행 파일 검증, 실패 사유 기록, 재실행, 외부 스케줄러 연계)를
> **가상 데이터로 처음부터 다시 구현한 공개용 예제**입니다. 회사 코드, 테이블, 업무 규칙은 포함하지 않습니다.

![ci](https://github.com/bong8230/spring-batch-precheck/actions/workflows/ci.yml/badge.svg)

## 기술 스택

Java 17 · Spring Boot 3.5 · Spring Batch 5 · MyBatis 3 · H2 · JUnit 5 · Gradle

## Job 구성

```
receiveFileJob (파일마다 1회)              dailySettlementJob (기준일자마다 1회)
┌──────────────────────────┐              ┌──────────────────────────────────┐
│ clearReceivedStep         │              │ requiredFilePrecheckStep          │
│  같은 파일 이전 적재분 삭제 │              │  필요한 파일(PAY, CANCEL)이        │
├──────────────────────────┤   수신 이력   │  baseDate로 수신됐는지 확인        │
│ loadReceivedStep (chunk)  │ ───────────▶ │  없으면 빠진 파일을 남기고 실패     │
│  CSV → 검증 → MyBatis 적재 │ file_receive │                                   │
│  성공 시 수신 이력 기록     │     _log     ├──────────────────────────────────┤
└──────────────────────────┘              │ dailyAggregateStep                │
                                          │  계좌별 입금 − 취소 집계 (재실행 안전) │
                                          └──────────────────────────────────┘
```

| 설계 포인트 | 구현 |
| --- | --- |
| 잘못된 기준일자 차단 | `BaseDateValidator`가 `yyyyMMdd` 실제 날짜인지 Job 시작 전에 검사 |
| 선행 파일 검증 | `RequiredFilePrecheckTasklet`이 수신 이력과 `precheck.required-files`를 비교, 빠진 파일 목록을 실패 사유로 남김 |
| 재시작 | 파일이 들어온 뒤 같은 `baseDate`로 다시 실행하면 **같은 JobInstance**가 실패한 Step부터 재시작 |
| 재실행 안전 | 수신은 이전 적재분 삭제 후 적재, 정산은 삭제 후 재집계라 몇 번 돌려도 결과가 같음 |
| 대량 적재 | `FlatFileItemReader` → `ItemProcessor`(값 검증) → `MyBatisBatchItemWriter`, chunk 100건 |
| 외부 스케줄러 연계 | Job 결과를 프로세스 종료 코드로 반환해 JobPass·cron 등이 성공/실패를 판단 |

## 실행

JDK 17이 필요합니다.

```sh
./gradlew bootJar

# 1) 파일 수신
java -jar build/libs/spring-batch-precheck-0.1.0.jar --spring.batch.job.name=receiveFileJob \
  fileCode=PAY baseDate=20261022 inputFile=sample-data/PAY_20261022.csv

# 2) 정산 → CANCEL 파일이 아직 없으므로 실패 (종료 코드 ≠ 0)
java -jar build/libs/spring-batch-precheck-0.1.0.jar --spring.batch.job.name=dailySettlementJob baseDate=20261022

# 3) CANCEL 파일 수신 후 같은 명령으로 다시 실행 → 실패한 Step부터 재시작해 완료
java -jar build/libs/spring-batch-precheck-0.1.0.jar --spring.batch.job.name=receiveFileJob \
  fileCode=CANCEL baseDate=20261022 inputFile=sample-data/CANCEL_20261022.csv
java -jar build/libs/spring-batch-precheck-0.1.0.jar --spring.batch.job.name=dailySettlementJob baseDate=20261022
```

데이터는 `./data/precheck.mv.db`(H2 파일 DB)에 남습니다.

## 테스트

```sh
./gradlew test
```

| 시나리오 | 확인 내용 |
| --- | --- |
| 선행 파일이 모두 있으면 정산 | 계좌별 순입금액, 생성 건수 메시지 |
| 선행 파일이 없으면 실패 후 재시작 | 실패 사유에 빠진 파일, 집계 Step 미실행, 파일 수신 후 같은 JobInstance로 완료 |
| 존재하지 않는 날짜 | Job이 시작되지 않음 |
| 같은 파일 재수신 | 중복 적재 없음, 수신 이력 건수 갱신 |
| 금액이 잘못된 줄 | 수신 Job 실패, 수신 이력 미기록(정산 선행 검증에서 걸러짐) |
