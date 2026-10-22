package io.github.bong8230.precheck;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.JobParametersValidator;

/**
 * baseDate 파라미터가 실제로 존재하는 yyyyMMdd 날짜인지 Job 시작 전에 확인한다.
 * 잘못된 기준일자로 Job 인스턴스가 만들어지는 것을 막는다.
 */
public class BaseDateValidator implements JobParametersValidator {

	public static final String BASE_DATE = "baseDate";

	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

	@Override
	public void validate(JobParameters parameters) throws JobParametersInvalidException {
		String baseDate = parameters == null ? null : parameters.getString(BASE_DATE);
		if (baseDate == null || baseDate.isBlank()) {
			throw new JobParametersInvalidException("baseDate 파라미터가 필요합니다. (예: baseDate=20261022)");
		}
		try {
			LocalDate.parse(baseDate, FORMAT);
		}
		catch (DateTimeParseException ex) {
			throw new JobParametersInvalidException("baseDate는 yyyyMMdd 형식의 실제 날짜여야 합니다: " + baseDate);
		}
	}
}
