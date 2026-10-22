package io.github.bong8230.precheck.receive;

import java.math.BigDecimal;

/** 수신 파일 한 줄을 스테이징 테이블에 적재하기 위한 값 */
public record ReceivedTx(String fileCode, String baseDate, String accountNo, BigDecimal amount) {
}
