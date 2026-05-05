package vn.edu.iuh.fit.client.service;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class SocketRequestService {

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9090;
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private final String host;
    private final int port;

    public SocketRequestService() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public SocketRequestService(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public Response send(Request request) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                out.writeObject(request);
                out.flush();

                Object responseObject = in.readObject();
                if (responseObject instanceof Response response) {
                    return response;
                }
                return Response.error("Phản hồi từ server không hợp lệ");
            }
        } catch (Exception e) {
            return Response.error("Không thể kết nối server: " + e.getMessage());
        }
    }
}
