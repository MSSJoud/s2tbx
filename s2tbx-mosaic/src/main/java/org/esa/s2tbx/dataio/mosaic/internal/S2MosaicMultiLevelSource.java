package org.esa.s2tbx.dataio.mosaic.internal;

import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelModel;
import com.bc.ceres.glevel.support.DefaultMultiLevelSource;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.gpf.common.MosaicOp;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.math.MathUtils;

import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.BorderDescriptor;
import javax.media.jai.operator.ConstantDescriptor;
import javax.media.jai.operator.CropDescriptor;
import javax.media.jai.operator.MosaicDescriptor;
import javax.media.jai.operator.TranslateDescriptor;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.esa.snap.core.dataop.resamp.Resampling.Index.crop;

/**
 * A single banded multi-level image source for Sentinel2 products
 *
 * @author Razvan Dumitrascu
 * @since 5.0.2
 */
public final class S2MosaicMultiLevelSource extends AbstractMultiLevelSource {
    private final Band[]sourceBands;
    private final int imageWidth;
    private final int imageHeight;
    private final int tileWidth;
    private final int tileHeight;
    private final int dataType;
    private final String mosaicType;
    private final Logger logger;
    private final double originX;
    private final double originY;
    private final int productType;

    public S2MosaicMultiLevelSource(Band[] sourceBands, int imageWidth, int imageHeight,
                                    int tileWidth, int tileHeight, int levels, int dataType,
                                    AffineTransform transform, String mosaicType, double originX, double originY, int productType) {
        super(new DefaultMultiLevelModel(levels,
                transform,
                imageWidth, imageHeight));
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.sourceBands = sourceBands;
        this.dataType = dataType;
        this.mosaicType = mosaicType;
        this.originX = originX;
        this.originY = originY;
        this.productType = productType;

        this.logger = Logger.getLogger(S2MosaicMultiLevelSource.class.getName());
    }

    /**
     * Creates a planar image corresponding of source band, at the specified resolution.
     *
     * @param bandIndex   The index of the sourceBand (0-based)
     *
     */
    protected PlanarImage createTileImage(final int bandIndex, final int level) throws IOException {
        return (PlanarImage) this.sourceBands[bandIndex].getSourceImage().getImage(level);
    }

    @Override
    protected RenderedImage createImage(int level) {

        double scaleFactor = 1.0 /Math.pow(2, level);
        final List<RenderedImage> tileImages = Collections.synchronizedList(new ArrayList<>(this.sourceBands.length));

        for (int index = 0; index < this.sourceBands.length; index++) {
            PlanarImage opImage;
            try {
                final AffineTransform affineTransformSourceBand =  sourceBands[index].getSourceImage().getModel().getImageToModelTransform(0);
                sourceBands[index].getGeoCoding().getMapCRS().getDomainOfValidity();
                opImage = createTileImage(index, level);
                if (opImage != null) {
                    //compute the origin of the source bands so that it can be determined where
                    // to place them in relation to the target product
                    final double sourceBandOriginX  = affineTransformSourceBand.getTranslateX();
                    final double sourceBandOriginY =  affineTransformSourceBand.getTranslateY();
                    final double sourceBandStepSize = affineTransformSourceBand.getScaleX();
                    opImage = TranslateDescriptor.create(opImage,
                            (float)((MathUtils.floorInt((sourceBandOriginX - this.originX)/sourceBandStepSize))*scaleFactor),
                            (float)((MathUtils.floorInt((sourceBandOriginY - this.originY)/sourceBandStepSize))*scaleFactor),
                            Interpolation.getInstance(Interpolation.INTERP_NEAREST),
                            null);
                }
            } catch (IOException e) {
                opImage = ConstantDescriptor.create((float) this.tileWidth, (float) this.tileHeight, new Number[]{0}, null);
            }
            /*if((opImage.getMinX()<0)&&(opImage.getMinY()<= 0)) {
                opImage = CropDescriptor.create(opImage,
                        (float) Math.abs(opImage.getMinX()), 0.0f,
                        (float) (opImage.getWidth() - Math.abs(opImage.getMinX())), (float) opImage.getHeight(),
                        null);
            }else if((opImage.getMinX()>=0)&&(opImage.getMinY()>0)){
                opImage = CropDescriptor.create(opImage,
                        0.0f, (float) opImage.getMinY(), (float) opImage.getWidth(),
                        (float) (opImage.getHeight()- Math.abs(opImage.getMinY())),
                        null);
            }else if((opImage.getMinX()<0)&&(opImage.getMinY()> 0)){
                opImage = CropDescriptor.create(opImage,
                        (float)Math.abs(opImage.getMinX()), (float) opImage.getMinY(),
                        (float) (opImage.getWidth()- Math.abs(opImage.getMinX())),
                        (float) (opImage.getHeight() - Math.abs(opImage.getMinY())),
                        null);
            }
*/
            tileImages.add(opImage);
        }
        if (tileImages.isEmpty()) {
            logger.warning("No tile images for mosaic");
            return null;
        }

        ImageLayout imageLayout = new ImageLayout();
        imageLayout.setMinX(0);
        imageLayout.setMinY(0);
        imageLayout.setTileWidth(MathUtils.floorInt(JAI.getDefaultTileSize().width*scaleFactor));
        imageLayout.setTileHeight(MathUtils.floorInt(JAI.getDefaultTileSize().height*scaleFactor));
        imageLayout.setTileGridXOffset(0);
        imageLayout.setTileGridYOffset(0);
        RenderedOp mosaicOp;

        switch(this.mosaicType) {
            case "MOSAIC_TYPE_BLEND":
                mosaicOp = MosaicDescriptor.create(tileImages.toArray(new RenderedImage[tileImages.size()]),
                        MosaicDescriptor.MOSAIC_TYPE_BLEND,
                        null, null,(this.productType==1)? new double[][]{{0.0}} : null, new double[] {0.0},
                        new RenderingHints(JAI.KEY_IMAGE_LAYOUT, imageLayout));
                break;
            case "MOSAIC_TYPE_OVERLAY":
                mosaicOp = MosaicDescriptor.create(tileImages.toArray(new RenderedImage[tileImages.size()]),
                        MosaicDescriptor.MOSAIC_TYPE_OVERLAY,
                        null, null, (this.productType==1)? new double[][]{{0.0}} : null, new double[] {0.0},
                        new RenderingHints(JAI.KEY_IMAGE_LAYOUT, imageLayout));
                break;
            default:
                throw new IllegalArgumentException("Mosaic type not accepted");
        }

        final int fittingRectWidth = scaleValue(this.imageWidth, level);
        final int fittingRectHeight = scaleValue(this.imageHeight, level);

        Rectangle fitRect = new Rectangle(0, 0, MathUtils.floorInt(fittingRectWidth*scaleFactor), MathUtils.floorInt(fittingRectHeight*scaleFactor));
        final Rectangle destBounds = DefaultMultiLevelSource.getLevelImageBounds(fitRect,scaleFactor);

        BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_ZERO);
        int rightPad = 0;
        if(mosaicOp.getWidth()<destBounds.getWidth() && mosaicOp.getMaxX()<destBounds.getWidth() && mosaicOp.getMaxX()>0) {
            rightPad = (int) destBounds.getMaxX() - mosaicOp.getMaxX();
        }
        int bottomPad =0;
        if(mosaicOp.getHeight()<destBounds.getHeight() && mosaicOp.getMinY()<destBounds.getHeight() && (Math.abs(mosaicOp.getMinY())+mosaicOp.getHeight())<destBounds.getHeight()) {
            bottomPad = (int) (destBounds.getMaxY() - mosaicOp.getMaxY() + mosaicOp.getMinY());
        }
        int topPad = 0;
        if((mosaicOp.getMinY()<0) &&(Math.abs(mosaicOp.getMinY())<destBounds.getHeight())){
            topPad = Math.abs(mosaicOp.getMinY());
        }
        int leftPad = 0;
        if(mosaicOp.getMinX()>0 && mosaicOp.getMinX()<destBounds.getWidth()){
            leftPad= mosaicOp.getMinX();
        }
        mosaicOp = BorderDescriptor.create(mosaicOp, leftPad, rightPad, topPad, bottomPad, borderExtender, null);

        return mosaicOp;
    }

    @Override
    public synchronized void reset() {
        super.reset();
    }

    private int scaleValue(int source, int level) {
        int size = source >> level;
        int sizeTest = size << level;
        if (sizeTest < source) {
            size++;
        }
        return size;
    }
}
