package impact.ee.lemmatizer.dutch;

import impact.ee.lemmatizer.Pattern;
import impact.ee.lemmatizer.dutch.StemChange.RegularStemChange;

public class DutchPattern implements Pattern
{
	StemChange.RegularStemChange stemChange;
	String infix="";
	String inflectionSuffix= "";
	String lemmaSuffix = "";
	
	public DutchPattern(String suffixa, String suffixb, RegularStemChange type) 
	{
		lemmaSuffix = suffixb;
		inflectionSuffix = suffixa;
		stemChange = type;
	}
	
	public String toString()
	{
		return  "{dutchPattern: "  
				+ (infix !=  ""? "(infix= "+ infix + ")":  "") 
				+ (stemChange != RegularStemChange.IDENTITY? stemChange: "") 
				+ " <" + showEmpty(inflectionSuffix) + "~" + showEmpty(lemmaSuffix) + ">}";
	}
	
	private String showEmpty(String s)
	{
		return (s == null || s== "")?"0":s;
	}

	@Override
	public String apply(String s)  // trouble: which possibility for infix removal?
	{
		if (!s.endsWith(inflectionSuffix))
			return null;
		if (inflectionSuffix != null && inflectionSuffix.length() > 0)
			s = s.substring(0, s.length() - inflectionSuffix.length());
		
		//s = s.replaceAll(inflectionSuffix + "$", ""); // probleem kasseistenen -> kasseisten
		StemChange change = StemChange.getStemChange(this.stemChange);
		
		// infix before stemchange???
		
		s = removeInfix(s);
		String s1 = change.transform(s);
		if (s1 == null)
			return null;
		s1 += this.lemmaSuffix;
		
		return s1;
	}

	private String removeInfix(String s1) 
	{
		if (this.infix.length() > 0)
		{
			if (s1.contains(infix +  "ï"))
			{
				s1 = s1.replaceFirst(infix +  "ï", "i"); // hm ugly hack
			} else if (s1.contains(infix +  "ï"))
			{
				s1 = s1.replaceFirst(infix +  "ü", "u"); // hm ugly hack
			} else if (s1.contains(infix +  "ë"))
			{
				s1 = s1.replaceFirst(infix +  "ë", "e"); // hm ugly hack
			} else 
				s1 = s1.replaceFirst(infix, ""); // HM! - this removes  'ge' twice for instance
		}
		return s1;
	}

	@Override
	public String applyConverse(String s) 
	{
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public int hashCode()
	{
		return this.toString().hashCode();
	}
	
	@Override
	public boolean equals(Object other)
	{
		//System.exit(1);
		try 
		{
			DutchPattern op = (DutchPattern) (other);
			boolean b =  this.infix.equals(op.infix) 
					&& this.inflectionSuffix.equals(op.inflectionSuffix)
					&& this.lemmaSuffix.equals(op.lemmaSuffix)
					&& this.stemChange == op.stemChange;
			//org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("?=?" + this + " " + other + " " + b);
			return b;
		} catch (Exception e)
		{
			return false;
		}
	}
}
