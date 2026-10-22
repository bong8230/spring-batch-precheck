package io.github.bong8230.precheck.settlement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

import io.github.bong8230.precheck.BaseDateValidator;

/**
 * 정산에 필요한 선행 파일이 모두 수신됐는지 확인한다.
 * 하나라도 없으면 빠진 파일을 메시지에 남기고 Step을 실패시켜 뒤 Step이 돌지 않게 한다.
 * 파일이 들어온 뒤 같은 baseDate로 다시 실행하면 실패한 지점(이 Step)부터 재시작된다.
 */
public class RequiredFilePrecheckTasklet implements Tasklet {

	private final SettlementMapper settlementMapper;

	private final List<String> requiredFiles;

	public RequiredFilePrecheckTasklet(SettlementMapper settlementMapper, List<String> requiredFiles) {
		this.settlementMapper = settlementMapper;
		this.requiredFiles = requiredFiles;
	}

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		String baseDate = chunkContext.getStepContext().getStepExecution().getJobParameters()
			.getString(BaseDateValidator.BASE_DATE);

		Set<String> received = new HashSet<>(settlementMapper.findReceivedFileCodes(baseDate));
		List<String> missing = requiredFiles.stream().filter(code -> !received.contains(code)).toList();

		if (!missing.isEmpty()) {
			throw new RequiredFileMissingException(baseDate, missing);
		}

		contribution.setExitStatus(ExitStatus.COMPLETED
			.addExitDescription("선행 파일 " + requiredFiles.size() + "종 수신 확인 (" + baseDate + ")"));
		return RepeatStatus.FINISHED;
	}
}
