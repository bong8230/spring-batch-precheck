package io.github.bong8230.precheck.receive;

import java.math.BigDecimal;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.batch.MyBatisBatchItemWriter;
import org.mybatis.spring.batch.builder.MyBatisBatchItemWriterBuilder;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersValidator;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.CompositeJobParametersValidator;
import org.springframework.batch.core.job.DefaultJobParametersValidator;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.bong8230.precheck.BaseDateValidator;

/**
 * 외부 기관에서 받은 CSV 파일을 스테이징 테이블에 적재하고 수신 이력을 남긴다.
 * 실무의 FEP 수신 → 적재 구간을 단순화한 Job이다.
 *
 * 파라미터: fileCode, baseDate(yyyyMMdd), inputFile
 */
@Configuration
public class ReceiveFileJobConfig {

	public static final String JOB_NAME = "receiveFileJob";

	private static final int CHUNK_SIZE = 100;

	@Bean
	public Job receiveFileJob(JobRepository jobRepository, Step clearReceivedStep, Step loadReceivedStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
			.validator(receiveParametersValidator())
			.start(clearReceivedStep)
			.next(loadReceivedStep)
			.build();
	}

	private JobParametersValidator receiveParametersValidator() {
		CompositeJobParametersValidator validator = new CompositeJobParametersValidator();
		validator.setValidators(List.of(
				new DefaultJobParametersValidator(new String[] { "fileCode", "baseDate", "inputFile" }, new String[0]),
				new BaseDateValidator()));
		return validator;
	}

	/** 같은 파일을 다시 받아도 중복 적재되지 않도록 이전 적재분을 먼저 지운다. */
	@Bean
	public Step clearReceivedStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			ReceiveMapper receiveMapper) {
		return new StepBuilder("clearReceivedStep", jobRepository)
			.tasklet((contribution, chunkContext) -> {
				var params = chunkContext.getStepContext().getJobParameters();
				receiveMapper.deleteReceived((String) params.get("fileCode"), (String) params.get("baseDate"));
				return RepeatStatus.FINISHED;
			}, transactionManager)
			.allowStartIfComplete(true)
			.build();
	}

	@Bean
	public Step loadReceivedStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			FlatFileItemReader<TxLine> txLineReader, ItemProcessor<TxLine, ReceivedTx> txLineProcessor,
			MyBatisBatchItemWriter<ReceivedTx> receivedTxWriter, ReceiveMapper receiveMapper) {
		return new StepBuilder("loadReceivedStep", jobRepository)
			.<TxLine, ReceivedTx>chunk(CHUNK_SIZE, transactionManager)
			.reader(txLineReader)
			.processor(txLineProcessor)
			.writer(receivedTxWriter)
			.listener(receiveLogListener(receiveMapper))
			.build();
	}

	@Bean
	@StepScope
	public FlatFileItemReader<TxLine> txLineReader(@Value("#{jobParameters['inputFile']}") String inputFile) {
		return new FlatFileItemReaderBuilder<TxLine>()
			.name("txLineReader")
			.resource(new FileSystemResource(inputFile))
			.linesToSkip(1)
			.delimited()
			.names("accountNo", "amount")
			.targetType(TxLine.class)
			.build();
	}

	@Bean
	@StepScope
	public ItemProcessor<TxLine, ReceivedTx> txLineProcessor(@Value("#{jobParameters['fileCode']}") String fileCode,
			@Value("#{jobParameters['baseDate']}") String baseDate) {
		return line -> {
			if (line.accountNo() == null || line.accountNo().isBlank()) {
				throw new IllegalArgumentException("계좌번호가 비어 있습니다: " + line);
			}
			if (line.amount() == null || line.amount().compareTo(BigDecimal.ZERO) <= 0) {
				throw new IllegalArgumentException("금액은 0보다 커야 합니다: " + line);
			}
			return new ReceivedTx(fileCode, baseDate, line.accountNo().trim(), line.amount());
		};
	}

	@Bean
	public MyBatisBatchItemWriter<ReceivedTx> receivedTxWriter(SqlSessionFactory sqlSessionFactory) {
		return new MyBatisBatchItemWriterBuilder<ReceivedTx>()
			.sqlSessionFactory(sqlSessionFactory)
			.statementId("io.github.bong8230.precheck.receive.ReceiveMapper.insertReceived")
			.build();
	}

	/** 적재가 성공했을 때만 수신 이력을 남긴다. 정산 Job은 이 이력으로 선행 파일 수신 여부를 판단한다. */
	private StepExecutionListener receiveLogListener(ReceiveMapper receiveMapper) {
		return new StepExecutionListener() {
			@Override
			public ExitStatus afterStep(StepExecution stepExecution) {
				if (ExitStatus.COMPLETED.equals(stepExecution.getExitStatus())) {
					var params = stepExecution.getJobParameters();
					receiveMapper.mergeReceiveLog(params.getString("fileCode"), params.getString("baseDate"),
							stepExecution.getWriteCount());
				}
				return stepExecution.getExitStatus();
			}
		};
	}
}
