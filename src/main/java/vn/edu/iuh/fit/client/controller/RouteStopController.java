package vn.edu.iuh.fit.client.controller;

import vn.edu.iuh.fit.client.service.SessionManager;
import vn.edu.iuh.fit.client.service.SocketRequestService;
import vn.edu.iuh.fit.common.command.ActionType;
import vn.edu.iuh.fit.common.dto.RouteActionDTO;
import vn.edu.iuh.fit.common.dto.RouteDTO;
import vn.edu.iuh.fit.common.dto.RouteStopDTO;
import vn.edu.iuh.fit.common.request.Request;
import vn.edu.iuh.fit.common.response.Response;

import java.util.List;

public class RouteStopController {

    public List<RouteStopDTO> findAllRouteStops() {
        Response response = new SocketRequestService().send(new Request(ActionType.FIND_ALL_ROUTES, null));
        if (!response.isSuccess() || !(response.getData() instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(RouteDTO.class::isInstance)
                .map(RouteDTO.class::cast)
                .filter(route -> route.getRouteStops() != null)
                .flatMap(route -> route.getRouteStops().stream())
                .toList();
    }

    public List<RouteStopDTO> findRouteStopsByRouteId(String routeId) {
        RouteActionDTO dto = RouteActionDTO.builder()
                .requestEmployeeId(currentEmployeeId())
                .routeId(routeId)
                .build();
        Response response = new SocketRequestService().send(new Request(ActionType.FIND_ROUTE_STOPS_BY_ROUTE, dto));
        if (!response.isSuccess() || !(response.getData() instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(RouteStopDTO.class::isInstance)
                .map(RouteStopDTO.class::cast)
                .toList();
    }

    public RouteStopDTO findRouteStopById(String routeStopId) {
        return findAllRouteStops().stream()
                .filter(stop -> routeStopId != null && routeStopId.equals(stop.getId()))
                .findFirst()
                .orElse(null);
    }

    public boolean createRouteStop(RouteStopDTO routeStop) {
        if (routeStop == null) return false;
        routeStop.setRequestEmployeeId(currentEmployeeId());
        Response response = new SocketRequestService().send(new Request(ActionType.CREATE_ROUTE_STOP, routeStop));
        return response.isSuccess();
    }

    public boolean updateRouteStop(RouteStopDTO routeStop) {
        if (routeStop == null) return false;
        routeStop.setRequestEmployeeId(currentEmployeeId());
        Response response = new SocketRequestService().send(new Request(ActionType.UPDATE_ROUTE_STOP, routeStop));
        return response.isSuccess();
    }

    public boolean deleteRouteStop(String routeStopId) {
        RouteStopDTO dto = RouteStopDTO.builder()
                .requestEmployeeId(currentEmployeeId())
                .id(routeStopId)
                .build();
        Response response = new SocketRequestService().send(new Request(ActionType.DELETE_ROUTE_STOP, dto));
        return response.isSuccess();
    }

    private String currentEmployeeId() {
        return SessionManager.getInstance().getEmployeeId();
    }
}
