from http.server import BaseHTTPRequestHandler, HTTPServer
import json

class MockActuator(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/actuator/health':
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            self.wfile.write(b'{"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"}}}')
        elif '/actuator/metrics/' in self.path:
            self.send_response(200)
            self.send_header('Content-type', 'application/json')
            self.end_headers()
            # Mock http.server.requests
            self.wfile.write(b'{"name":"http.server.requests","measurements":[{"statistic":"COUNT","value":120.0},{"statistic":"TOTAL_TIME","value":5.0}]}')
        elif self.path == '/actuator/metrics':
             self.send_response(200)
             self.send_header('Content-type', 'application/json')
             self.end_headers()
             self.wfile.write(b'{"names":["http.server.requests","jvm.memory.used"]}')
        else:
            self.send_response(404)
            self.end_headers()

def run():
    server_address = ('', 8081) # Run on 8081 to avoid conflict with Agent (8080)
    httpd = HTTPServer(server_address, MockActuator)
    print("Mock Actuator running on 8081...")
    httpd.serve_forever()

if __name__ == '__main__':
    run()
