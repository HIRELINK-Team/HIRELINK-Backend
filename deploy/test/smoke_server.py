#!/usr/bin/env python3
import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


CORE_PATHS = {
    "/api/job-postings/{jobPostingId}": {"get": {}},
    "/api/recommendations": {"get": {}},
    "/api/applications": {"post": {}},
}


class SmokeHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parts = self.path.split("/")
        scenario = parts[1] if len(parts) > 2 else ""
        endpoint = "/" + "/".join(parts[2:])

        if scenario == "bad-http" and endpoint == "/health":
            self.respond(503, {"status": "UP"})
        elif scenario == "bad-health-json" and endpoint == "/health":
            self.respond(200, "not-json", content_type="text/plain")
        elif scenario == "down" and endpoint == "/health":
            self.respond(200, {"status": "DOWN"})
        elif endpoint == "/health":
            self.respond(200, {"status": "UP"})
        elif scenario == "bad-openapi-json" and endpoint == "/v3/api-docs":
            self.respond(200, "not-json", content_type="text/plain")
        elif scenario == "missing-path" and endpoint == "/v3/api-docs":
            self.respond(200, {"paths": {"/api/recommendations": {"get": {}}}})
        elif endpoint == "/v3/api-docs":
            self.respond(200, {"paths": CORE_PATHS})
        else:
            self.respond(404, {"error": "not found"})

    def respond(self, status, body, content_type="application/json"):
        content = body if isinstance(body, str) else json.dumps(body)
        encoded = content.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, _format, *_args):
        return


port_file = Path(sys.argv[1])
server = ThreadingHTTPServer(("127.0.0.1", 0), SmokeHandler)
port_file.write_text(str(server.server_port))
server.serve_forever()
