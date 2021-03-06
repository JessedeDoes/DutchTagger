package impact.ee.tagger.features;


import impact.ee.classifier.*;
import impact.ee.lexicon.*;
import impact.ee.ner.gazetteer.Gazetteer;
import impact.ee.tagger.Context;
import impact.ee.util.TabSeparatedFile;

import java.io.ObjectInputStream;
import java.util.*;


/*
 * Since the map is serialized with the feature, there is really no
 * reason to re-read it, is there??
 */

public class ClusterFeature extends Feature
{
	private static final long serialVersionUID = 1L;

	// maybe it is better not to have a transient map, just store the stuff in the model?

	public Map<String,String> word2cluster = new HashMap<String,String>();
	private boolean initialized = false;

	static final String SandersClusterFile = 
			"resources/exampledata/brownClusters.from.sanders.txt";

	static Map<String, Map<String,String>> lexiconMap = 
			new  HashMap<String, Map<String,String>>();

	public int k;
	public int depth;

	public ClusterFeature(String fileName, int depth, int k)
	{
		this.name = "cluster_" + depth + "_" + k;
		this.k = k;
		this.depth = depth;
		initLexicon(fileName);
	}

	public String getValue(Object o)
	{
		try
		{
			Context c = (Context) o;
			String s = word2cluster.get(c.getAttributeAt("word", k));
			if (s != null && s.length() > depth)
			{
				return s.substring(0,depth);
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	public void readClustersFromFile()
	{
		initLexicon(SandersClusterFile);
	}

	public void initLexicon(String fileName)
	{

		if (lexiconMap.containsKey(fileName))
			word2cluster = lexiconMap.get(fileName);
		else
		{
			if (!initialized)
			{
				try
				{
					org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("reading cluster file " + fileName);
					word2cluster = readClusters(fileName);
					lexiconMap.put(fileName, word2cluster);
				} catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
		initialized = true;
	}

	private Map<String,String> readClusters(String fileName)
	{
		String[] fields = {"cluster", "word"};
		Map<String,String> clusterMap = new HashMap<String,String>();
		TabSeparatedFile f = new TabSeparatedFile(fileName,fields, true);
		while (f.getLine() != null)
		{
			clusterMap.put(f.getField("word"), f.getField("cluster"));
		}
		return clusterMap;
	}

	private void readObject(ObjectInputStream in) throws java.io.IOException, ClassNotFoundException
	{
		in.defaultReadObject();
		//readClustersFromFile();
	}
}
