package nl.namescape.evaluation;
import nl.namescape.util.XML;
public class NEREvaluationTEI {
	public static void main(String[] args)
	{
		try
		{
			TEIDocument d1 = new TEIDocument(XML.parse(args[0]));
			TEIDocument d2 = new TEIDocument(XML.parse(args[1]));
			new NEREvaluation().evaluate(d1, d2);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
