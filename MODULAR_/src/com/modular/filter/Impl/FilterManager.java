package com.modular.filter.Impl;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;














import com.modular.filter.core.IFilter;

import weka.core.Instance;



import ij.ImagePlus;
import ij.ImageStack;
import ij.io.PluginClassLoader;


public class FilterManager {

	private Map<String,IFilter> filterMap= new HashMap<String, IFilter>();
	private Map<Integer,ImageStack> featurStackMap= new HashMap<Integer, ImageStack>();
	private FilterUtil filterUtil= new FilterUtil();
	/** flag to specify the use of color features */
	private  boolean colorFeatures;
	
	private ImagePlus originalImage;

	/** flag to specify the use of the old color format (using directly the RGB values as float) */
	private boolean oldColorFormat = false; 

	public  void loadFilters(String home) throws InstantiationException, IllegalAccessException, 
	IOException, ClassNotFoundException {
		
		// IN ORIGINAL WILL BE LOADED FROM PROPERTY FILE

		File f=new File(home);
		String[] plugins = f.list();

		List<String> classes=new ArrayList<String>();
		for(String plugin: plugins){
			if(plugin.endsWith(".jar"))
			{ 
				classes.addAll(installJarPlugins(home+plugin));
			}
			else if (plugin.endsWith(".class")){
				classes.add(plugin);
			}

		}

		final ClassLoader classLoader= new PluginClassLoader(home);

		for(String plugin: classes){		

			Class<?>[] classesList=(classLoader.loadClass(plugin)).getInterfaces();
			for(Class<?> cs:classesList){

				if(cs.getSimpleName().equals("IFilter")){
					IFilter	thePlugIn =(IFilter) (classLoader.loadClass(plugin)).newInstance(); 

					filterMap.put(thePlugIn.getKey(), thePlugIn);
				}
			}

		}


	}


	public void applyFilters(ImagePlus image){
         checkColorFeatures(image);
		for(int i=1; i<=originalImage.getImageStackSize(); i++){
			List<ImageStack> tempStack= new ArrayList<ImageStack>();
			for(IFilter filter: filterMap.values()){
				ImageStack featureStack= filter.applyFilter(originalImage.getImageStack().getProcessor(i));
				//new ImagePlus("temp", featureStack).show();
				tempStack.add(featureStack);
			}

			System.out.println("temp size"+tempStack.size());
			
			featurStackMap.put(i, combineStacks(tempStack));
		}

	}


	private ImageStack combineStacks(List<ImageStack> imageStackList)
	{
		ImageStack finalStack=new ImageStack(imageStackList.get(0).getWidth(),imageStackList.get(0).getHeight());
		for(ImageStack stack: imageStackList){

			for(int i=1; i<=stack.getSize(); i++){

				finalStack.addSlice(stack.getSliceLabel(i), stack.getProcessor(i));

			}
		}

		return finalStack;
	}

	public Set<String> getFilters(){
		return filterMap.keySet();
	}

	public Map<String,String> getFilterSetting(String key){

		return filterMap.get(key).getDefaultSettings();
	}

	public boolean updateFilterSetting(String key, Map<String,String> settingsMap){

		return filterMap.get(key).updateSettings(settingsMap);
	}

	
	/**
	 * Get the number of features of the reference stack (consistent all along the array)
	 * @return number of features on each feature stack of the array
	 */
	public int getNumOfFeatures() {
		
		return featurStackMap.get(featurStackMap.size()).getSize();
	}
	
	/**
	 * Get a specific label of the reference stack
	 * @param index slice index (>=1)
	 * @return label name
	 */
	public String getLabel(int index)
	{
		return  featurStackMap.get(featurStackMap.size()).getSliceLabel(index);
	}
	
	
	
	// Install plugins located in JAR files. 
	private  List<String> installJarPlugins(String home) throws IOException {

		List<String> classNames = new ArrayList<String>();
		ZipInputStream zip = new ZipInputStream(new FileInputStream(home));
		for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
			if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
				// This ZipEntry represents a class. Now, what class does it represent?
				String className = entry.getName().replace('/', '.'); // including ".class"
				classNames.add(className.substring(0, className.length() - ".class".length()));
			}
		}


		return classNames;
	}

	public ImageStack getImageStack(int sliceNum)
	{
		return featurStackMap.get(sliceNum);
	}

	public Instance createInstance(int x, int y, int classIndex, int sliceNum) {
		
		
		
		return filterUtil.createInstance(x, y, classIndex,
				featurStackMap.get(sliceNum), colorFeatures, oldColorFormat);
	}


	public void checkColorFeatures(ImagePlus image)
	{
		if( image.getType() == ImagePlus.COLOR_RGB)
		{
			originalImage = new ImagePlus("original image", image.getProcessor() );
			colorFeatures = true;
		}
		else
		{
			originalImage = new ImagePlus("original image", image.getProcessor().duplicate());
			colorFeatures = false;
		}
	}

}
