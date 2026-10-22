package io.github.bong8230.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
		"spring.batch.job.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:precheck;DB_CLOSE_DELAY=-1" })
class BatchFlowTest {

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	@Qualifier("receiveFileJob")
	private Job receiveFileJob;

	@Autowired
	@Qualifier("dailySettlementJob")
	private Job dailySettlementJob;

	@Autowired
	private JdbcTemplate jdbc;

	@TempDir
	private Path inbox;

	@BeforeEach
	void clearBusinessTables() {
		jdbc.update("DELETE FROM daily_settlement");
		jdbc.update("DELETE FROM received_tx");
		jdbc.update("DELETE FROM file_receive_log");
	}

	@Test
	void 선행_파일이_모두_수신되면_계좌별로_정산한다() throws Exception {
		String baseDate = "20261001";
		receive("PAY", baseDate, "A-001,10000\nA-001,5000\nB-002,7000\n");
		receive("CANCEL", baseDate, "A-001,3000\n");

		JobExecution execution = settle(baseDate);

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(netAmount(baseDate, "A-001")).isEqualByComparingTo("12000");
		assertThat(netAmount(baseDate, "B-002")).isEqualByComparingTo("7000");
		assertThat(step(execution, "dailyAggregateStep").getExitStatus().getExitDescription())
			.contains("정산 2건 생성");
	}

	@Test
	void 선행_파일이_없으면_실패하고_파일이_들어온_뒤_같은_인스턴스로_재시작한다() throws Exception {
		String baseDate = "20261002";
		receive("PAY", baseDate, "A-001,10000\n");

		JobExecution first = settle(baseDate);

		assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(step(first, "requiredFilePrecheckStep").getExitStatus().getExitDescription())
			.contains("미수신 선행 파일 [CANCEL]");
		assertThat(first.getStepExecutions()).extracting(StepExecution::getStepName)
			.containsExactly("requiredFilePrecheckStep");

		receive("CANCEL", baseDate, "A-001,1000\n");
		JobExecution restarted = settle(baseDate);

		assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(restarted.getJobInstance().getInstanceId()).isEqualTo(first.getJobInstance().getInstanceId());
		assertThat(netAmount(baseDate, "A-001")).isEqualByComparingTo("9000");
	}

	@Test
	void 존재하지_않는_날짜는_Job을_시작하지_않는다() {
		JobParameters params = new JobParametersBuilder().addString("baseDate", "20261332").toJobParameters();

		assertThatThrownBy(() -> jobLauncher.run(dailySettlementJob, params))
			.isInstanceOf(JobParametersInvalidException.class)
			.hasMessageContaining("20261332");
	}

	@Test
	void 같은_파일을_다시_받아도_중복_적재되지_않는다() throws Exception {
		String baseDate = "20261003";
		receive("PAY", baseDate, "A-001,10000\nB-002,7000\n");
		receive("PAY", baseDate, "A-001,10000\nB-002,7000\n");

		Integer rows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM received_tx WHERE file_code = 'PAY' AND base_date = ?", Integer.class, baseDate);
		Integer logged = jdbc.queryForObject(
				"SELECT row_count FROM file_receive_log WHERE file_code = 'PAY' AND base_date = ?", Integer.class,
				baseDate);

		assertThat(rows).isEqualTo(2);
		assertThat(logged).isEqualTo(2);
	}

	@Test
	void 금액이_잘못된_줄이_있으면_수신_이력을_남기지_않는다() throws Exception {
		String baseDate = "20261004";

		JobExecution execution = receive("PAY", baseDate, "A-001,10000\nB-002,-500\n");

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
		Integer logged = jdbc.queryForObject("SELECT COUNT(*) FROM file_receive_log WHERE base_date = ?",
				Integer.class, baseDate);
		assertThat(logged).isZero();
	}

	private JobExecution receive(String fileCode, String baseDate, String body) throws Exception {
		Path file = inbox.resolve(fileCode + "_" + baseDate + "_" + System.nanoTime() + ".csv");
		write(file, "account_no,amount\n" + body);
		JobParameters params = new JobParametersBuilder()
			.addString("fileCode", fileCode)
			.addString("baseDate", baseDate)
			.addString("inputFile", file.toString())
			.toJobParameters();
		return jobLauncher.run(receiveFileJob, params);
	}

	private JobExecution settle(String baseDate) throws Exception {
		return jobLauncher.run(dailySettlementJob,
				new JobParametersBuilder().addString("baseDate", baseDate).toJobParameters());
	}

	private BigDecimal netAmount(String baseDate, String accountNo) {
		Map<String, Object> row = jdbc.queryForMap(
				"SELECT net_amount FROM daily_settlement WHERE base_date = ? AND account_no = ?", baseDate, accountNo);
		return (BigDecimal) row.get("NET_AMOUNT");
	}

	private static StepExecution step(JobExecution execution, String name) {
		return execution.getStepExecutions().stream()
			.filter(step -> step.getStepName().equals(name))
			.findFirst()
			.orElseThrow();
	}

	private static void write(Path file, String content) throws IOException {
		Files.writeString(file, content);
	}
}
