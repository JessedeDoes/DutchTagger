package nl.namescape.tagging;

import java.util.Map;
import java.util.Properties;

import nl.namescape.tei.TEINameTagging;
import nl.namescape.util.Options;
import org.ivdnt.openconvert.filehandling.DirectoryHandling;
import org.ivdnt.openconvert.filehandling.MultiThreadedFileHandler;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import impact.ee.tagger.NamePartTagger;
import impact.ee.tagger.Tagger;

public class ImpactNERTaggingClient extends ImpactTaggingClient 
{

	public ImpactNERTaggingClient()
	{
		
	}
	
	public ImpactNERTaggingClient(Tagger tagger) 
	{
		super(tagger);
	}
	
	@Override
	public void attachToElement(Element e, Map<String,String> m)
	{
		// e.setAttribute("type", tag);
		String tag = m.get("tag");
		if (tag != null)
			e.setAttribute("neLabel", tag);
		tag = m.get("namePartTag");
		if (tag != null)
			e.setAttribute("nePartLabel", tag);
	}
	
	@Override 
	public void postProcess(Document d)
	{
		(new TEINameTagging()).realizeNameTaggingInTEI(d);
	}
	
	public void setProperties(Properties p)
	{
		this.properties = p;
		// 2016: pass properties here!
		Tagger namePartTagger = 
				NamePartTagger.getNamePartTagger(p.getProperty("nerModel"), p.getProperty("partModel"), p);
		this.tagger = namePartTagger;
	}
	
	public static void main(String[] args)
	{
		nl.namescape.util.Options options = 
				new nl.namescape.util.Options(args);
		args = options.commandLine.getArgs();
		 int nThreads = Runtime.getRuntime().availableProcessors()-1;
		Tagger namePartTagger = 
				NamePartTagger.getNamePartTagger(args[0], args[1], options.properties);
		ImpactNERTaggingClient x = new ImpactNERTaggingClient(namePartTagger);
		x.tokenize = options.getOptionBoolean("tokenize", true);
		MultiThreadedFileHandler m = new MultiThreadedFileHandler(x,nThreads);
		DirectoryHandling.usePathHandler = false;
		DirectoryHandling.traverseDirectory(m, args[2], args[3],null);
		m.shutdown();
	}
}
