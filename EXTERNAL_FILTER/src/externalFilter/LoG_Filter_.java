package externalFilter;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.DialogListener;
import ij.plugin.filter.ExtendedPlugInFilter;
import ij.plugin.filter.PlugInFilterRunner;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ijaux.scale.GScaleSpace;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;






import com.modular.filter.core.IFilter;

import dsp.Conv;

/**
 * @version 	1.1	14 Oct 2013
 * 				- moved contratAdjust -> Conv
 * 				- changed brightness adjustment factor to sigma^2		
 * 				1.1 	18 Jul 2013
 * 				- refactoring
 * 				1.0		05 Feb 2013 
 * 				Based on Mexican_Hat_Filter v 2.2
 * 				- common functionality is refactored in a library class
 * 				
 *   
 * 
 * @author Dimiter Prodanov
 * 		  IMEC
 *
 *
 * @contents
 * This pluign convolves an image with a Mexican Hat (Laplacian of Gaussian, LoG) filter
 * 
 * 
 * @license This library is free software; you can redistribute it and/or
 *      modify it under the terms of the GNU Lesser General Public
 *      License as published by the Free Software Foundation; either
 *      version 2.1 of the License, or (at your option) any later version.
 *
 *      This library is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *       Lesser General Public License for more details.
 *
 *      You should have received a copy of the GNU Lesser General Public
 *      License along with this library; if not, write to the Free Software
 *      Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


public class LoG_Filter_ implements ExtendedPlugInFilter, DialogListener,IFilter {

	private PlugInFilterRunner pfr=null;

	private final int flags=DOES_ALL+SUPPORTS_MASKING+KEEP_PREVIEW;
	private String version="2.1";
	private int nPasses=1;

	public static boolean debug=IJ.debugMode;
	public final static String SIGMA="LOG_sigma", LEN="G_len",MAX_LEN="G_MAX", ISSEP="G_SEP", SCNORM="G_SCNORM";

	private  int sz= Prefs.getInt(LEN, 9);
	private  int max_sz= Prefs.getInt(MAX_LEN, 16);
	public  boolean sep= Prefs.getBoolean(ISSEP, true);

	public  boolean scnorm= Prefs.getBoolean(SCNORM, false);

	
	private ImagePlus image=null;	
	private boolean isFloat=false;	
	private boolean hasRoi=false;
	private Object pixundo;
	private boolean convert=false;
	
	
	/* NEW VARIABLES*/
	
	/** A string key identifying this factory. */
	private final  String FILTER_KEY = "LOG2";

	/** The pretty name of the target detector. */
	private final String FILTER_NAME = "Laplacian of Gaussian2";
	
	private Map< String, String > settings= new HashMap<String, String>();
	
	private ImageStack imageStack;


	/**
	 * 
	 */
	/* (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp) {
		image=imp;
		isFloat= (image.getType()==ImagePlus.GRAY32);
		hasRoi=imp.getRoi()!=null;
		return  flags;
	}

	/*
	 * (non-Javadoc)
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor ip) {

		FloatProcessor fp=filter(ip,sz,sep,scnorm);
		image.setProcessor(fp);
		image.updateAndDraw();
	}

	/**
	 * Apply filter to input image (in place)
	 * @param inputImage input image
	 * @param size kernel size (it must be odd)
	 * @param nAngles number of angles
	 * @return false if error
	 */
	public ImageStack applyFilter(ImageProcessor ip){
	
		 imageStack=new ImageStack(ip.getWidth(),ip.getHeight());
			for (int sigma=sz; sigma<= max_sz; sigma +=2){					
			ImageProcessor fp=filter(ip.duplicate(), sigma,sep, scnorm);
			imageStack.addSlice( FILTER_KEY+"_" + sigma, fp);		
			}
		
	return imageStack;
	}


	public FloatProcessor filter(ImageProcessor ip,final int size, final boolean seperable,final boolean snorm){
		float[][] kernel=null;
		ip.snapshot();

		if (!isFloat) 
			ip=ip.toFloat(0, null);
		int r = size;//(sz-1)/2;
		GScaleSpace sp=new GScaleSpace(r);
		float[] kernx= sp.gauss1D();
		GScaleSpace.flip(kernx);		
		float[] kern_diff= sp.diff2Gauss1D();
		GScaleSpace.flip(kern_diff);

		System.out.println("scnorm "+snorm);
		if (snorm) {
			double gamma=sp.getSigma(); 	 
			for (int i=0; i<kern_diff.length; i++) {
				kern_diff[i]=(float) (kern_diff[i]*gamma);
				kernx[i]=(float) (kernx[i]*gamma);
			}
		}
		kernel=new float[3][];
		kernel[0]=kernx;
		kernel[1]=kern_diff;

		float[] kernel2=sp.computeDiff2Kernel2D();
		if (snorm) {
			double gamma=sp.getScale();
			for (int i=0; i<kern_diff.length; i++) {
				kernel2[i]=(float) (kernel2[i]*gamma);
			}
		}
		kernel[2]=kernel2;
		GScaleSpace.flip(kernel2);  // symmetric but this is the correct way

		int sz= sp.getSize();
		long time=-System.nanoTime();	
		FloatProcessor fpaux= (FloatProcessor) ip;

		Conv cnv=new Conv();
		if (seperable) {
			cnv.convolveSemiSep(fpaux, kernx, kern_diff);			
		} else {		 
			cnv.convolveFloat(fpaux, kernel2, sz, sz);
		}

		time+=System.nanoTime();
		time/=1000.0f;
		System.out.println("elapsed time: " + time +" us");
		fpaux.resetMinAndMax();	

		if (convert) {

			final double d1=0;
			final double dr=sp. getScale();	
			System.out.println("linear contrast adjustment y=ax+b \n " +
					" b= " +d1 +" a= " + dr);

			Conv.contrastAdjust(fpaux, dr, d1);
		}

		return fpaux;


	}

	

	/* (non-Javadoc)
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus, java.lang.String, ij.plugin.filter.PlugInFilterRunner)
	 */
	@Override
	public int showDialog(ImagePlus imp, String command, PlugInFilterRunner pfr) {
		this.pfr = pfr;
		int r = (sz-1)/2;
		GenericDialog gd=new GenericDialog("Mex. Hat " + version);
		gd.addNumericField("hw", r, 1);
		gd.addCheckbox("Show kernel", debug);
		gd.addCheckbox("Separable", sep);
		gd.addCheckbox("Scale normalize", scnorm);
		if (hasRoi)
			gd.addCheckbox("Brightness correct", true);

		gd.addPreviewCheckbox(pfr);
		gd.addDialogListener(this);
		gd.showDialog();

		pixundo=imp.getProcessor().getPixelsCopy();
		if (gd.wasCanceled()) {			
			return DONE;
		}

		return IJ.setupDialog(imp, flags);
	}



	// Called after modifications to the dialog. Returns true if valid input.
	/* (non-Javadoc)
	 * @see ij.gui.DialogListener#dialogItemChanged(ij.gui.GenericDialog, java.awt.AWTEvent)
	 */
	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e) {
		sz = (int)(gd.getNextNumber());
		debug = gd.getNextBoolean();
		sep = gd.getNextBoolean();
		scnorm = gd.getNextBoolean();
		convert=gd.getNextBoolean();
		if (gd.wasCanceled()) {
			ImageProcessor proc=image.getProcessor();
			proc.setPixels(pixundo);
			proc.resetMinAndMax();
			return false;
		}
		return sz>0;
	}


	/* (non-Javadoc)
	 * @see ij.plugin.filter.ExtendedPlugInFilter#setNPasses(int)
	 */
	@Override
	public void setNPasses (int nPasses) {
		this.nPasses = nPasses;
	}

	/* Saves the current settings of the plugin for further use
	 * 
	 *
	 * @param prefs
	 */
	public void savePreferences(Properties prefs) {
		prefs.put(LEN, Integer.toString(sz));
		prefs.put(ISSEP, Boolean.toString(sep));
		prefs.put(SCNORM, Boolean.toString(scnorm));

	}

	@Override
	public Map<String, String> getDefaultSettings() {
		
		settings.put(LEN, Integer.toString(sz));
		settings.put(MAX_LEN, Integer.toString(max_sz));
		settings.put(ISSEP, Boolean.toString(sep));
		settings.put(SCNORM, Boolean.toString(scnorm));
		
		return this.settings;
	}

	@Override
	public boolean updateSettings(Map<String, String> settingsMap) {
		sz=Integer.parseInt(settingsMap.get(LEN));
		max_sz=Integer.parseInt(settingsMap.get(MAX_LEN));
		sep=Boolean.parseBoolean(settingsMap.get(ISSEP));
		scnorm=Boolean.parseBoolean(settingsMap.get(SCNORM));
	
		return true;
	}

	@Override
	public String getKey() {
		return this.FILTER_KEY;
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return this.FILTER_NAME;
	}
	

	/**
	 * Get stack size
	 * @param sliceNum
	 * @return number of slices in the stack
	 */
	@Override
	public int getSize(){
		return imageStack.getSize();
	}
	/**
	 * Get slice label
	 * @param index slice index (from 1 to max size)
	 * @return slice label
	 */
	@Override
	public String getSliceLabel(int index){
		return imageStack.getSliceLabel(index);
	}
	/**
	 * Get stack height
	 * @return stack height
	 */
	@Override
	public int getHeight(){
		return imageStack.getHeight();
	}
	/**
	 * Get stack width
	 * @return stack width
	 */
	@Override
	public int getWidth(){
		return imageStack.getWidth();
	}


}
