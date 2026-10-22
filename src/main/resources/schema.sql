-- 외부 기관에서 받은 파일의 수신 이력. 수신 Job이 적재를 마치면 한 줄 남긴다.
CREATE TABLE IF NOT EXISTS file_receive_log (
    file_code    VARCHAR(20) NOT NULL,
    base_date    CHAR(8)     NOT NULL,
    row_count    INT         NOT NULL,
    received_at  TIMESTAMP   NOT NULL,
    PRIMARY KEY (file_code, base_date)
);

-- 수신 파일 원본 데이터(스테이징)
CREATE TABLE IF NOT EXISTS received_tx (
    file_code   VARCHAR(20)   NOT NULL,
    base_date   CHAR(8)       NOT NULL,
    account_no  VARCHAR(20)   NOT NULL,
    amount      DECIMAL(15,0) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_received_tx ON received_tx (base_date, file_code);

-- 일별 계좌별 정산 결과
CREATE TABLE IF NOT EXISTS daily_settlement (
    base_date     CHAR(8)       NOT NULL,
    account_no    VARCHAR(20)   NOT NULL,
    paid_amount   DECIMAL(15,0) NOT NULL,
    cancel_amount DECIMAL(15,0) NOT NULL,
    net_amount    DECIMAL(15,0) NOT NULL,
    tx_count      INT           NOT NULL,
    PRIMARY KEY (base_date, account_no)
);
