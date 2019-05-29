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

import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

public class GDALServerBuilder implements ImageServerBuilder<BufferedImage> {

    private static Logger logger = LoggerFactory.getLogger(GDALServerBuilder.class);

    @Override
    public ImageServer<BufferedImage> buildServer(String path) {
        try {
            return new GDALImageServer(path);
        } catch (UnsatisfiedLinkError e) {
            logger.error("Could not load GDAL native library", e);
        } catch (Exception e) {
            logger.warn("Unable to open {} with GDAL: {}", path, e.getLocalizedMessage());
        }
        return null;
    }

    @Override
    public float supportLevel(String path, ImageCheckType type, Class<?> cls) {
        GDALOptionsExtension opts = GDALOptionsExtension.getInstance();
        if (!opts.isEnabled())
            return 0;
        if (cls != BufferedImage.class)
            return 0;
        switch (type) {
            case TIFF_2D_RGB:
                return 0;
            case TIFF_IMAGEJ:
                return 0;
            case TIFF_OTHER:
                return 0;
            case UNKNOWN:  // JP2 files fall into this category.
                // Don't try to open files if not .jp2 ext (is there a nicer way of checking this?)
                if (path.toLowerCase().endsWith(".jp2")) {
                    return 3;
                } else {
                    return 0;
                }
            case URL:
                return 0;
            default:
                return 0;
        }
    }

    @Override
    public String getName() {
        return "GDAL Builder";
    }

    @Override
    public String getDescription() {
        return "Image server for JP2 whole slide images using the GDAL library.";
    }

    public static String getGDALVersion() {
        try {
            Class<?> cls = Class.forName("org.gdal.gdal.gdal");
            return (String)cls.getMethod("VersionInfo", String.class).invoke(null, "--version");
        } catch (Exception e) {
            logger.error("Could not load GDAL native library", e);
        }
        return null;
    }

}