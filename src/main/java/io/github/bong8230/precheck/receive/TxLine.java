package io.github.bong8230.precheck.receive;

import java.math.BigDecimal;

/** 수신 파일(CSV)의 한 줄: account_no,amount */
public record TxLine(String accountNo, BigDecimal amount) {
}
