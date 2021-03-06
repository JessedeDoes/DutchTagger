package impact.ee.lexicon;

import impact.ee.trie.DoubleArrayTrie;
import impact.ee.util.Options;

import java.io.FileNotFoundException;
import java.io.IOException;
//import java.util.Collections;
//import java.util.HashSet;
//import java.util.Set;
import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

//import org.apache.commons.io.FileUtils;


/*
 * create a new directory (per language) containing
 * The pattern files (patterns.txt)
 * Subdirectories for modern lexicon and historical lexicon database
 * Trie for modern lexicon
 */

public class LexiconUtils 
{
	public static boolean prepareLexiconData(String targetDirectory, String patternFilename, 
			String modernLexiconFilename,
			String historicalLexiconFilename)
	{
		NeoLexicon modernLexicon = new NeoLexicon(targetDirectory + "/ModernLexicon", true);
		modernLexicon.readWordsFromFile(modernLexiconFilename);

		NeoLexicon historicalLexicon = new NeoLexicon(targetDirectory + "/HistoricalLexicon", true);
		historicalLexicon.readWordsFromFile(historicalLexiconFilename);

		boolean addWordBoundaries = Options.getOptionBoolean("addWordBoundaries",false);	
		DoubleArrayTrie dat = new DoubleArrayTrie();
		int k=0;
		SortedSet<String> words = new TreeSet<String>();
		
		for (WordForm w: modernLexicon)
		{
			if (k % 10000 == 0) 
				org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(k + ": " +  w);
			words.add(w.wordform);
			k++;
		}
		k=0;
		for (String w: words)
		{
			if (k % 10000 == 0) org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("to trie: " + k + ": " +  w);

			dat.add(addWordBoundaries? "^" + w + "$": w);

			dat.add(w);
			k++;
		}
		
		try 
		{
			dat.saveToFile(targetDirectory + "/modernWords.datrie");
		} catch (FileNotFoundException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		//dat.r
		return true;
	}

	// wordform lemma PoS lemmaPOS lemmafrequency wordformfrequency
	
	// toDo no lemma Id parsed!
	public static WordForm getWordformFromLine(String s) 
	{
		String[] parts = s.split("\t");
		if (parts.length < 3)
			return null;
		WordForm w = new WordForm();
		w.wordform = parts[0]; 
		w.lemma = parts[1];
		w.tag = parts[2];

		if (parts.length > 3)
		{
			w.lemmaPoS = parts[3];
		} else
		{
			w.lemmaPoS = w.tag;
		}	
		try
		{
			if (parts.length > 4)
			{
				w.lemmaFrequency = Integer.parseInt(parts[4]);
				if (w.lemmaFrequency > 10000) // Huh???/
				{
					org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(s);
					//System.exit(1);
				}
			}
			if (parts.length > 5)
				w.wordformFrequency = Integer.parseInt(parts[5]);
		} catch (Exception e)
		{
			//e.printStackTrace();
		}
		return w;
	}

	public static void prepareModernToHistoricalMatching(ILexicon modernLexicon, ILexicon historicalLexicon)
	{
		int k=1;
		for (WordForm w: historicalLexicon)
		{
			boolean exactMatch = false;
			//org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(w);
			Set<WordForm> candidates = modernLexicon.findForms(w.lemma, w. tag);
			Set<String> forms = new HashSet<String>();
			for (WordForm c: candidates)
			{
				if (c.wordform.equalsIgnoreCase(w.wordform))
					exactMatch = true;
				forms.add(c.wordform);
			}
			if (!exactMatch && candidates.size() > 0)
			{
				System.out.println(k++ + "\t"  + 
						impact.ee.util.StringUtils.join(forms, " ") + "\t" +  w.wordform);
			}
		}
	}
	
	public static void main(String [] args)
	{
		new Options(args);
		String spanishDir = "c:/IREval/Data/Spanish";
		LexiconUtils.prepareLexiconData(
				Options.getOption("targetDirectory", "C:/Temp/SpanishLexicon"), 
				Options.getOption("patternInput", spanishDir + "/patterns.txt"), 
				Options.getOption("modernLexicon", spanishDir + "/ModernLexicon.txt"), 
				Options.getOption("historicalLexicon", spanishDir + "/HistoricalLexicon.txt"));

	}
}
