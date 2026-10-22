package io.github.bong8230.precheck.settlement;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SettlementMapper {

	/** 기준일자에 수신 이력이 있는 파일 코드 */
	List<String> findReceivedFileCodes(@Param("baseDate") String baseDate);

	int deleteSettlement(@Param("baseDate") String baseDate);

	/** 스테이징 데이터를 계좌별로 집계해 정산 결과를 만든다. 생성 건수를 돌려준다. */
	int insertSettlement(@Param("baseDate") String baseDate);
}
