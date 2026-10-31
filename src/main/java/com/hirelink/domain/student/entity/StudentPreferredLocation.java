package com.hirelink.domain.student.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "student_preferred_location", uniqueConstraints = {
		@UniqueConstraint(name = "uk_student_preferred_location_user", columnNames = "user_id")
})
public class StudentPreferredLocation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "location_name", nullable = false, length = 150)
	private String locationName;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal latitude;

	@Column(nullable = false, precision = 10, scale = 7)
	private BigDecimal longitude;

	@Column(name = "radius_m", nullable = false)
	private Integer radiusM;

	protected StudentPreferredLocation() {
	}

	public StudentPreferredLocation(Long userId) {
		this.userId = userId;
	}

	public void updateLocation(String locationName, BigDecimal latitude, BigDecimal longitude, Integer radiusM) {
		this.locationName = locationName;
		this.latitude = latitude;
		this.longitude = longitude;
		this.radiusM = radiusM;
	}

	public String getLocationName() {
		return locationName;
	}

	public BigDecimal getLatitude() {
		return latitude;
	}

	public BigDecimal getLongitude() {
		return longitude;
	}

	public Integer getRadiusM() {
		return radiusM;
	}
}
