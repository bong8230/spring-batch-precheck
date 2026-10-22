package io.github.bong8230.precheck.settlement;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import io.github.bong8230.precheck.BaseDateValidator;

/**
 * 일별 정산 Job: 선행 파일 검증 → 계좌별 집계.
 *
 * 파라미터: baseDate(yyyyMMdd)
 */
@Configuration
public class DailySettlementJobConfig {

	public static final String JOB_NAME = "dailySettlementJob";

	@Bean
	public Job dailySettlementJob(JobRepository jobRepository, Step requiredFilePrecheckStep,
			Step dailyAggregateStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
			.validator(new BaseDateValidator())
			.start(requiredFilePrecheckStep)
			.next(dailyAggregateStep)
			.build();
	}

	@Bean
	public Step requiredFilePrecheckStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			SettlementMapper settlementMapper, PrecheckProperties properties) {
		return new StepBuilder("requiredFilePrecheckStep", jobRepository)
			.tasklet(new RequiredFilePrecheckTasklet(settlementMapper, properties.requiredFiles()), transactionManager)
			.build();
	}

	/** 같은 기준일자로 다시 돌려도 결과가 같도록 지우고 다시 만든다. */
	@Bean
	public Step dailyAggregateStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			SettlementMapper settlementMapper) {
		return new StepBuilder("dailyAggregateStep", jobRepository)
			.tasklet((contribution, chunkContext) -> {
				String baseDate = chunkContext.getStepContext().getStepExecution().getJobParameters()
					.getString(BaseDateValidator.BASE_DATE);
				settlementMapper.deleteSettlement(baseDate);
				int created = settlementMapper.insertSettlement(baseDate);
				contribution.incrementWriteCount(created);
				contribution.setExitStatus(ExitStatus.COMPLETED
					.addExitDescription("정산 " + created + "건 생성 (" + baseDate + ")"));
				return RepeatStatus.FINISHED;
			}, transactionManager)
			.build();
	}
}
