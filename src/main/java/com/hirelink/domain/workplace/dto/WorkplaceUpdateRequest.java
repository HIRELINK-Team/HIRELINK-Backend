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

public class WorkplaceUpdateRequest {

	@Size(max = 150, message = "사업장명은 150자 이하여야 합니다.")
	private String workplaceName;
	private Long businessTypeId;
	@Size(max = 500, message = "주소는 500자 이하여야 합니다.")
	private String workplaceAddress;
	@DecimalMin(value = "-90", message = "위도는 -90부터 90까지 입력해주세요.")
	@DecimalMax(value = "90", message = "위도는 -90부터 90까지 입력해주세요.")
	@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
	private BigDecimal latitude;
	@DecimalMin(value = "-180", message = "경도는 -180부터 180까지 입력해주세요.")
	@DecimalMax(value = "180", message = "경도는 -180부터 180까지 입력해주세요.")
	@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
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
		return workplaceName != null || businessTypeId != null || hasLocationChanges();
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
