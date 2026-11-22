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
			for(String invalid:List.of("{}","null","private-document-content",
					"{\"universityName\":\"학교\",\"campusName\":null,\"gradeLevel\":1.5,\"semesterCount\":null}",
					"{\"universityName\":\"학교\",\"campusName\":null,\"gradeLevel\":null}")) {
				body[0]=invalid;
				assertThatThrownBy(()->client.recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class)
						.hasMessage("재학증명서 인식 서비스를 이용할 수 없습니다.");
			}
			status[0]=422;
			assertThatThrownBy(()->client.recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("UNREADABLE_ENROLLMENT");
			status[0]=503;
			assertThatThrownBy(()->client.recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class).extracting("code").isEqualTo("ENROLLMENT_OCR_FAILED");
			assertThatThrownBy(()->new EnrollmentOcrClient("https://example.com").recognize(data,"application/pdf")).isInstanceOf(StudentDocumentException.class);
		} finally {server.stop(0);}
	}
}
