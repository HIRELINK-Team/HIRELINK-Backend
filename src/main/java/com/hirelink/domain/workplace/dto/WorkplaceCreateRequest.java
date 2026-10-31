package com.hirelink.domain.workplace.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import tools.jackson.databind.JsonNode;

public class WorkplaceCreateRequest {

	@NotBlank(message = "사업장명을 입력해주세요.")
	@Size(max = 150, message = "사업장명은 150자 이하여야 합니다.")
	private String workplaceName;
	@NotBlank(message = "사업자등록번호를 입력해주세요.")
	@Pattern(regexp = "[0-9]{10}|[0-9]{3}-[0-9]{2}-[0-9]{5}", message = "사업자등록번호 형식이 올바르지 않습니다.")
	private String businessRegistrationNumber;
	@NotNull(message = "업종을 선택해주세요.")
	private Long businessTypeId;
	@NotBlank(message = "직원 수 구간을 선택해주세요.")
	@Pattern(regexp = "UNDER_5|FIVE_OR_MORE", message = "직원 수 구간은 UNDER_5 또는 FIVE_OR_MORE여야 합니다.")
	private String employeeCountRange;
	@NotBlank(message = "사업장 주소를 입력해주세요.")
	@Size(max = 500, message = "주소는 500자 이하여야 합니다.")
	private String workplaceAddress;
	@NotNull(message = "위도를 입력해주세요.")
	@DecimalMin(value = "-90", message = "위도는 -90부터 90까지 입력해주세요.")
	@DecimalMax(value = "90", message = "위도는 -90부터 90까지 입력해주세요.")
	@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
	private BigDecimal latitude;
	@NotNull(message = "경도를 입력해주세요.")
	@DecimalMin(value = "-180", message = "경도는 -180부터 180까지 입력해주세요.")
	@DecimalMax(value = "180", message = "경도는 -180부터 180까지 입력해주세요.")
	@Digits(integer = 3, fraction = 7, message = "좌표는 소수점 이하 7자리까지 입력해주세요.")
	private BigDecimal longitude;

	@JsonSetter(nulls = Nulls.FAIL)
	public void setWorkplaceName(JsonNode value) {
		workplaceName = requireText(value);
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBusinessRegistrationNumber(JsonNode value) {
		businessRegistrationNumber = requireText(value).strip();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setBusinessTypeId(JsonNode value) {
		if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
			throw new IllegalArgumentException("업종 ID는 Long 범위의 정수여야 합니다.");
		}
		businessTypeId = value.longValue();
	}

	@JsonSetter(nulls = Nulls.FAIL)
	public void setEmployeeCountRange(JsonNode value) {
		employeeCountRange = requireText(value);
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
		throw new IllegalArgumentException("허용되지 않은 요청 항목입니다.");
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

	public String getBusinessRegistrationNumber() {
		return businessRegistrationNumber;
	}

	public Long getBusinessTypeId() {
		return businessTypeId;
	}

	public String getEmployeeCountRange() {
		return employeeCountRange;
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
