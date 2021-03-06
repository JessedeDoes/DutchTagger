package impact.ee.lemmatizer;
import impact.ee.classifier.Classifier;
import impact.ee.classifier.Dataset;
import impact.ee.classifier.Distribution;
import impact.ee.classifier.FeatureSet;
import impact.ee.classifier.Instance;
import impact.ee.util.LemmaLog;

import java.io.*;
//import java.util.HashSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
//import weka.classifiers.*;
//import weka.classifiers.functions.*;
//import weka.core.Instance;
import java.util.Properties;




/**
 * One classifier per tag in the paradigm to generate candidate word forms
 */

public class ClassifierSet
{
	HashMap<String, Dataset> datasetsPerTag = new HashMap<String,Dataset>();
	HashMap<String, Classifier> classifiersPerTag = new HashMap<String,Classifier>();
	HashMap<String, Rule> ruleID2Rule = new HashMap<String,Rule>();
	String classifierType = "trees.J48";
	String classifierClassName = "WekaClassifier";// "trees.J48"; // "functions.SMO";
	Class<?> classifierClass = null;
	FeatureSet features = new SimpleFeatureSet();
	public ArrayList<String> tagsSorted = null;
	public FoundFormHandler callback = null;

	int MAX_ITEMS_USED = 10000; // we unfortunately need this because of weka limitations
	double MIN_PROBABILITY = 100000;

	public ClassifierSet()
	{
		try
		{
			classifierClass = Class.forName("weka.classifiers." + classifierType); 
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public ClassifierSet(FeatureSet features, String classifierClassName)
	{
		try
		{
			this.features = features;
			this.classifierClass = Class.forName(classifierClassName);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void buildClassifiers()
	{
		for (String tag: datasetsPerTag.keySet())
		{
			//org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Build classifier for " + tag);
			Dataset d = datasetsPerTag.get(tag);
			Classifier c = null;
			try
			{
				c = (Classifier) classifierClass.newInstance();
				c.setType(classifierType);
			} catch (InstantiationException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			c.setType(classifierType);
			classifiersPerTag.put(tag,c);
			c.train(d, MAX_ITEMS_USED);
		}
		tagsSorted = new ArrayList<String>(datasetsPerTag.keySet());
		Collections.sort(tagsSorted);
	}
	/**
	 * For each full part of speech tag extending the lemma PoS, apply the corresponding
	 * classifier to find the most plausible inflection forms for that tag
	 */

	public void classifyLemma(String lemma, String lemmaPoS)
	{
		for (String tag: tagsSorted)
		{
			classifyLemma(lemma,  lemmaPoS,  tag);
		}
	}

	public void classifyLemma(String lemma, String lemmaPoS, String tag)
	{
		classifyLemma(lemma,  lemmaPoS,  tag, true);
	}
	
	public Set<String> allPossibleLabelsForTag(String tag)
	{
		Set<String> l = new HashSet<String>();
		Dataset d = this.datasetsPerTag.get(tag);
		if (d != null)
		{
			for (Instance x: d.instances)
			{
				l.add(x.classLabel);
			}
		}
		return l;
	}
	
	public void classifyLemma(String lemma, String lemmaPoS, String tag, boolean checkPoS)
	{ 
		this.classifyLemma(lemma,lemmaPoS,tag,checkPoS,this.callback);
	}
	
	public void classifyLemma(String lemma, String lemmaPoS, String tag, boolean checkPoS, FoundFormHandler theCallback)
	{
		if (tag == null)
		{
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("HEY: tag = null for " + lemma);
		}
		if (!tag.startsWith(lemmaPoS))
		{
			return; // doe dit anders!
		}
		Classifier classifier = classifiersPerTag.get(tag);
		if (classifier == null)
		{
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Error: no classifier trained for "  + tag);
			return;
		}
		
		
		Instance testItem = features.makeTestInstance(lemma);

		Distribution outcomes = classifier.distributionForInstance(testItem);
		// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(outcomes);
		outcomes.sort();
		double cumulativeP = 0;

		for (int rank=0; rank < outcomes.size();  rank++)
		{ 
			// Problem: the pattern suggested by the classifier need not be applicable to the given lemma
			// TODO: solve this by using a different classifier (or pruning the decision trees)

			String classId = outcomes.get(rank).label;
			double p = outcomes.get(rank).p;
			Rule r = ruleID2Rule.get(classId);
			LemmaLog.addToLog("rule for " + lemma + ":  " + r.toString());
			cumulativeP += p;
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(r + " " + lemmaPoS + " "  + r.lemmaPoS);
			if (p > cumulativeP/MIN_PROBABILITY && (!checkPoS || r.lemmaPoS.equals(lemmaPoS)))
			{
				if (theCallback != null)
				{
					theCallback.foundForm(lemma, tag, lemmaPoS, r, p, rank);
				} else
				{
					String wf = r.pattern.applyConverse(lemma);
					if (wf != null)
					{
						System.out.println(String.format("%s\t%s\t%s\t%s\t%f\t[%d]\t%s=%s",wf,lemma,tag,lemmaPoS, p,rank,classId,r.toString()));
					}
				}
			}
		}
	}

	public void addItem(String pos, String lemma, String ruleID, Rule rule)
	{ 
		Dataset d = datasetsPerTag.get(pos);
		if (d == null)
		{
			d = new Dataset(pos);
			d.features = this.features;
		}
		ruleID2Rule.put(ruleID,rule);
		datasetsPerTag.put(pos,d);
		d.addInstance(lemma,ruleID);
	}

	public void saveToDirectory(String dirName)
	{
		int k=1;
		try
		{
			Properties p = new Properties();
			p.setProperty("directory",dirName);
			p.setProperty("class",classifierType);
			for (String tag: datasetsPerTag.keySet())
			{
				//Dataset d = datasetsPerTag.get(tag);
				Classifier c = classifiersPerTag.get(tag);
				String fileName = String.format("%s/M%d.model", dirName, k); 
				p.setProperty(String.format("M%d.model",k), tag);
				c.save(fileName);
				k++;
			}
			for (String s: ruleID2Rule.keySet())
			{
				p.setProperty(s,ruleID2Rule.get(s).toString());
			}
			p.store(new FileOutputStream(dirName + "/model.properties"), "no comments");
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
