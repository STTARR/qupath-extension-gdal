/*
Copyright (C) 2019 STTARR, University Health Network
Contact: STTARR (STTARR.Image.Analysis@uhn.ca)

This project is derived from and extends QuPath <http://qupath.github.io>.
Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
Contact: IP Management (ipmanagement@qub.ac.uk)

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
 
package qupath.lib.images.servers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.panels.PreferencePanel;
import qupath.lib.gui.prefs.PathPrefs;

public class GDALOptionsExtension implements QuPathExtension {

    private static Logger logger = LoggerFactory.getLogger(GDALOptionsExtension.class);
    private String gdalVersion = null;

    private BooleanProperty gdalEnabled;
    private StringProperty gdalDriver;

    private static GDALOptionsExtension instance;

    @Override
    public void installExtension(QuPathGUI qupath) {
		
		gdalVersion = GDALServerBuilder.getGDALVersion();
		if (gdalVersion == null) {
			logger.info("GDAL version could not be found - check GDAL libraries and Java binding " +
                        "are installed into QuPath folder and gdal.jar is in extensions.");
		} else {
			logger.info("GDAL version string: {}", gdalVersion);
		}
		
		// Create persistent properties
		gdalEnabled = PathPrefs.createPersistentPreference("gdalEnabled", true);
		gdalDriver = PathPrefs.createPersistentPreference("gdalDriver", "");
		
		// // Add preferences to QuPath GUI
		PreferencePanel prefs = QuPathGUI.getInstance().getPreferencePanel();
		prefs.addPropertyPreference(gdalEnabled, Boolean.class, "Enable GDAL image server", "GDAL",
            "Allow QuPath to use GDAL for reading JP2 files.");
		prefs.addPropertyPreference(gdalDriver, String.class, "Allow specific GDAL drivers (advanced)", "GDAL",
            "Specify a list of GDAL drivers to allow, separated by semicolons. If empty, will use GDAL's default driver load order.");
	
		instance = GDALOptionsExtension.this;
	}

    @Override
    public String getName() {
		if (gdalVersion == null) {
			return "GDAL server options - missing GDAL?";
        } else {
			return "GDAL server options - " + gdalVersion;
        }
	}

	@Override
	public String getDescription() {
        return "Installs options for the GDAL image server in the QuPath preference pane.";
	}

	public static GDALOptionsExtension getInstance() {
		return instance;
	}

    public boolean isEnabled() {
        return gdalEnabled.getValue();
    }

    public String[] getAllowedDrivers() {
		// Split input by ;, e.g. "JP2ECW;JP2OpenJPEG" -> {"JP2ECW", "JP2OpenJPEG"}
		if (gdalDriver.getValue() != null && !gdalDriver.getValue().isEmpty()) {
	        return gdalDriver.getValue().split(";");
		} else {
			return new String[] {};  // Allow all drivers
		}
    }

}