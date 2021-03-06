package impact.ee.lemmatizer.clitics;

import impact.ee.classifier.Classifier;
import impact.ee.classifier.Dataset;
import impact.ee.classifier.FeatureSet;
import impact.ee.classifier.Instance;
import impact.ee.classifier.libsvm.LibSVMClassifier;
import impact.ee.classifier.weka.WekaClassifier;
import impact.ee.lemmatizer.SimpleFeatureSet;
import impact.ee.util.TabSeparatedFile;

import java.util.HashMap;
import java.util.Map;


public class CliticFinder 
{
	// SMO best so far.. (0.18)
	
	// WekaClassifier weka = new WekaClassifier("functions.SMO", true);
	Classifier classifier = new LibSVMClassifier();
	Dataset d;
	Map<String,String> explanations = new HashMap<String,String>();
	String[] fieldsInTrainingData = {"word", "classLabel", "classDescription"};
	FeatureSet features =  new SimpleFeatureSet();
	
	public void train(Dataset d)
	{
		classifier.train(d);
	}
	
	public void train(String fileName)
	{
		Dataset d = new Dataset("my.data");
		d.features = this.features = new SimpleFeatureSet();
		
		TabSeparatedFile t = new TabSeparatedFile(fileName, fieldsInTrainingData);
		
		while (t.getLine() != null)
		{
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(t.getField("word"));
			d.addInstance(t.getField("word"), t.getField("classLabel"));
			explanations.put( t.getField("classLabel"),  
					t.getField("classDescription"));
		}
		
		train(d);
	}
	
	public void test(String fileName)
	{
		TabSeparatedFile t = new TabSeparatedFile(fileName, fieldsInTrainingData);
		int nItems=0;
		int nErrors=0;
		
		while (t.getLine() != null)
		{
			String s = t.getField("word");
			Instance i = this.features.makeInstance(s, "UNKNOWN");
			String answer = classifier.classifyInstance(i);
			String truth = t.getField("classLabel");
			if (!truth.equals(answer))
			{
				nErrors++;
				System.out.println(t.getField("word") + "\t" + answer + "\t"  +
						explanations.get(answer) + "\t" + t.getField("classDescription"));
			}
			nItems++;
		}
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("foutpercentage: " + (nErrors / (double) nItems));
		//train(d);
	}
	
	public static void main(String[] args)
	{
		CliticFinder c = new CliticFinder();
		c.train(args[0]);
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("done training......");
		c.test(args[1]);
	}
}
