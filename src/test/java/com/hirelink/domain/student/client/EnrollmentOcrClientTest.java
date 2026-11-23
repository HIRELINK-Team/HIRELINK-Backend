package com.hirelink.domain.student.client;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import com.hirelink.global.exception.StudentDocumentException;
import static org.assertj.core.api.Assertions.*;

class EnrollmentOcrClientTest {
	@Test void checksEndpointStatusAndTypesWithoutDisclosingUpstreamContent() throws Exception {
		var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
		int[] status={200}; String[] body={"{\"universityName\":\"학교\",\"campusName\":null,\"gradeLevel\":null,\"semesterCount\":7}"};
		server.createContext("/student/enrollment", exchange -> {
			assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).isEqualTo("application/pdf");
			assertThat(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).isEqualTo("%PDF-test");
			byte[] bytes=body[0].getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type","application/json");
			exchange.sendResponseHeaders(status[0],bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
		});
		server.start();
		try {
			var client=new EnrollmentOcrClient("http://127.0.0.1:"+server.getAddress().getPort());
			byte[] data="%PDF-test".getBytes(StandardCharsets.UTF_8);
			assertThat(client.recognize(data,"application/pdf").supportedGrade()).isEqualTo((short)4);
			body[0]="{\"universityName\":null,\"campusName\":null,\"gradeLevel\":null,\"semesterCount\":null}";
			assertThatThrownBy(()->client.recognize(data,"application/pdf").supportedGrade()).hasMessageContaining("대학교명과 학년 또는 학기 수를 확인하지 못했습니다.");
			for(String invalid:List.of("{}","null","private-document-content",
					"{\"universityName\":\"학교\",\"campusName\":null,\"gradeLevel\":1.5,\"semesterCount\":null}",
					"{\"universityName\":\"학교\",\"campusName\":null,\"gradeLevel\":null}")) {
				body[0]=invalid;
				assertThatThrownBy(()->client.recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class)
						.hasMessage("재학증명서 인식 서비스를 이용할 수 없습니다.");
			}
			status[0]=422;
			assertThatThrownBy(()->client.recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("UNREADABLE_ENROLLMENT");
			for (String fields : List.of("\"universityName\"", "\"gradeLevelOrSemesterCount\"", "\"universityName\",\"gradeLevelOrSemesterCount\"")) {
				body[0]="{\"code\":\"OCR_REQUIRED_FIELDS_UNREADABLE\",\"fields\":["+fields+"]}";
				var error = catchThrowableOfType(() -> client.recognize(data,"application/pdf"), StudentDocumentException.class);
				assertThat(error.getCode()).isEqualTo("UNREADABLE_ENROLLMENT");
				if (fields.contains("universityName")) assertThat(error).hasMessageContaining("대학교명");
				if (fields.contains("gradeLevelOrSemesterCount")) assertThat(error).hasMessageContaining("학년 또는 학기 수");
			}
			for (String invalid : List.of("{\"code\":\"OCR_REQUIRED_FIELDS_UNREADABLE\",\"fields\":[\"private-name\"]}",
					"{\"code\":\"OCR_REQUIRED_FIELDS_UNREADABLE\",\"fields\":[\"topikLevel\"]}",
					"{\"code\":\"OCR_REQUIRED_FIELDS_UNREADABLE\",\"fields\":[]}",
					"{\"code\":\"OCR_REQUIRED_FIELDS_UNREADABLE\",\"fields\":[\"universityName\",\"universityName\"]}",
					"private-name".repeat(200), "not-json")) {
				body[0]=invalid;
				assertThatThrownBy(()->client.recognize(data,"application/pdf")).hasMessage("재학증명서 정보를 확인하지 못했습니다. 다시 촬영하거나 업로드해주세요.");
			}
			status[0]=503;
			assertThatThrownBy(()->client.recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("ENROLLMENT_OCR_FAILED");
			assertThatThrownBy(()->new EnrollmentOcrClient("https://example.com").recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class);
		} finally {server.stop(0);}
	}
}
