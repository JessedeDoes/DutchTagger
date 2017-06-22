package nl.namescape.languageidentification;
import nl.namescape.evaluation.Counter;
import nl.namescape.sentence.JVKSentenceSplitter;
import nl.namescape.sentence.TEISentenceSplitter;
import nl.namescape.tagging.ImpactTaggingClient;
import nl.namescape.tei.TEITagClasses;
import nl.namescape.tokenizer.TEITokenizer;
import nl.namescape.util.Options;
import nl.namescape.util.XML;
import org.ivdnt.openconvert.filehandling.DirectoryHandling;
import org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess;

import org.w3c.dom.*;

import edu.stanford.nlp.util.StringUtils;
import nl.namescape.languageidentification.cybozu.*;


import impact.ee.tagger.BasicTagger;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

/**
 * Simple paragraph-level language identification<br>
 * Now using cybozu labs language identification
 * <p>
 * Problems:
 *<p>
 * Not very good at short paragraphs<br>
 * Uppercase-only text should be lowercased (else almost always recognized as german)
 * 
 * @author does
 *
 */

public class LanguageTagger implements SimpleInputOutputProcess
{
	static String[] priorLanguages = {"nl", "en", "de", "fr", "it", "es", "la"};
        static double[] priorProbabilities = {0.3, 0.3, 0.025, 0.3, 0.025, 0.025, 0.025};
	//static String[] priorLanguages = {"nl", "fr"};
	//static double[] priorProbabilities = {0.6, 0.4};
	String defaultLanguage = "nl";
	
	static HashMap<String,Double> priorMap  = new HashMap<String,Double>();
	String MainLanguage = "nl"; // nl
	boolean usePriors = true;
	boolean tagNTokens = true;

	private Properties properties;
	
	static
	{
		try 
		{
			DetectorFactory.loadProfileFromJar();
			List<String> langs = DetectorFactory.getLangList();
			setPriorProbabilities(langs);
		} catch (LangDetectException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	protected static void setPriorProbabilities(List<String> langs)
	{
		double sum=0;
		for (int i=0; i < priorLanguages.length; i++) 	
			sum += priorProbabilities[i];
			
		for (int i=0; i < priorLanguages.length; i++) 
		{
			String lang= priorLanguages[i];
			priorMap.put(lang,priorProbabilities[i] / sum);
		}
		for (String lang: langs)
		{
			if (priorMap.get(lang) == null)
				priorMap.put(lang,0.0);
		}
	}


	public String detectLanguage(String s)
	{
		try
		{
			Detector detector = DetectorFactory.create();
			if (usePriors)
				detector.setPriorMap(priorMap);
			detector.append(s);
			String lang = detector.detect();
			//detector.getProbabilities();
			return lang;
		} catch (Exception e)
		{
			// e.printStackTrace();
			return null;
		}
	}

	public void deleteXmlLang(Element e)
	{
		e.removeAttribute("xml:lang");
		for (Element x: XML.getAllSubelements(e, false))
			deleteXmlLang(x);
	}
	
	static class LanguageInformation
	{
		Counter<String> counts = new Counter<String>();
		int textLength = 0;
		
		public void aggregate(LanguageInformation other)
		{
			for (String s: other.counts.keyList())
			{
				this.counts.increment(s, other.counts.get(s));
				this.textLength += other.counts.get(s);
			}
		}
		
		public String getMajorityLanguage()
		{
			List<String> keys = counts.keyList();
			if (keys.size() > 0)
			{
				Collections.sort(keys, (l1,l2) -> counts.get(l2).compareTo(counts.get(l1)));
				return keys.get(0);
			} else
			{
				return null;
			}
		}
		
		public String toString()
		{
			return this.getMajorityLanguage() + "/" + this.textLength +  "/" +
		      StringUtils.join(this.counts.keyList().stream().map( (l) -> l + ":"  + counts.get(l)),";");
		}
		
		public Element langInfoElement(Element context)
		{
			Element x = context.getOwnerDocument().createElement("langInfo");
			x.setAttribute("textLength", textLength + "");
			for (String l: this.counts.keyList())
			{
				int c = counts.get(l);
				Element ce = context.getOwnerDocument().createElement("langCharLength");
				ce.setAttribute("language", l);
				ce.setAttribute("count", c + "");
				x.appendChild(ce);
			}
			return x;
		}
	}
	
	public LanguageInformation langStats(Element e)
	{
		System.err.println("find aggregate lang info for "  + e);
		LanguageInformation l = new LanguageInformation();
		String lang = e.getAttribute("xml:lang");
		
		if (lang != null && lang.length() > 0)
		{
			l.textLength = getTextContentExcludingNotesAndHeads(e).length(); // NEE: excluding notes ...
			l.counts.increment(lang,l.textLength);
			System.err.println("Found at base level for " + e.getTagName() + " " + l.toString());
		} else
		{
			for (Element x: XML.getAllSubelements(e, false))
			{
				if (!(x.getTagName().equals("note") || x.getTagName().equals("head")))
				{
					LanguageInformation other = langStats(x);
					l.aggregate(other);
					System.err.println("Aggregate: " + x.getTagName()  + " into " + e.getTagName());
				}
			}

		}
		String mainLang = l.getMajorityLanguage();
		if (mainLang != null)
		{
			e.setAttribute("xml:lang", mainLang);
			if (e.getTagName().startsWith("div") )
			{
				// tag language info in some way

				Element egXML = XML.getElementByTagname(e, "egXML:egXML");
				if (egXML != null && egXML.getParentNode() == e) // ugly
				{
					egXML.appendChild(l.langInfoElement(e));
				} else
				{
					System.err.println("egXML niet gevonden in divje "  + e.getTextContent());
				}
			}
			if (e.getTagName().equals("div2"))
			{
				System.err.println("##### " + l + "\n" + this.getTextContentExcludingNotesAndHeads(e));
			}
		}
		// if (mainLang != null) System.err.println("LangInfo=" + l.toString() + " for " + e.getTagName());
		return l;
	}
	public String getTextContentExcludingNotesAndHeads(Element e)
	{
		if (e.getTagName().equals("note") || e.getTagName().equals("head"))
		{
			return e.getTextContent();
		} else
		{
			NodeList nl = e.getChildNodes();
			String txt="";
			for (int i=0; i < nl.getLength(); i++)
			{
				Node n = nl.item(i);
				if (n.getNodeType() == Node.ELEMENT_NODE)
				{
					Element ne = (Element) n;
					if (ne.getTagName().equals("note") || e.getTagName().equals("head"))
					{
						
					} else
					{
						txt += getTextContentExcludingNotesAndHeads(ne);
					}
				} else if (n.getNodeType() == Node.TEXT_NODE)
				{
					txt += n.getTextContent();
				}
			}
			return txt;
		}
	}
	
	public String tagLanguages(Document d)
	{
		Counter<String> c = new Counter<String>();
		Set<Element> paragraphLike = TEITagClasses.getSentenceSplittingElements(d);
		int L = 0;
		int totalTokens = 0;
		deleteXmlLang(d.getDocumentElement());
		for (Element  z: paragraphLike)
		{
			//System.err.println(z);
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(z);
			if (z.getTagName().contains("div") || z.getTagName().contains("text")) // ugly hack...
				continue;
			String s = getTextContentExcludingNotesAndHeads(z); // should exclude notes ....
			L += s.length();
			
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Paragraph content: " + s);
			
			String lang = detectLanguage(s);
			// System.err.println("detected " + lang + "  for " + z.getTagName() +  ":  " + s);
			int nTokens = TEITagClasses.getWordElements(z).size();
			totalTokens += nTokens;
			if (this.tagNTokens)
				z.setAttribute("n", new Integer(nTokens).toString());
			
			if (lang != null)
			{
				if (lang.equals("af"))
					lang = "nl";
				z.setAttribute("xml:lang", lang);
				c.increment(lang,s.length());
				
				if (!lang.equalsIgnoreCase(MainLanguage) && s.length() > 100)
				{
					System.err.println(lang + " IN " + z);
				}
			} else
			{	
				z.setAttribute("xml:lang", this.defaultLanguage);
				System.err.println("DEFAULT " + " IN " + z);
				// org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("No language found for " + s);
			}
		}
		
		LanguageInformation li = this.langStats(d.getDocumentElement());
		return li.toString();
/*		
		String mainLanguage = "unknown";
		
		for (String lang: c.keyList())
		{
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(lang + "\t"  + c.get(lang));
			if (c.get(lang) > 0.5  *L)
			{
				mainLanguage = lang;
				d.getDocumentElement().setAttribute("xml:lang", mainLanguage);
				if (!lang.equalsIgnoreCase(MainLanguage))
				{
					org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Document has nondutch main lang: "  + lang);
				}
			}
		}
		
		if (mainLanguage.equals("unknown"))
		{
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("No main language found! Text length in chars: " + L);
		}
		return mainLanguage;
*/
	}
	
	@Override
	public void handleFile(String in, String out) 
	{
		
		Document d = null;
		try 
		{
			d = XML.parse(in);
			String li = tagLanguages(d);
			System.out.println(in + "\t" + li);
			
		} catch (Exception e) 
		{
			e.printStackTrace();
			return;
		} 

		try 
		{
			PrintStream pout = new PrintStream(new FileOutputStream(out));
			pout.print(XML.documentToString(d));
			pout.close();
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		
		LanguageTagger xmlTagger = new LanguageTagger();
		DirectoryHandling.usePathHandler = false;
		DirectoryHandling.tagAllFilesInDirectory(xmlTagger, args[0], args[1]);
	}

	@Override
	public void setProperties(Properties properties) 
	{
		// TODO Auto-generated method stub
		this.properties = properties;
	}


	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
}
