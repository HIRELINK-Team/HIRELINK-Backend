package com.hirelink.domain.employer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import com.hirelink.domain.member.entity.UserAccount;

@Entity
@Table(name = "employer_profile")
public class EmployerProfile {

	@Id
	private Long id;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "id", nullable = false)
	private UserAccount userAccount;

	@Column(length = 50)
	private String position;

	protected EmployerProfile() {
	}

	public EmployerProfile(UserAccount userAccount) {
		this.userAccount = userAccount;
	}

	public Long getId() {
		return id;
	}

	public String getPosition() {
		return position;
	}
}
