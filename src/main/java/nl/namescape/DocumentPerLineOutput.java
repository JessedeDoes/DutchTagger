package nl.namescape;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;

import nl.namescape.sentence.JVKSentenceSplitter;
import nl.namescape.sentence.TEISentenceSplitter;
import nl.namescape.tei.TEITagClasses;
import nl.namescape.tokenizer.TEITokenizer;
import nl.namescape.util.XML;
import org.ivdnt.openconvert.filehandling.DoSomethingWithFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.util.Set;

public class DocumentPerLineOutput implements org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess, DoSomethingWithFile
{
	boolean tagParts = true;
	private Properties properties;
	PrintStream stdout = new PrintStream(System.out);
	boolean printLemmata = false;
	double threshold = -0.5; 
	boolean allSentences = true;
	boolean lowerCase = true;
	
	public void printSentences(Document d, PrintStream out)
	{
		Map<String,Set<String>> metadataMap = nl.namescape.tei.Metadata.getMetadata(d);
		
		List<Element> sentences = nl.namescape.tei.TEITagClasses.getSentenceElements(d);
		int nLines=0;
		int nSkippedLines=0;
		for (Element s: sentences)
		{
			
			List<Element> tokens = 	nl.namescape.tei.TEITagClasses.getWordElements(s);
			boolean first = true;
			String outLine = "";
			int nLowercase=0;
			int nCharacters=0;
			int nWords=0;
			boolean firstIsUpper=false;
			for (Element t: tokens)
			{
				String lemma = t.getAttribute("lemma");
				String token = t.getTextContent();
				nCharacters += token.length();
				if (lowerCase) token = token.toLowerCase();
				for (int i=0; i < token.length(); i++)
				{
					char c  = token.charAt(i);
					if (first && i==0)
					{
						firstIsUpper = Character.isLetter(c) && Character.isUpperCase(c);
					}
					if (Character.isLetter(c) && Character.isLowerCase(c))
						nLowercase++;
				}
				
				outLine += (first? "":" ") + ((printLemmata && lemma != null && lemma.length()>0)?lemma:token);
				first = false;
				nWords++;
			}
			
			if (nWords > 0 && (allSentences || firstIsUpper) && nLowercase / (double) nCharacters > threshold)
			{
				out.print(outLine   + " ");
			} else
				nSkippedLines++;
			nLines++;
		}
		out.print("\n");
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("skipped " + nSkippedLines + " of " + nLines);
		
	}
	
	public static boolean sentenceHasEnoughLowercaseCharacters(Element s)
	{
		List<Element> tokens = 	nl.namescape.tei.TEITagClasses.getTokenElements(s);
		boolean first = true;
		String outLine = "";
		int nLowercase=0;
		int nCharacters=0;
		boolean firstIsUpper=false;
		for (Element t: tokens)
		{
			String token = t.getTextContent();
			nCharacters += token.length();
			
			for (int i=0; i < token.length(); i++)
			{
				char c  = token.charAt(i);
				if (first && i==0)
				{
					firstIsUpper = Character.isLetter(c) && Character.isUpperCase(c);
				}
				if (Character.isLetter(c) && Character.isLowerCase(c))
					nLowercase++;
			}
			
			outLine += (first? "":" ") + token;
			first = false;
		}
		
		return ( nLowercase / (double) nCharacters > 0.7);
	}
	
	@Override
	public void handleFile(String in, String out) 
	{
		try 
		{
			Document d = XML.parse(in);
			PrintStream pout = new PrintStream(new FileOutputStream(out));
			printSentences(d, pout);
			pout.close();
		} catch (Exception e) 
		{
			
			e.printStackTrace();
		}
	}

	@Override
	public void setProperties(Properties properties) 
	{
		// TODO Auto-generated method stub
		this.properties = properties;
	}
	

	@Override
	public void handleFile(String fileName) 
	{
		try 
		{
			Document d = XML.parse(fileName);
			
			printSentences(d, stdout);
			stdout.flush();
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args)
	{
		if (args.length > 1)
		{
			org.ivdnt.openconvert.filehandling.DirectoryHandling.tagAllFilesInDirectory(new DocumentPerLineOutput(), args[0], 
				args[1]);
		} else
		{
			org.ivdnt.openconvert.filehandling.DirectoryHandling.traverseDirectory(new DocumentPerLineOutput(), args[0]);
		}
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}
}
