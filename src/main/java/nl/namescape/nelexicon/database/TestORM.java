package nl.namescape.nelexicon.database;

import impact.ee.lexicon.LexiconDatabase;
import impact.ee.lexicon.WordForm;

import java.util.*;

import nl.namescape.nelexicon.NELemma;
public class TestORM 
{
	public static List<Object> testRead()
	{
		LexiconDatabase ldb = new LexiconDatabase("impactdb", "EE3_5");
		String table = "lemmata";
		ObjectRelationalMapping orm = 
				new ObjectRelationalMapping(NELemma.class, "analyzed_wordforms");
		
		orm.addField("modern_lemma", "lemma");
		orm.addField("wordform", "wordform");
		orm.addField("lemma_part_of_speech", "lemmaPoS");
		orm.addField("persistent_id", "lemmaID");
		orm.setPrimaryKeyField("primaryKey");
	
		List<Object> objects = 
			orm.fetchObjects(ldb.connection);
		
		for (Object o: objects)
		{
			//System.out.println(o);
		}
		return objects;
	}
	
	public static void testWrite()
	{
		LexiconDatabase ldb = new LexiconDatabase("impactdb", "ORMTEST");
		String table = "lemmata";
		
		ObjectRelationalMapping orm = 
				new ObjectRelationalMapping(WordForm.class, "lemmata");
		orm.addField("modern_lemma", "lemma");
		//orm.addField("wordform", "wordform");
		orm.addField("lemma_part_of_speech", "lemmaPoS");
		orm.addField("persistent_id", "lemmaID");
		orm.setPrimaryKeyField("primaryKey");
		WordForm w = new WordForm();
		
		w.lemma = "ondersteboven";
		w.lemmaPoS = "ADP";
		w.lemmaID="M000000";
		
		WordForm w1 = new WordForm();
		w1.lemma = "boven";
		w1.lemmaPoS = "ADP";
		w1.lemmaID = "M000001";
		
		List<Object> wl  = new ArrayList<Object>();
		
		wl.add(w);
		wl.add(w1);
		
		orm.insertObjects(ldb.connection, "lemmata", wl);
		
		for (Object o: wl)
		{
			try
			{
				org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(orm.primaryKeyField.get(o) + " --> " + o);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		//List<Object> nogwat = testRead();
		//orm.insertObjects(ldb.connection, "lemmata", nogwat);
	
	}
	
	public static void main(String[] args)
	{
		testWrite();
	}
}
