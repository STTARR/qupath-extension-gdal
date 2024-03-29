# qupath-extension-gdal

## Background

**This extension supports the latest stable release of QuPath (v0.1.2). For now, it is not compatible with the newest v0.2.0 milestones due to API changes.**

[QuPath](https://qupath.github.io/) is a free open-source whole slide image analysis platform. It ships with two "image servers" which enable reading of images: OpenSlide (built-in) and BioFormats (as an [optional extension](https://github.com/qupath/qupath-bioformats-extension)). OpenSlide does not support JPEG2000/JP2 (.jp2) slides and Bio-Formats has support [only for small images](https://www.openmicroscopy.org/community/viewtopic.php?f=13&t=8329). This extension adds support for JPEG2000 whole slide images using GDAL (Geospatial Data Abstraction Library). It is adapted from QuPath's built-in OpenSlide image server.

## Prerequisites

- [QuPath v0.1.2](https://github.com/qupath/qupath/releases/tag/v0.1.2)
- GDAL with Java bindings and a JPEG2000 driver (most builds should come with JP2OpenJPEG by default). GDAL does not offer binary releases, but a [variety of other maintainers](https://trac.osgeo.org/gdal/wiki/DownloadingGdalBinaries) do. Here are some suggestions for where to look to obtain these:
    - **Windows**: [GISInternals](http://www.gisinternals.com/index.html) (since OSGeo4W does not ship with Java bindings). **Important**: The GISInternals build of GDAL depends on `iconv.dll` which is also needed by OpenSlide but the versions they are shipped with are not compatible. Due to the way Windows handles .dll loading, integrating GDAL into QuPath will break OpenSlide support. You can avoid this by building GDAL from source to a single .dll file (or just keep a separate copy of QuPath for loading JP2 files specifically).
    - **Mac**: [KyngChaos](http://www.kyngchaos.com/software/frameworks/) (unconfirmed)
    - **Linux**: Your package manager, most likely. On Ubuntu, the relevant packages are `gdal-bin`, `libgdal20` and `libgdal-java`.
    - Alternatively, build GDAL from source: https://trac.osgeo.org/gdal/wiki/BuildHints.
As long as the GDAL Java methods required in this extension have not changed in between versions, the version of GDAL should not matter (although there may be some performance differences depending on the optimization of the individual drivers). This extension is compiled against the GDAL 2.4.0 Java binding but also works fine on 2.2.3 (the latest version available on Ubuntu 18.04LTS).

You can check that your copy of GDAL includes a JP2 driver using the bundled gdalinfo executable and running `gdalinfo --formats`.

## Installation

These instructions will mainly refer to the Windows GDAL build from GISInternals but should be fairly similar on Mac/Linux (except that library extensions will be `.so` instead of `.dll`).

1. Copy `gdalxxx.dll` (where `xxx` is the version number) and any additional `.dll` dependencies (in most cases this should be all `.dll` files that exist in the same folder) from your GDAL release to the QuPath `QuPath/app` folder. 
2. Also copy `gdalalljni.dll` to the same folder, which is part of the GDAL Java binding.
4. Install this extension [as usual](https://github.com/qupath/qupath/wiki/Extensions) as well as `gdal.jar`.
5. Confirm installation by executing `QuPath.exe` and ensure GDAL Builder is listed under *Help* -> *Installed extensions*. If you open a JP2 image and go to the image tab, the *Server type* property should say GDAL.

**Important**: If the QuPath Bio-Formats extension is installed, make sure to disable .jp2 files in the option *Never used Bio-Formats for specified image extensions*, otherwise BioFormats may take precedence.

## Usage notes

- Slide metadata (e.g. magnification, pixel resolution, label image) is not supported. I'm not certain how to read this data or whether the metadata tags are standardized enough across different scanners for it to make sense. QuPath will still work fine in the absence of this data (pixel units are used instead).
- Unlike traditional pyramid-based formats (e.g. TIFF, Aperio SVS), JP2 images do not store image overviews as part of the image. Rather, the decoder algorithm is able to efficiently render power-of-two downsamples, resulting in a smaller file size. Unfortunately decompression is very CPU-intensive, so unlike those other formats, having a decent multi-core processor will help greatly to improve decoding speeds.
- When initially adding slides into a new project, QuPath attempts to retrieve thumbnails for all imported images, which blocks tile retrieval. Until all thumbnails are generated, QuPath will appear to lag when opening an image. Once thumbnails are generated, it should be quite fast however.
- GDAL's default JP2 driver is JP2OpenJPEG, which is based on the open-source OpenJPEG project, and should be adequately fast. Alternative proprietary JP2 drivers are available. JP2ECW is free of charge for decoding purposes, although will require additional steps which may involve building from source: see https://trac.osgeo.org/gdal/wiki/ECW. Experimentally it has been faster versus JP2OpenJPEG. Other drivers are JP2MrSID (similarly free) and JP2KAK ($). If you decide to use alternative drivers, you may need to specify the driver in the option *Allow specific GDAL drivers (advanced)* to override GDAL's default driver load order.

## Building the extension from source

Note the extension needs to be built using the Java 8 JDK (to match QuPath v0.1.2). A Gradle build file is included. Specify the directory to the `QuPath/app/qupath` as shown below:

```
gradle build -Pqpdir=/path/to/QuPath/app/qupath
```

After successful completion, the extension will be in `build/libs`.