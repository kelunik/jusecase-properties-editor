package org.jusecase.properties.usecases;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jusecase.Usecase;
import org.jusecase.properties.entities.Property;
import org.jusecase.properties.entities.UndoableRequest;
import org.jusecase.properties.gateways.PropertiesGateway;
import org.jusecase.properties.plugins.PluginManager;
import org.jusecase.properties.plugins.importer.PropertiesImporter;


@Singleton
public class Import implements Usecase<Import.Request, Import.Response> {

    private final PropertiesGateway propertiesGateway;
    private final PluginManager pluginManager;

    @Inject
    public Import( PropertiesGateway propertiesGateway, PluginManager pluginManager ) {
        this.propertiesGateway = propertiesGateway;
        this.pluginManager = pluginManager;
    }

    @Override
    public Response execute(Request request) {
        if (request.undo) {
            propertiesGateway.deleteProperties(request.importedProperties);
            propertiesGateway.addProperties(request.overwrittenProperties);
        } else {
            if (request.importedProperties == null) {
                request.importedProperties = loadProperties(request);
                request.overwrittenProperties = findOverwrittenProperties(request.importedProperties);
            }

            propertiesGateway.addProperties(request.importedProperties);
        }
        return new Response();
    }

    private List<Property> loadProperties(Request request) {
        PropertiesImporter importer = pluginManager.getPlugin(request.pluginId, PropertiesImporter.class);
        if (importer == null) {
            throw new UsecaseException("No plugin '" + request.pluginId + "' found to import properties");
        }

        request.name = "import " + importer.getPluginName();
        List<Property> properties = new ArrayList<>();
        for ( Path file : request.files ) {
            List<Property> importResult = importer.importProperties(file);
            for ( Property property : importResult ) {
                property.fileName = propertiesGateway.resolveFileName(property.fileName);
                if (property.fileName != null) {
                    properties.add(property);
                }
            }
        }

        return properties;
    }

    private List<Property> findOverwrittenProperties( List<Property> importedProperties ) {
        Set<String> keys = new HashSet<>();
        for ( Property importedProperty : importedProperties ) {
            keys.add(importedProperty.key);
        }

        List<Property> overwrittenProperties = new ArrayList<>();
        for ( String key : keys ) {
            List<Property> properties = propertiesGateway.getProperties(key);
            if (properties != null) {
                overwrittenProperties.addAll(properties);
            }
        }

        return overwrittenProperties;
    }

    public static class Request extends UndoableRequest {
        public List<Path> files;
        public String pluginId;
        List<Property> importedProperties;
        List<Property> overwrittenProperties;
    }

    public static class Response {
    }
}
