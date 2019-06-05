package org.esa.s2tbx.lib.openjpeg;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * Created by jcoravu on 30/4/2019.
 */
public abstract class AbstractMarkerSegment implements IMarkers {

	protected AbstractMarkerSegment() {
	}
	
	public abstract void readData(DataInputStream jp2FileStream) throws IOException;
}
