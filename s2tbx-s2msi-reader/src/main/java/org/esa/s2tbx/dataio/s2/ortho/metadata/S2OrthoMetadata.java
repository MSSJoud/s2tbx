package org.esa.s2tbx.dataio.s2.ortho.metadata;

import org.esa.s2tbx.dataio.s2.S2BandConstants;
import org.esa.s2tbx.dataio.s2.S2BandInformation;
import org.esa.s2tbx.dataio.s2.S2Config;
import org.esa.s2tbx.dataio.s2.S2Metadata;
import org.esa.s2tbx.dataio.s2.S2SpatialResolution;
import org.esa.s2tbx.dataio.s2.S2SpecificBandConstants;
import org.esa.s2tbx.dataio.s2.Sentinel2ProductReader;
import org.esa.s2tbx.dataio.s2.VirtualPath;
import org.esa.s2tbx.dataio.s2.ortho.filepatterns.S2OrthoGranuleDirFilename;
import org.esa.snap.core.util.SystemUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Denisa Stefanescu
 */
public abstract class S2OrthoMetadata extends S2Metadata {

    private VirtualPath path;
    private String granuleName;
    private boolean foundProductMetadata;

    protected Logger logger = SystemUtils.LOG;

    protected S2OrthoMetadata(VirtualPath path, String granuleName, boolean foundProductMetadata, S2Config config) {
        super(config);
        this.path = path;
        this.granuleName = granuleName;
        this.foundProductMetadata = foundProductMetadata;
    }

    public VirtualPath getPath() {
        return path;
    }

    public String getGranuleName() {
        return granuleName;
    }

    public boolean isFoundProductMetadata() {
        return foundProductMetadata;
    }

    public  List<Sentinel2ProductReader.BandInfo> computeBandInfoByKey(List<Tile> tileList) throws IOException {
        List<Sentinel2ProductReader.BandInfo> bandInfoList = new ArrayList<>();
        S2Metadata.ProductCharacteristics productCharacteristics = getProductCharacteristics();
        // Verify access to granule image files, and store absolute location
        for (S2BandInformation bandInformation : productCharacteristics.getBandInformations()) {
            HashMap<String, VirtualPath> tilePathMap = new HashMap<>();
            for (S2Metadata.Tile tile : tileList) {
                S2OrthoGranuleDirFilename gf = S2OrthoGranuleDirFilename.create(tile.getId());
                if (gf != null) {
                    String imageFileName = computeFileName(isFoundProductMetadata(), productCharacteristics, tile, bandInformation, gf);

                    logger.finer("Adding file " + imageFileName + " to band: " + bandInformation.getPhysicalBand());

                    boolean bFound = false;
                    VirtualPath virtualPath = path.getParent().resolve(imageFileName);
                    if (virtualPath.exists()) {
                        tilePathMap.put(tile.getId(), virtualPath);
                        bFound = true;
                    } else {
                        VirtualPath parentPath = virtualPath.getParent();
                        if (parentPath != null && parentPath.exists()) { //Search a sibling containing the physicalBand name
                            S2BandConstants bandConstant = S2BandConstants.getBandFromPhysicalName(bandInformation.getPhysicalBand());
                            if (bandConstant != null) {
                                VirtualPath[] otherPaths = parentPath.listPaths(bandConstant.getFilenameBandId());
                                if (otherPaths != null && otherPaths.length == 1) {
                                    tilePathMap.put(tile.getId(), otherPaths[0]);
                                    bFound = true;
                                } else if (otherPaths != null && otherPaths.length > 1) {
                                    VirtualPath pathWithResolution = filterVirtualPathsByResolution(otherPaths, bandInformation.getResolution().resolution);
                                    if (pathWithResolution != null) {
                                        tilePathMap.put(tile.getId(), pathWithResolution);
                                        bFound = true;
                                    }
                                }
                            } else { //try specific bands
                                S2SpecificBandConstants specificBandConstant = S2SpecificBandConstants.getBandFromPhysicalName(bandInformation.getPhysicalBand());
                                if (specificBandConstant != null) {
                                    VirtualPath[] otherPaths = parentPath.listPaths(specificBandConstant.getFilenameBandId());
                                    if (otherPaths != null && otherPaths.length == 1) {
                                        tilePathMap.put(tile.getId(), otherPaths[0]);
                                        bFound = true;
                                    } else if (otherPaths != null && otherPaths.length > 1) {
                                        VirtualPath pathWithResolution = filterVirtualPathsByResolution(otherPaths, bandInformation.getResolution().resolution);
                                        if (pathWithResolution != null) {
                                            tilePathMap.put(tile.getId(), pathWithResolution);
                                            bFound = true;
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!bFound) {
                        logger.warning(String.format("Warning: missing file %s\n", virtualPath.getFullPathString()));
                    }
                }
            }
            if (!tilePathMap.isEmpty()) {
                Sentinel2ProductReader.BandInfo bandInfo = createBandInfoFromHeaderInfo(bandInformation, tilePathMap);
                if (bandInfo != null) {
                    bandInfoList.add(bandInfo);
                }
            } else {
                logger.warning(String.format("Warning: no image files found for band %s\n", bandInformation.getPhysicalBand()));
            }
        }
        return  bandInfoList;
    }

    private VirtualPath filterVirtualPathsByResolution(VirtualPath[] paths, int resolution) {
        boolean found = false;
        VirtualPath resultPath = null;
        if (paths == null) {
            return null;
        }
        for (VirtualPath virtualPath : paths) {
            if (virtualPath.getFileName().toString().contains(String.format("%dm", resolution))) {
                if (found) {
                    return null;
                }
                found = true;
                resultPath = virtualPath;
            }
        }
        return resultPath;
    }

    private String computeFileName(boolean foundProductMetadata, S2Metadata.ProductCharacteristics productCharacteristics, Tile tile, S2BandInformation bandInformation, S2OrthoGranuleDirFilename gf) {
        String imageFileName;
        String separator = path.getSeparator();
        String imageFileTemplate = bandInformation.getImageFileTemplate()
                .replace("{{TILENUMBER}}", gf.getTileID())
                .replace("{{MISSION_ID}}", gf.missionID)
                .replace("{{SITECENTRE}}", gf.siteCentre)
                .replace("{{CREATIONDATE}}", gf.creationDate)
                .replace("{{ABSOLUTEORBIT}}", gf.absoluteOrbit)
                .replace("{{DATATAKE_START}}", productCharacteristics.getDatatakeSensingStartTime())
                .replace("{{RESOLUTION}}", String.format("%d", bandInformation.getResolution().resolution));
        if (foundProductMetadata) {
            VirtualPath vp = resolveResource(tile.getId());
            String fileName = vp.getFileName().toString();
            imageFileName = String.format("GRANULE%s%s%s%s", separator, fileName, separator, imageFileTemplate);
        } else {
            imageFileName = imageFileTemplate;
        }
        return imageFileName;
    }

    private Sentinel2ProductReader.BandInfo createBandInfoFromHeaderInfo(S2BandInformation bandInformation, Map<String, VirtualPath> tilePathMap) {
        S2SpatialResolution spatialResolution = bandInformation.getResolution();
        if (getConfig().getTileLayout(spatialResolution.resolution) == null) {
            return null;
        }
        return new Sentinel2ProductReader.BandInfo(tilePathMap, bandInformation, getConfig().getTileLayout(spatialResolution.resolution));
    }

}
