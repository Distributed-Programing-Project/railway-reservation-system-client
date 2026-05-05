package vn.edu.iuh.fit.client.service;

import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.LoginRequestDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

public class LoginClientService {

    private final SocketRequestService socketRequestService = new SocketRequestService();

    public Response login(String username, String password) {
        LoginRequestDTO loginRequestDTO = LoginRequestDTO.builder()
                .username(username)
                .password(password)
                .build();
        Request request = new Request(ActionType.LOGIN, loginRequestDTO);
        return socketRequestService.send(request);
    }
}
