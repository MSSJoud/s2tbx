package org.esa.s2tbx.dataio.s2;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.esa.snap.core.dataio.geocoding.ComponentFactory;
import org.esa.snap.core.dataio.geocoding.ComponentGeoCoding;
import org.esa.snap.core.dataio.geocoding.ForwardCoding;
import org.esa.snap.core.dataio.geocoding.GeoChecks;
import org.esa.snap.core.dataio.geocoding.GeoRaster;
import org.esa.snap.core.dataio.geocoding.InverseCoding;
import org.esa.snap.core.dataio.geocoding.forward.PixelForward;
import org.esa.snap.core.dataio.geocoding.inverse.PixelQuadTreeInverse;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.ProductData;

import ucar.ma2.Array;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

public class ECMWFTReader {

    List<Band> bands;


    public ECMWFTReader(Path path,Path cachedir) throws IOException {
        if(bands!=null)
            bands.clear();
        bands = new ArrayList<>();
        NetcdfFile ncfile = null;
        System.out.println("getECMWFBands cachedir: "+cachedir.toAbsolutePath());
        Path cacheFolderPath = cachedir;
        cacheFolderPath = cacheFolderPath.resolve("aux_ecmfwt");
        try{
            Files.createDirectory(cacheFolderPath);
        }catch(FileAlreadyExistsException exc){
        }
        Path copyPath = cacheFolderPath.resolve(path.getFileName());
        try {
            System.out.println("cacheFolderPath: "+cacheFolderPath.toAbsolutePath());
            Files.copy(path, copyPath, StandardCopyOption.REPLACE_EXISTING);
            ncfile = NetcdfFile.openInMemory(copyPath.toString());
            bands.add(getBand(ncfile, "Total_column_water_vapour_surface"));
            bands.add(getBand(ncfile, "Total_column_ozone_surface"));
            bands.add(getBand(ncfile, "Mean_sea_level_pressure_surface"));
        } catch (IOException ioe) {
            // Handle less-cool exceptions here
            ioe.printStackTrace();
        }catch (Exception e) {
            // Handle less-cool exceptions here
            e.printStackTrace();
        }  finally {
            ncfile.close();
            System.out.println("FileUtils.deleteDirectory ");
            FileUtils.deleteDirectory(cacheFolderPath.toFile());
        }
    }

    public List<Band> getECMWFBands() throws IOException {
        return bands;
    }


    public Band getBand(NetcdfFile ncfile, String name) throws IOException {
       
        Variable variable = ncfile.findVariable(null, name);
        Variable lon = ncfile.findVariable(null, "lon");
        Variable lat = ncfile.findVariable(null, "lat");
        String description = variable.getFullName();
        String units = variable.getUnitsString();
        int[] shape = variable.getShape();
        Band band = new Band(name,ProductData.TYPE_FLOAT64,shape[1],shape[2]);
        band.setUnit(units);
        band.setDescription(description);
        band.setDescription(description);
        band.setNoDataValue(Double.NaN);
        band.setNoDataValueUsed(true);
        System.out.println("getGlobalAttributes : "+ncfile.getGlobalAttributes());
        System.out.println("lon.read() : "+lon.read());
        System.out.println("lat.read() : "+lat.read());

        double[] longitude = (double[])lon.read().get1DJavaArray(double.class);
        double[] longitudeLine = (double[])lon.read().get1DJavaArray(double.class);
        for(int i=1;i<shape[2];i++)
        {
            // longitude = ArrayUtils.addAll(longitude, longitudeLine);
            System.arraycopy(longitudeLine, 0, longitude, longitude.length, longitudeLine.length);
        }
        
        double[] latitude = new double[shape[1]];
        Arrays.fill(latitude, lat.read().getDouble(0));
        for(int i=1;i<shape[2];i++)
        {
            double[] latitudeLine = new double[shape[1]];
            Arrays.fill(latitudeLine, lat.read().getDouble(i));
            // latitude = ArrayUtils.addAll(latitude, latitudeLine);
            System.arraycopy(latitudeLine, 0, latitude, latitude.length, latitudeLine.length);
        }
        final ForwardCoding forward = ComponentFactory.getForward(PixelForward.KEY);
        final InverseCoding inverse = ComponentFactory.getInverse(PixelQuadTreeInverse.KEY);
        final GeoRaster geoRaster = new GeoRaster(longitude,latitude, "lon", "lat", shape[1],shape[2], 12.5);
        final ComponentGeoCoding geoCoding = new ComponentGeoCoding(geoRaster, forward, inverse, GeoChecks.NONE);
        geoCoding.initialize();
        System.out.println("longitude: "+ArrayUtils.toString(longitude));
        System.out.println("latitude: "+ArrayUtils.toString(latitude));
        System.out.println("geoCoding 0 0 : "+geoCoding.getGeoPos(new PixelPos(0,0), null));
        System.out.println("geoCoding 8 8 : "+geoCoding.getGeoPos(new PixelPos(8,8), null));

        
        band.setGeoCoding(geoCoding);

        ProductData tileData = ProductData.createInstance(ProductData.TYPE_FLOAT64,shape[1]*shape[2]);
        Array raster = variable.read();
        
        for(int i=0;i<raster.getSize();i++)
        {
            tileData.setElemFloatAt(i, raster.getFloat(i));
        }
        band.setRasterData(tileData);     
        return band;
    }
}
