package com.usermgmt.rest;

import com.usermgmt.model.ApiResponse;
import com.usermgmt.service.UserService;
import com.usermgmt.service.impl.UserServiceImpl;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * REST Web Service for Dashboard KPIs and system statistics.
 */
@Path("/stats")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StatsResource {

    private final UserService userService;

    public StatsResource() {
        this.userService = new UserServiceImpl();
    }

    @GET
    public Response getDashboardStatistics() {
        Map<String, Object> stats = userService.getDashboardStatistics();
        return Response.ok(ApiResponse.ok("Statistics retrieved", stats)).build();
    }
}
