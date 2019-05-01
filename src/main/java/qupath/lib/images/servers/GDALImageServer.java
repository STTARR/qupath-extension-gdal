package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.DataBuffer;
import java.awt.Transparency;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Vector;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.awt.common.AwtTools;
import qupath.lib.awt.images.PathBufferedImage;
import qupath.lib.images.PathImage;
import qupath.lib.regions.RegionRequest;
import qupath.lib.common.GeneralTools;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.gdalconst.gdalconstConstants;


public class GDALImageServer extends AbstractImageServer<BufferedImage> {

    final private static Logger logger = LoggerFactory.getLogger(GDALImageServer.class);

    private ImageServerMetadata originalMetadata;
    private ImageServerMetadata userMetadata;
    private double[] downsamples;
    private String ds_path;

    // Parameters
    static final double MAX_DOWNSAMPLE_FACTOR = 150.0;
    // Set this to empty to allow all drivers, but only JP2OpenJPEG has been tested.
    // This option should be exposed so that the user can set driver priorities (like with BioFormats)
    static final String[] GDAL_ALLOWED_DRIVERS = {}; //e.g. {"JP2ECW", "JP2OpenJPEG"};

    // Each thread that tries to access tiles needs its own Dataset handle since GDAL is not thread-safe
    private ConcurrentHashMap<String,Dataset> ds_map = new ConcurrentHashMap<>();

    public GDALImageServer(String path) throws IOException {
        System.gc();

        // GDAL Boilerplate
        gdal.AllRegister();
        gdal.SetCacheMax(Integer.MAX_VALUE);  // Set cache to maximum (~2 GB)
        //gdal.UseExceptions();  // This doesn't work? (bug?)

        ds_path = path;
        Dataset ds = gdal.OpenEx(path, gdalconstConstants.GA_ReadOnly,
                new Vector<>(Arrays.asList(GDAL_ALLOWED_DRIVERS)));
        // Check if that succeeded (since GDAL won't raise native exceptions)
        if (ds == null)
            throw new IOException("GDAL could not open instantiate a Dataset.");
        logger.info("Using GDAL Driver: {}", ds.GetDriver().GetDescription());

        Band band = ds.GetRasterBand(1);
        // Get metadata
        int width = ds.GetRasterXSize();
        int height = ds.getRasterYSize();
        // Not sure if this is getting the correct sizes, but it doesn't seem to be a problem at least.
        int tileWidth = band.GetBlockXSize();
        int tileHeight = band.GetBlockYSize();

        // Can't read these properties so just set to NaN
        double pixelWidth = Double.NaN;
        double pixelHeight = Double.NaN;
        double magnification = Double.NaN;

        // Create metadata objects
        originalMetadata = new ImageServerMetadata.Builder(path, width, height).
                setSizeC(3). // Assume 3 channels (RGB)
                setPreferredTileSize(tileWidth, tileHeight).
                setPixelSizeMicrons(pixelWidth, pixelHeight).
                setMagnification(magnification).
                build();

        // JP2 stores arbitrary power-of-2 downsamples, but try to be consistent with GDAL's "number of overviews"
        int levelCount = band.GetOverviewCount() + 1;
        double[] allDownsamples = new double[levelCount];
        int levelCountFinal = levelCount;

        for (int i = 0; i < levelCount; i++) {
            allDownsamples[i] = Math.pow(2, i);
            // Truncate here to avoid too-pixelated images
            if (allDownsamples[i] >= MAX_DOWNSAMPLE_FACTOR) {
                levelCountFinal = i;
                break;
            }
        }
        downsamples = new double[levelCountFinal];
        System.arraycopy(allDownsamples, 0, downsamples, 0, levelCountFinal);

        logger.info("Downsamples loaded: {}", downsamples, allDownsamples);
        ds.delete();  // don't need the Dataset anymore
    }

    @Override
    public double[] getPreferredDownsamples() {
        return downsamples;
    }

    @Override
    public PathImage<BufferedImage> readRegion(RegionRequest request) {
        return new PathBufferedImage(this, request, readBufferedImage(request));
    }

    @Override
    public void close() {
        // Close all Dataset handles
        for (Dataset ds: ds_map.values()) {
            if (ds != null)
                ds.delete();
        }
        ds_map.clear();
    }

    @Override
    public String getServerType() {
        return "GDAL";
    }

    @Override
    public boolean isRGB() {
        return true; // Only RGB currently supported
    }

    @Override
    public BufferedImage readBufferedImage(RegionRequest request) {
        Rectangle region = AwtTools.getBounds(request);
        double downsampleRequest = request.getDownsample();
        int level = ServerTools.getClosestDownsampleIndex(getPreferredDownsamples(), downsampleRequest);
        double downsampleClosest = getPreferredDownsamples()[level];

        try {
            // Open the dataset at the requested level
            Dataset ds_downsample = getDatasetforThread();
            // Get width/height of downsampled region
            // Always round down final width/height to be consistent with how GDAL sees the overviews
            int ds_w = (int)(request.getWidth() / downsampleClosest);
            int ds_h = (int)(request.getHeight() / downsampleClosest);

            // Read region
            // Note by default GDAL returns bytes array as a BSQ raster if spacing arguments aren't specified, but need
            // BIL for BufferedImage (which can't read BSQ)
            byte[] byteArray = new byte[ds_w * ds_h * 3]; //*3 for RGB
            ds_downsample.ReadRaster(
                    region.x,
                    region.y,
                    region.width,
                    region.height,
                    ds_w,
                    ds_h,
                    gdalconstConstants.GDT_Byte, byteArray, new int[]{1,2,3}, 3, 0, 1);

            // Convert byte array to RGB image
            DataBufferByte buffer = new DataBufferByte(byteArray, byteArray.length);
            WritableRaster raster = Raster.createInterleavedRaster(buffer, ds_w, ds_h, 3 * ds_w, 3,
                    new int[] {0, 1, 2}, null);
            ComponentColorModel cm = new ComponentColorModel(ColorModel.getRGBdefault().getColorSpace(), false,
                    false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
            BufferedImage img = new BufferedImage(cm, raster, true, null);

            // Resize image if needed to the requested downsample factor
            if (!GeneralTools.almostTheSame(downsampleClosest, downsampleRequest, 0.001)) {
                img = resize(img,
                        (int)(request.getWidth() / downsampleRequest + .5),
                        (int)(request.getHeight() / downsampleRequest + .5));
            }
            return img;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private Dataset getDatasetforThread() {
        // Need to create a handle if it doesn't exist yet for this thread
        if (!ds_map.containsKey(Thread.currentThread().getName())) {
            ds_map.put(
                    Thread.currentThread().getName(),
                    gdal.OpenEx(ds_path, gdalconstConstants.GA_ReadOnly,
                            new Vector<>(Arrays.asList(GDAL_ALLOWED_DRIVERS)))
            );
        }
        return ds_map.get(Thread.currentThread().getName());
    }

    private BufferedImage resize(final BufferedImage img, final int finalWidth, final int finalHeight) {
        BufferedImage img2 = new BufferedImage(finalWidth, finalHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = img2.createGraphics();
        g2d.drawImage(img, 0, 0, finalWidth, finalHeight, null);
        g2d.dispose();
        return img2;
    }

    @Override
    public List<String> getSubImageList() {
        return Collections.emptyList();
    }

    @Override
    public String getDisplayedImageName() {
        return getShortServerName();
    }

    @Override
    public boolean usesBaseServer(ImageServer<?> server) {
        return this == server;
    }

    @Override
    public int getBitsPerPixel() {
        return 8;
    }

    @Override
    public boolean containsSubImages() {
        return false;
    }

    @Override
    public Integer getDefaultChannelColor(int channel) {
        return getDefaultRGBChannelColors(channel);
    }

    @Override
    public List<String> getAssociatedImageList() {
        return Collections.emptyList();
    }

    @Override
    public BufferedImage getAssociatedImage(String name) {
        return null;
    }

    @Override
    public File getFile() {
        File file = new File(getPath());
        if (file.exists())
            return file;
        return null;
    }

    @Override
    public double getTimePoint(int ind) {
        return 0;
    }

    @Override
    public ImageServerMetadata getMetadata() {
        return userMetadata == null ? originalMetadata : userMetadata;
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return originalMetadata;
    }

    @Override
    public void setMetadata(ImageServerMetadata metadata) {
        if (!originalMetadata.isCompatibleMetadata(metadata))
            throw new RuntimeException("Specified metadata is incompatible with original metadata for " + this);
        userMetadata = metadata;
    }

}
