package activeSegmentation.learning;

import ij.IJ;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import weka.core.Instances;
import activeSegmentation.IClassifier;
import activeSegmentation.IDataSet;

public class ApplyClassifier {

	private IClassifier iclassifier;
	private List<IDataSet> testDataSet;
	private ForkJoinPool pool; 


	public ApplyClassifier(IClassifier iclassifier, List<IDataSet> testDataSet) {
		this.iclassifier = iclassifier;
		this.testDataSet = testDataSet;
		pool=  new ForkJoinPool();
	}

	public List<double[]> applyClassifier(){
		List<double[]> classificationList= new ArrayList<double[]>();
		for(IDataSet dataSet: testDataSet){

			double[] classificationResult = new double[dataSet.getNumInstances()];
			ApplyTask applyTask= new ApplyTask(dataSet, 0, dataSet.getNumInstances(), classificationResult, iclassifier);
			pool.invoke(applyTask);
			classificationList.add(classificationResult);
		}
		return classificationList;
	}

	public void generateProbabilityMask(){

	}




}
