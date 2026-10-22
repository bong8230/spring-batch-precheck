package io.github.bong8230.precheck.settlement;

import java.util.List;

/** 선행 파일이 수신되지 않아 정산을 시작할 수 없을 때 던진다. 메시지가 Job 실패 사유로 남는다. */
public class RequiredFileMissingException extends RuntimeException {

	private final List<String> missingFiles;

	public RequiredFileMissingException(String baseDate, List<String> missingFiles) {
		super("미수신 선행 파일 " + missingFiles + " (baseDate=" + baseDate + ")");
		this.missingFiles = List.copyOf(missingFiles);
	}

	public List<String> getMissingFiles() {
		return missingFiles;
	}
}
