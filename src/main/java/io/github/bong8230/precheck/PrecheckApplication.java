package io.github.bong8230.precheck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PrecheckApplication {

	/**
	 * 외부 스케줄러(JobPass, cron 등)가 실패를 감지할 수 있도록
	 * Job 결과를 프로세스 종료 코드로 돌려준다. (COMPLETED = 0, 그 외 = 1 이상)
	 */
	public static void main(String[] args) {
		System.exit(SpringApplication.exit(SpringApplication.run(PrecheckApplication.class, args)));
	}
}
