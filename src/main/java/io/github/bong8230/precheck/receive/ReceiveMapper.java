package io.github.bong8230.precheck.receive;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReceiveMapper {

	/** 같은 파일·기준일자를 다시 수신할 때 이전 적재분을 지운다. */
	int deleteReceived(@Param("fileCode") String fileCode, @Param("baseDate") String baseDate);

	/** 적재가 끝난 파일의 수신 이력을 남긴다. 재수신이면 건수와 시각을 갱신한다. */
	int mergeReceiveLog(@Param("fileCode") String fileCode, @Param("baseDate") String baseDate,
			@Param("rowCount") long rowCount);
}
