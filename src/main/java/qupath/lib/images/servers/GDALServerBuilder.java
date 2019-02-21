package qupath.lib.images.servers;

import java.awt.image.BufferedImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;

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

}