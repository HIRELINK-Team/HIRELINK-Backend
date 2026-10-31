package com.hirelink.domain.workplace.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.hirelink.domain.member.entity.MemberRole;
import com.hirelink.domain.member.entity.UserAccount;
import com.hirelink.domain.member.repository.UserAccountRepository;
import com.hirelink.domain.workplace.client.PaddleOcrClient;
import com.hirelink.domain.workplace.dto.BusinessRegistrationOcrResponse;
import com.hirelink.global.exception.InvalidWorkplaceException;
import com.hirelink.global.exception.UnsupportedBusinessRegistrationFileException;
import com.hirelink.global.exception.WithdrawnAccountException;

@Service
public class BusinessRegistrationOcrService {

	private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
	private final UserAccountRepository userAccountRepository;
	private final PaddleOcrClient paddleClient;

	public BusinessRegistrationOcrService(UserAccountRepository userAccountRepository, PaddleOcrClient paddleClient) {
		this.userAccountRepository = userAccountRepository;
		this.paddleClient = paddleClient;
	}

	public BusinessRegistrationOcrResponse recognize(Long userId, MultipartFile file) {
		UserAccount user = userAccountRepository.findById(userId)
				.orElseThrow(() -> new AuthenticationCredentialsNotFoundException("로그인이 필요합니다."));
		if (user.isWithdrawn()) {
			throw new WithdrawnAccountException();
		}
		if (user.getRole() != MemberRole.EMPLOYER) {
			throw new AccessDeniedException("사업주 계정만 사업자등록증을 확인할 수 있습니다.");
		}
		if (file.isEmpty()) {
			throw new InvalidWorkplaceException("사업자등록증 파일을 첨부해주세요.");
		}
		if (file.getSize() > MAX_FILE_SIZE) {
			throw new MaxUploadSizeExceededException(MAX_FILE_SIZE);
		}
		byte[] bytes = null;
		try (InputStream input = file.getInputStream()) {
			bytes = input.readNBytes(MAX_FILE_SIZE + 1);
			if (bytes.length > MAX_FILE_SIZE) {
				throw new MaxUploadSizeExceededException(MAX_FILE_SIZE);
			}
			String format = fileFormat(bytes, file.getContentType());
			return paddleClient.recognize(bytes, format);
		} catch (IOException exception) {
			throw new IllegalStateException("사업자등록증 파일을 읽을 수 없습니다.");
		} finally {
			if (bytes != null) {
				Arrays.fill(bytes, (byte) 0);
			}
		}
	}

	private String fileFormat(byte[] bytes, String contentType) {
		if ("application/pdf".equals(contentType) && startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
			return "pdf";
		}
		if ("image/png".equals(contentType) && startsWith(bytes, new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
			return "png";
		}
		if ("image/jpeg".equals(contentType) && startsWith(bytes, new byte[] {(byte) 255, (byte) 216, (byte) 255})) {
			return "jpg";
		}
		throw new UnsupportedBusinessRegistrationFileException();
	}

	private boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}

}
