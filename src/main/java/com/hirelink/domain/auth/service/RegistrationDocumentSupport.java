package com.hirelink.domain.auth.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import com.hirelink.domain.auth.client.RegistrationOcrClient;
import com.hirelink.global.exception.SocialAuthException;
import tools.jackson.databind.JsonNode;

final class RegistrationDocumentSupport {
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private RegistrationDocumentSupport() { }
	static byte[] readImage(MultipartFile file) {
		return readFile(file, false);
	}

	static byte[] readFile(MultipartFile file, boolean document) {
		if (file.isEmpty()) { throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "등록증 이미지 또는 체류기간 증빙 파일을 첨부해주세요."); }
		if (file.getSize() > MAX_FILE_SIZE) { throw tooLarge(); }
		byte[] bytes = null;
		try (InputStream input = file.getInputStream()) {
			bytes = input.readNBytes(MAX_FILE_SIZE + 1);
			if (bytes.length > MAX_FILE_SIZE) { throw tooLarge(); }
			boolean png = "image/png".equals(file.getContentType()) && startsWith(bytes, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
			boolean jpg = "image/jpeg".equals(file.getContentType()) && startsWith(bytes, new byte[]{(byte) 255, (byte) 216, (byte) 255});
			boolean pdf = "application/pdf".equals(file.getContentType()) && startsWith(bytes, new byte[]{37, 80, 68, 70, 45});
			if (document ? !pdf : !png && !jpg) {
				throw new SocialAuthException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", document
						? "체류기간 증빙은 PDF만 사용할 수 있습니다." : "등록증 촬영 이미지는 JPG, PNG만 사용할 수 있습니다.");
			}
			return bytes;
		} catch (IOException | RuntimeException exception) {
			if (bytes != null) { Arrays.fill(bytes, (byte) 0); }
			if (exception instanceof SocialAuthException social) { throw social; }
			throw new SocialAuthException(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "등록증 이미지 또는 체류기간 증빙 파일을 읽을 수 없습니다.");
		}
	}

	static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
	}

	static SocialAuthException tooLarge() {
		return new SocialAuthException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "등록증 이미지 또는 체류기간 증빙 파일은 5MB 이하여야 합니다.");
	}

	static String text(JsonNode node, String field) {
		if (!node.path(field).isString() || node.path(field).stringValue().isBlank()) { throw RegistrationOcrClient.unreadable(); }
		return node.path(field).stringValue().strip();
	}

	static LocalDate date(JsonNode node, String field) {
		try { return LocalDate.parse(text(node, field)); }
		catch (DateTimeParseException exception) { throw RegistrationOcrClient.unreadable(); }
	}

	static String countryCode(String value) {
		String normalized = value.strip().toUpperCase(Locale.ROOT);
		if (normalized.equals("VIET NAM")) { normalized = "VIETNAM"; }
		for (String code : Locale.getISOCountries()) {
			Locale locale = new Locale("", code);
			if (normalized.equals(code) || normalized.equals(locale.getISO3Country())
					|| normalized.equals(locale.getDisplayCountry(Locale.ENGLISH).toUpperCase(Locale.ROOT))
					|| normalized.equals(locale.getDisplayCountry(Locale.KOREAN))) { return code; }
		}
		throw RegistrationOcrClient.unreadable();
	}
}
