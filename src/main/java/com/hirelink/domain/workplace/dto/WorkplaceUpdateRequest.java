package com.hirelink.domain.workplace.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보낸 항목만 수정한다. 기본 주소·위도·경도는 함께 전송한다. 상세 주소만 수정할 수 있다. 기본 주소 변경 시 상세 주소를 생략하면 이전 상세 주소를 지운다. null·빈 요청·알 수 없는 필드는 400.", example = "{\"workplaceName\":\"예시카페 성수점\"}")
public class WorkplaceUpdateRequest {

	@Size(max = 150, message = "사업장명은 150자 이하여야 합니다.")
	@Schema(description = "사업장명. 빈 문자열·공백만 입력 불가. 생략하면 기존 값 유지, null 불가", maxLength = 150,
			example = "예시카페",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String workplaceName;
	@Schema(description = "업종 ID: 1 음식점·카페, 2 매장·판매, 3 사무·행사·관광, 4 통·번역, 5 면세점, 6 전공·학업, 7 인턴십, 8 기타. 생략하면 기존 값 유지, null 불가",
			example = "1",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private Long businessTypeId;
	@Size(max = 500, message = "주소는 500자 이하여야 합니다.")
	@Schema(description = "사업장 주소. 생략하면 기존 값 유지, null 불가", maxLength = 500,
			example = "서울특별시 성동구 예시로 10",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private String workplaceAddress;
    @Size(max = 500, message = "상세 주소는 500자 이하여야 합니다.")
    @Schema(description = "상세 주소. 생략 가능. 빈 문자열로 삭제. 기본 주소와 분리 저장", maxLength = 500,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
    private String workplaceAddressDetail;

    @JsonSetter(nulls = Nulls.FAIL)
    public void setWorkplaceAddressDetail(JsonNode value) {
        if (value == null || !value.isString()) { throw new IllegalArgumentException("상세 주소는 문자열이어야 합니다."); }
        workplaceAddressDetail = value.stringValue().strip();
    }

    public String getWorkplaceAddressDetail() { return workplaceAddressDetail; }

	@DecimalMin(value = "-90", message = "위도는 -90부터 90까지 입력해주세요.")
	@DecimalMax(value = "90", message = "위도는 -90부터 90까지 입력해주세요.")
	@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
	@Schema(description = "위도. 소수점 이하 최대 7자리. 생략하면 기존 값 유지, null 불가", minimum = "-90", maximum = "90",
			example = "37.5665",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private BigDecimal latitude;
	@DecimalMin(value = "-180", message = "경도는 -180부터 180까지 입력해주세요.")
	@DecimalMax(value = "180", message = "경도는 -180부터 180까지 입력해주세요.")
	@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
	@Schema(description = "경도. 소수점 이하 최대 7자리. 생략하면 기존 값 유지, null 불가", minimum = "-180", maximum = "180",
			example = "126.978",
			requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = false)
	private BigDecimal longitude;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWorkplaceName(JsonNode value) {
		workplaceName = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBusinessTypeId(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new IllegalArgumentException("업종 ID는 Long 범위의 정수여야 합니다.");
		}
		businessTypeId = value.longValue();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWorkplaceAddress(JsonNode value) {
		workplaceAddress = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setLatitude(JsonNode value) {
		latitude = requireDecimal(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setLongitude(JsonNode value) {
		longitude = requireDecimal(value);
	}

	@JsonAnySetter
	public void rejectUnknownField(String field, JsonNode value) {
		throw new IllegalArgumentException("수정할 수 없는 항목이 포함되어 있습니다.");
	}

	public boolean hasChanges() {
		return workplaceName != null || businessTypeId != null || workplaceAddressDetail != null || hasLocationChanges();
	}

	public boolean hasLocationChanges() {
		return workplaceAddress != null || latitude != null || longitude != null;
	}

	private String requireText(JsonNode value) {
		if (value == null || !value.isString() || value.stringValue().isBlank()) {
			throw new IllegalArgumentException("공백이 아닌 문자열을 입력해주세요.");
		}
		return value.stringValue();
	}

	private BigDecimal requireDecimal(JsonNode value) {
		if (value == null || !value.isNumber()) {
			throw new IllegalArgumentException("좌표는 숫자로 입력해주세요.");
		}
		return value.decimalValue();
	}

	public String getWorkplaceName() {
		return workplaceName;
	}

	public Long getBusinessTypeId() {
		return businessTypeId;
	}

	public String getWorkplaceAddress() {
		return workplaceAddress;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}
}
