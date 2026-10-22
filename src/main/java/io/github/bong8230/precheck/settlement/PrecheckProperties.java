package io.github.bong8230.precheck.settlement;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 정산 전에 수신이 끝나 있어야 하는 파일 코드 목록 */
@ConfigurationProperties("precheck")
public record PrecheckProperties(List<String> requiredFiles) {

	public PrecheckProperties {
		requiredFiles = requiredFiles == null ? List.of() : List.copyOf(requiredFiles);
	}
}
