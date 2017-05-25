package org.jusecase.properties.usecases;

import org.jusecase.Usecase;
import org.jusecase.properties.entities.Key;
import org.jusecase.properties.gateways.PropertiesGateway;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class Search implements Usecase<Search.Request, Search.Response> {
    private final PropertiesGateway propertiesGateway;

    @Inject
    public Search(PropertiesGateway propertiesGateway) {
        this.propertiesGateway = propertiesGateway;
    }

    @Override
    public Response execute(Request request) {
        Response response = new Response();
        response.keys = propertiesGateway.search(request);
        return response;
    }

    public static class Request {
        public String query;
        public boolean regex;
        public boolean caseSensitive;
    }

    public static class Response {
        public List<Key> keys;
    }
}
