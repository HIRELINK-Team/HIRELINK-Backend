package com.hirelink.domain.workplace.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.hirelink.domain.member.entity.UserAccount;

@Entity
@Table(name = "workplace", uniqueConstraints = {
		@UniqueConstraint(name = "uk_workplace_business_registration_number", columnNames = "business_registration_number")
})
public class Workplace {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employer_user_id", nullable = false)
	private UserAccount employerUser;

	@Column(name = "workplace_name", nullable = false, length = 150)
	private String workplaceName;

	@Column(name = "representative_name", length = 100)
	private String representativeName;

	@Column(name = "business_registration_file_key", length = 500)
	private String businessRegistrationFileKey;

	@Column(name = "business_registration_number", nullable = false, length = 20)
	private String businessRegistrationNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "business_type_id", nullable = false)
	private BusinessType businessType;

	@Column(name = "workplace_address", nullable = false, length = 500)
	private String workplaceAddress;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "employee_count_range", nullable = false, length = 20)
	private String employeeCountRange;

	protected Workplace() {
	}

	public Workplace(UserAccount employerUser, String workplaceName, String representativeName,
			String businessRegistrationFileKey, String businessRegistrationNumber, BusinessType businessType,
			String workplaceAddress, BigDecimal latitude, BigDecimal longitude, String employeeCountRange) {
		this.employerUser = employerUser;
		this.workplaceName = workplaceName;
		this.representativeName = representativeName;
		this.businessRegistrationFileKey = businessRegistrationFileKey;
		this.businessRegistrationNumber = businessRegistrationNumber;
		this.businessType = businessType;
		this.workplaceAddress = workplaceAddress;
		this.latitude = latitude;
		this.longitude = longitude;
		this.employeeCountRange = employeeCountRange;
	}

	public void updateName(String workplaceName) {
		this.workplaceName = workplaceName;
	}

	public void updateBusinessType(BusinessType businessType) {
		this.businessType = businessType;
	}

	public void updateLocation(String workplaceAddress, BigDecimal latitude, BigDecimal longitude) {
		this.workplaceAddress = workplaceAddress;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public Long getId() {
		return id;
	}

	public UserAccount getEmployerUser() {
		return employerUser;
	}

	public String getWorkplaceName() {
		return workplaceName;
	}

	public String getRepresentativeName() {
		return representativeName;
	}

	public String getBusinessRegistrationFileKey() {
		return businessRegistrationFileKey;
	}

	public String getBusinessRegistrationNumber() {
		return businessRegistrationNumber;
	}

	public BusinessType getBusinessType() {
		return businessType;
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

	public String getEmployeeCountRange() {
		return employeeCountRange;
	}
}
