package nl.namescape.tagging;

import java.util.Map;

import org.w3c.dom.Element;

import org.ivdnt.openconvert.filehandling.DirectoryHandling;
import org.ivdnt.openconvert.filehandling.MultiThreadedFileHandler;
import impact.ee.lemmatizer.dutch.SimplePatternBasedLemmatizer;
import impact.ee.lexicon.InMemoryLexicon;
import impact.ee.tagger.Tagger;

public class ImpactLemmatizerClient extends ImpactTaggingClient {

	public ImpactLemmatizerClient(Tagger tagger) 
	{
		super(tagger);
		this.tokenize = false;
		// TODO Auto-generated constructor stub
	}

	public void attachToElement(Element e, Map<String,String> m)
	{
		// e.setAttribute("type", tag);
		if (e.getLocalName().equals("w"))
		{
			String lemma = m.get("lemma");
			if (lemma != null)
				e.setAttribute("lemma", lemma);
		}
	}
	
	public static void main(String[] args)
	{
		InMemoryLexicon l = new InMemoryLexicon();
		l.readFromFile(args[0]);
		SimplePatternBasedLemmatizer spbl = new SimplePatternBasedLemmatizer();
		spbl.train(l);
		ImpactLemmatizerClient xmlLemmatizer = new ImpactLemmatizerClient(spbl);
		//MultiThreadedFileHandler m = new MultiThreadedFileHandler(xmlLemmatizer,2); 
		DirectoryHandling.tagAllFilesInDirectory(xmlLemmatizer, args[1], args[2]);
		//m.shutdown();
	}
}
