package org.avni.server.service;

import org.avni.server.importer.batch.csv.writer.header.LocationHeaderCreator;


public interface ImportLocationsConstants {
    String STRING_CONSTANT_ONE = "1";
    String STRING_CONSTANT_NEW_LINE = "\n";
    String STRING_CONSTANT_SEPARATOR = ",";
    String STRING_PLACEHOLDER_BLOCK = "\"%s\"";
    String EXAMPLE = "Example: ";
    String ALLOWED_VALUES = "Allowed values: ";
    String OPTIONAL = "Optional";
    String COLUMN_NAME_GPS_COORDINATES = LocationHeaderCreator.gpsCoordinates;
    String COLUMN_NAME_LOCATION_WITH_FULL_HIERARCHY = "Location with full hierarchy";
    String COLUMN_NAME_NEW_LOCATION_NAME = "New location name";
    String COLUMN_NAME_PARENT_LOCATION_WITH_FULL_HIERARCHY = "Parent location with full hierarchy";
    String LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION = "Can be found from Admin -> Locations -> Click Export. Used to specify which location's fields need to be updated. mandatory field";
    String NEW_LOCATION_NAME_DESCRIPTION = "Enter new name here ONLY if it needs to be updated";
    String PARENT_LOCATION_WITH_FULL_HIERARCHY_DESCRIPTION = "Hierarchy of parent location that should contain the child location";
    String LOCATION_WITH_FULL_HIERARCHY_EXAMPLE = "PHC B, Sub B, Vil B";
    String NEW_LOCATION_NAME_EXAMPLE = "Vil C";
    String PARENT_LOCATION_WITH_FULL_HIERARCHY_EXAMPLE = "PHC C, Sub C";
    String GPS_COORDINATES_EXAMPLE = "Example: 23.45,43.85";
    String PARENT_LOCATION_REQUIRED = ". Mandatory to mention the parent locations to which the location to be created belongs to.";
    String GPS_COORDINATES_SAMPLE = "23.45,43.85";
    int STARTING_INDEX = 0;
    String[] EXAMPLE_LOCATION_NAMES = {"Jawhar","Sarsun","Dehere","Barso", "Roru"};
}
