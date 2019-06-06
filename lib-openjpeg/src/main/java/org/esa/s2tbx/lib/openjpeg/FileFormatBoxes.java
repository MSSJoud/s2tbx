/*
 * $RCSfile: FileFormatBoxes.java,v $
 * $Revision: 1.1 $
 * $Date: 2005/02/11 05:02:10 $
 * $State: Exp $
 *
 * Class:                   FileFormatMarkers
 *
 * Description:             Contains definitions of boxes used in jp2 files
 *
 *
 *
 * COPYRIGHT:
 *
 * This software module was originally developed by Raphaël Grosbois and
 * Diego Santa Cruz (Swiss Federal Institute of Technology-EPFL); Joel
 * Askelöf (Ericsson Radio Systems AB); and Bertrand Berthelot, David
 * Bouchard, Félix Henry, Gerard Mozelle and Patrice Onno (Canon Research
 * Centre France S.A) in the course of development of the JPEG2000
 * standard as specified by ISO/IEC 15444 (JPEG 2000 Standard). This
 * software module is an implementation of a part of the JPEG 2000
 * Standard. Swiss Federal Institute of Technology-EPFL, Ericsson Radio
 * Systems AB and Canon Research Centre France S.A (collectively JJ2000
 * Partners) agree not to assert against ISO/IEC and users of the JPEG
 * 2000 Standard (Users) any of their rights under the copyright, not
 * including other intellectual property rights, for this software module
 * with respect to the usage by ISO/IEC and Users of this software module
 * or modifications thereof for use in hardware or software products
 * claiming conformance to the JPEG 2000 Standard. Those intending to use
 * this software module in hardware or software products are advised that
 * their use may infringe existing patents. The original developers of
 * this software module, JJ2000 Partners and ISO/IEC assume no liability
 * for use of this software module or modifications thereof. No license
 * or right to this software module is granted for non JPEG 2000 Standard
 * conforming products. JJ2000 Partners have full right to use this
 * software module for his/her own purpose, assign or donate this
 * software module to any third party and to inhibit third parties from
 * using this software module for non JPEG 2000 Standard conforming
 * products. This copyright notice must be included in all copies or
 * derivative works of this software module.
 *
 * Copyright (c) 1999/2000 JJ2000 Partners.
 *
 */
package org.esa.s2tbx.lib.openjpeg;

/**
 * Created by jcoravu on 30/4/2019.
 */
public interface FileFormatBoxes {

    /** JP2 Box Types */
	public static final int JP2_SIGNATURE_BOX = 0x6a502020;

	public static final int JP2_SIGNATURE_BOX_CONTENT = 0x0D0A870A; // <CR><LF><0x87><LF> (0x0D0A 870A)
	
    public static final int FILE_TYPE_BOX       = 0x66747970;

    public static final int JP2_HEADER_BOX   = 0x6a703268;

    public static final int CONTIGUOUS_CODESTREAM_BOX = 0x6a703263;
    
    public static final int INTELLECTUAL_PROPERTY_BOX = 0x64703269;
    
    public static final int XML_BOX                   = 0x786d6c20;

    public static final int UUID_BOX                  = 0x75756964;

    public static final int UUID_INFO_BOX             = 0x75696e66;

    /** JPX Box Types */
    public static final int ASSOCIATION_BOX             = 0x61736f63;

    /** File Type Fields */
    public static final int FT_BR = 0x6a703220;
}
