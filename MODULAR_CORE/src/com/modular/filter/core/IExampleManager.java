package com.modular.filter.core;

import java.util.List;

import ij.gui.Roi;

public interface IExampleManager {

	public void addExample(int classNum, Roi roi, int n) ;
	public void addExampleList(int classNum, List<Roi> roi, int n) ;
	public void deleteExample(int classNum, int nSlice, int index);
	public List<Roi> getExamples(int classNum, int n);
	public String[] getClassLabels();
	public void setClassLabel(int classNum, String label);
	public void setNumOfClasses(int numOfClasses);
	public int getNumOfClasses();
	public List<Roi> loadZippedExample(String path, int classNum, int sliceNumber);
}
