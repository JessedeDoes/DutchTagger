package impact.ee.tagger;

import impact.ee.classifier.Classifier;
import impact.ee.classifier.Dataset;
import impact.ee.classifier.Feature;
import impact.ee.classifier.FeatureSet;
import impact.ee.classifier.Instance;
import impact.ee.classifier.libsvm.LibSVMClassifier;
import impact.ee.classifier.svmlight.SVMLightClassifier;
import impact.ee.tagger.features.*;
import impact.ee.util.Pair;
import impact.ee.util.Serialize;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.*;




/**
 * BasicTagger. (now a NER tagger).
 * segmentation is reasonable (best on ned.testa is now 87%)
 * but classification is really miserable.
 * suppose classification is especially bad for multiwords entities as
 * everything is decided by the first one now....
 * so ... first segment,
 * then (re)classify???
 *
 */

public class BasicNERTagger implements Serializable, Tagger
{
	private static final long serialVersionUID = 1L;

	FeatureSet features = new FeatureSet();
	Classifier classifier = new SVMLightClassifier(); // .svmlight.SVMLightClassifier();
	boolean useFeedback = true;
	boolean useLexicon = false;
	boolean doeEvenRaar = false;
	boolean useShapes = true;
	boolean useVectors = true;
	Set<String> knownWords = new HashSet<String>();
	double proportionOfTrainingToUse = 1;
	public String taggedAttribute = "tag";
	
	public static String[] defaultAttributeNames = {"word", "tag"};
	public String[] attributeNames = defaultAttributeNames;
	public boolean onlyUseContextInsideSentence = true;
	
	public void setClassifier(String className)
	{
		try
		{
			Class c = Class.forName(className);
			this.classifier = (Classifier) c.newInstance();
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/*
	 * why not wrap the row in one object
	 */
	
	public void loadModel(String fileName)
	{
		fileName += "." + this.classifier.getClass().getName();
		Pair<Classifier,FeatureSet> 
			p = new Serialize<Pair<Classifier,FeatureSet>>().loadFromFile(fileName);
		this.classifier = p.first;
		this.features = p.second;
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("model loaded from " + fileName + " for "  + this.getClass().getName());
	}
	
	public void saveModel(String fileName)
	{
		try 
		{
			fileName += "." + this.classifier.getClass().getName();
			Pair<Classifier,FeatureSet> p = new Pair<Classifier,FeatureSet>(classifier,features);
			new Serialize<Pair<Classifier,FeatureSet>>().saveObject(p, fileName);
		} catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// to do 2016: handle properties like in BasicTagger
	public BasicNERTagger(Properties p)
	{
		if (p.getProperty("tagLexicon") != null)
		{
			TaggerFeatures.setLexiconFileName(p.getProperty("tagLexicon"));
		}
		
		if (p.getProperty("word2vecFile") != null)
		{
			WordVectorFeature.SonarVectors =  p.getProperty("word2vecFile");
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("vector file name set to " +WordVectorFeature.SonarVectors );
		}
		String fileName = p.getProperty("modelFileName");
		this.loadModel(fileName);
	}
	
	public BasicNERTagger() // wrong setup: features should not be initialized in this way.
	{
		// features.addFeature(new GazetteerFeature(GazetteerFeature.LOC));
	}
	
	public BasicNERTagger(boolean create) // wrong setup: features should not be initialized in this way.
	{
		if (create)
			initializeFeatures();
		// features.addFeature(new GazetteerFeature(GazetteerFeature.LOC));
	}

	protected void initializeFeatures() {
		features = TaggerFeatures.getMoreFeatures(useFeedback, false);
		if (useLexicon)
		{
			features.addStochasticFeature(new HasTagFeature(0));
			features.addStochasticFeature(new HasTagFeature(-1));
			features.addStochasticFeature(new HasTagFeature(1));
			//context potential PoS does not appear to contribute much
			//features.addStochasticFeature(new LexiconBasedFeature.HasPoSFeature(1));
			//features.addStochasticFeature(new LexiconBasedFeature.HasPoSFeature(-1));
		}
		if (useShapes)
		{
			Set<Feature> shapeFeatures = ShapeFeature.getShapeFeatures();
			for (Feature f: shapeFeatures)
				features.addFeature(f);
		}
		if (useVectors)
		{
			features.addStochasticFeature(new WordVectorFeature(0));
		}
	}
	
	public void examine(Corpus statsCorpus)
	{
		features.gatherStatistics((Iterator<Object>) statsCorpus);
	}
	
	public void train(Corpus trainingCorpus)
	{		
		Dataset d = new Dataset("trainingCorpus");
		Dataset classificationSet = new Dataset("classification");
		d.features = features;
		
		for (Context c: trainingCorpus.enumerate())
		{
			if (Math.random() <= proportionOfTrainingToUse)
			{
				if (filter(c))
				{
					String answer = c.getAttributeAt(taggedAttribute, 0);
					if (answer == null)
						continue;
					if (doeEvenRaar)
					{
						boolean x = c.getAttributeAt("word",0).matches("^[A-Z]");
						answer = answer + "/" + x;
					}
					d.addInstance(c, answer);
					if (answer.startsWith("B-"))
					{
						
					}
					knownWords.add(c.getAttributeAt("word", 0));
				}
			}
		}
		
		features.finalize(); // oehoeps, dit is niet fijn, dat dat expliciet moet, moet anders...
		
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("start training, "  + d.size() + " items");
		
		// hier zou je de dataset moeten prunen om
		// irrelevante features (te weinig voorkomende f,v combinaties) weg te gooien
		// d.pruneInstances();
		
		classifier.train(d);
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("finish training...");
	}
	
	protected boolean filter(Context c) 
	{
		// TODO Auto-generated method stub
		return true;
	}
	
	public SimpleCorpus tag(Corpus testCorpus)
	{
		 Enumeration<Map<String,String>> output = new OutputEnumeration(this, testCorpus);
		 EnumerationWithContext<Map<String,String>> ewc = 
				 new EnumerationWithContext(Map.class, output, new DummyMap());
		 return new SimpleCorpus(ewc);
	}
	
	public void test(Corpus testCorpus)
	{
		int nItems=0; int nErrors=0;
		
		int nUnknownItems=0; int nUnknownErrors=0;
		
		long startTime =  System.currentTimeMillis();
		for (Context c: testCorpus.enumerate())
		{
			if (!filter(c))
				continue;
			impact.ee.classifier.Instance instance = features.makeTestInstance(c);
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(features.itemToString(item));
			String truth = c.getAttributeAt(taggedAttribute, 0);
			if (truth == null)
			{
				System.out.print("\n");
				continue;
			}
			String word = c.getAttributeAt("word", 0);
			boolean known = knownWords.contains(word);
		
			String outcome = classifier.classifyInstance(instance);
			if (doeEvenRaar)
				outcome = outcome.replaceAll("/.*",  "");
			
			if (useFeedback)
			{
				c.setAttributeAt(taggedAttribute, outcome, 0);
			}
					
			if (!truth.equals(outcome))
			{
				nErrors++;
				if (!known) nUnknownErrors++;
			}
			
		
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(c.getAttributeAt("word", 0) + " " + outcome);
			nItems++;
			if (nItems % 100 ==0)
			{
				org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(features.itemToString(instance));
				org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("nItems: " + nItems + " errors: "  + nErrors / (double) nItems);
			}
			if (!known)
			{
				nUnknownItems++;
			}
			Boolean correct = truth.equals(outcome);
			System.out.println(word + "\t" + outcome + "\t" + truth + "\t"  + correct);
		}
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("nItems: " + nItems + 
				" errors: "  + nErrors / (double) nItems);
		// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("n unknown tems: " + nUnknownItems + 
		//	" errors: "  + nUnknownErrors / (double) nUnknownItems);
		
		long endTime = System.currentTimeMillis();
		long interval = endTime - startTime;
		double secs = interval / 1000.0;
		double wps = nItems / secs;
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("tokens " + nItems);
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("seconds " + secs);
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("tokens per second " + wps);
	}
	
	public static class Trainer
	{
		public static void main(String[] args)
		{
			BasicNERTagger t = new BasicNERTagger(true);
			SimpleCorpus statsCorpus = new SimpleCorpus(args[0], t.attributeNames);
			t.examine(statsCorpus);
			SimpleCorpus trainingCorpus = new SimpleCorpus(args[0], t.attributeNames); // set chunking if needed ?
			if (t.onlyUseContextInsideSentence)
				trainingCorpus.setChunking(true);
			t.train(trainingCorpus);
			t.saveModel(args[1]); 
		}
	}
	
	public static class Tester
	{
		public static void main(String[] args)
		{
			Properties p = new Properties();
			p.put("modelFileName", args[0]);
			BasicNERTagger t = new BasicNERTagger(p);
			SimpleCorpus testCorpus = new SimpleCorpus(args[1], t.attributeNames); // set chunking if needed
			if (t.onlyUseContextInsideSentence)
				testCorpus.setChunking(true);
			//t.loadModel(args[0]);
			t.test(testCorpus);
		}
	}
	
	@Override
	public HashMap<String, String> apply(Context c) 
	{
		// TODO Auto-generated method stub
		
		HashMap<String,String> m = new HashMap<String,String>();
		//m.put("word", c.getAttributeAt("word", 0));
		
		for (String key: c.getAttributes())
		{
			m.put(key, c.getAttributeAt(key, 0));
		}
		
		if (filter(c))
		{
			impact.ee.classifier.Instance instance = features.makeTestInstance(c);
			String outcome = classifier.classifyInstance(instance);
			m.put(taggedAttribute, outcome);
			if (useFeedback)
			{
				c.setAttributeAt(taggedAttribute, outcome, 0);
			}
		} else
		{
			c.setAttributeAt(taggedAttribute, "O", 0);
		}
		return m;
	}
	
	
	public static void main(String[] args)
	{
		BasicNERTagger t = new BasicNERTagger(true);
		// 
		t.useFeedback = true;
		t.useLexicon = true;
		
		boolean doTraining = false;
		
		if (doTraining)
		{
			SimpleCorpus statsCorpus = new SimpleCorpus(args[0], t.attributeNames);
			t.examine(statsCorpus);
			SimpleCorpus trainingCorpus = new SimpleCorpus(args[0], t.attributeNames);
			t.train(trainingCorpus);
			t.saveModel("Models/basicTagger");
		}
		
		SimpleCorpus testCorpus = new SimpleCorpus(args[1], t.attributeNames);
		t.loadModel("Models/basicTagger");
		t.test(testCorpus);
	}

	@Override
	public void setProperties(Properties properties) 
	{
		// TODO Auto-generated method stub
	}
}
