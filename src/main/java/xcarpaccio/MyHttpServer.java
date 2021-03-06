package xcarpaccio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static xcarpaccio.MyHttpServer.HttpResponse.ok;
import static xcarpaccio.MyHttpServer.HttpResponse.error;
import static xcarpaccio.StringUtils.stringify;

public class MyHttpServer {

    private final int port;

    private final Logger logger;

    private HttpServer server;

    public MyHttpServer(int port, Logger logger) {
        this.port = port;
        this.logger = logger;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/ping", new PingHttpHandler());
        server.createContext("/feedback", new FeedbackHttpHandler());
        server.createContext("/order", new OrderHttpHandler());
        server.start();

        logger.log("Server running on port " + port + "...");
    }

    public void shutdown() {
        if (server != null) {
            logger.log("Stopping server...");
            server.stop(2);
        }
    }

    public static void main(String[] args) throws IOException {
        Logger logger = new Logger();
        new MyHttpServer(9000, logger).start();
    }

    private abstract class AbstractHttpHandler implements HttpHandler {
        protected final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            HttpResponse response = doHandle(httpExchange);
            respond(httpExchange, response);
        }

        public abstract HttpResponse doHandle(HttpExchange request) throws IOException;

        private void respond(HttpExchange httpExchange, HttpResponse response) throws IOException {
            httpExchange.sendResponseHeaders(response.getStatusCode(), response.getBody().length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(response.getBody());
            os.close();
        }
    }

    private class PingHttpHandler extends AbstractHttpHandler {
        @Override
        public HttpResponse doHandle(HttpExchange request) {
            return ok("pong");
        }
    }

    private class FeedbackHttpHandler extends AbstractHttpHandler {
        @Override
        public HttpResponse doHandle(HttpExchange request) {
            InputStream body = request.getRequestBody();

            try {
                FeedbackMessage message = objectMapper.readValue(body, FeedbackMessage.class);
                logger.log(message.getType() + ": " + message.getContent());
            } catch (IOException exception) {
                logger.error(exception.getMessage());
            }

            return ok();
        }
    }

    private class OrderHttpHandler extends AbstractHttpHandler {

        @Override
        public HttpResponse doHandle(HttpExchange request) {

            try {
                String method = request.getRequestMethod();
                String uri = request.getRequestURI().getPath();
                String requestBody = stringify(request.getRequestBody());
                logger.log(method + " " + uri + " " + requestBody);
                Order order = objectMapper.readValue(requestBody, Order.class);
                logger.log("Unserialized order: " + order);

                double total = 0;

                Map<String, Double> countryTaxes = CountryTaxParser.CountryTaxParser();

                if (countryTaxes.get(order.country) == null || order.reduction == null || order.country == null || order.quantities == null || order.prices == null || (order.quantities.length != order.prices.length)) {
                    return ok("");
                }

                for (int i = 0; i < order.prices.length; i++) {
                    total += order.prices[i] * order.quantities[i];
                }

                Double tax = countryTaxes.get(order.country);

                total *= tax;

                switch (order.reduction) {
                    case "STANDARD":
                        if (total >= 50000) total *= 0.85;
                        else if (total >= 10000) total *= 0.90;
                        else if (total >= 7000) total *= 0.93;
                        else if (total >= 5000) total *= 0.95;
                        else if (total >= 1000) total *= 0.97;
                        break;
                    case "HALF PRICE":
                        total /= 2;
                        break;
                    case "PAY THE PRICE":
                        break;
                    default:
                        return ok("");
                }

                Result result = new Result(total);

                return ok(objectMapper.writeValueAsString(result)); // Use this to respond to an order with a total

            } catch (Exception e) {
                logger.log(e);
                return ok("");
            }
        }
    }


    public static class HttpResponse {
        private static final byte[] NO_CONTENT = new byte[]{};

        private final int statusCode;
        private final byte[] body;

        private HttpResponse(int statusCode, byte[] body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public static HttpResponse ok() {
            return ok(NO_CONTENT);
        }

        public static HttpResponse ok(byte[] body) {
            return new HttpResponse(200, body);
        }

        public static HttpResponse error() {
            return new HttpResponse(500, NO_CONTENT);
        }

        public static HttpResponse ok(String body) {
            return ok(body != null ? body.getBytes() : NO_CONTENT);
        }

        public int getStatusCode() {
            return statusCode;
        }

        public byte[] getBody() {
            return body;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HttpResponse response = (HttpResponse) o;

            return statusCode == response.statusCode && Arrays.equals(body, response.body);
        }

        @Override
        public int hashCode() {
            int result = statusCode;
            result = 31 * result + (body != null ? Arrays.hashCode(body) : 0);
            return result;
        }
    }
}
