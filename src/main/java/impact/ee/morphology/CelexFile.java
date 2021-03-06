package impact.ee.morphology;
import impact.ee.util.TabSeparatedFile;

import java.util.*;

/**
 *  *<pre>
 *lichtbaden	licht@N+bad@N+en@N|NINFLWB	licht@N+bad@N+en@N|NINFLWB	m
lichtbak	licht@N+bak@NWB	licht@N+bak@NWB	e
lichtbaken	licht@N+baken@NWB	licht@N+baken@NWB	e
lichtbakens	licht@N+baken@N+s@N|NINFLWB	licht@N+baken@N+s@N|NINFLWB	m
lichtbakken	licht@N+bakk@N+en@N|NINFLWB	licht@N+bak@N+en@N|NINFLWB	m
lichtband	licht@A+band@NWB	licht@A+band@NWB	e
lichtbanden	licht@A+band@N+en@N|NINFLWB	licht@A+band@N+en@N|NINFLWB	m
lichtbeeld	licht@N+beeld@NWB	licht@N+beeld@NWB	e
lichtbeelden	licht@N+beeld@N+en@N|NINFLWB	licht@N+beeld@N+en@N|NINFLWB	m
lichtbeneming	licht@N+be@V|.V+nem@V+ing@N|V.WB	licht@N+be@V|.V+neem@V+ing@N|V.WB	e
</pre>
 *
 * @author Gebruiker
 *
 */
public class CelexFile 
{
	public Set<MorphologicalWord> words = new HashSet<MorphologicalWord>();
	String[] fields = {"word", "ana1", "ana2", "inflection"};

	public void readFromFile(String fileName)
	{
		TabSeparatedFile tsf = new TabSeparatedFile (fileName, fields);
		String[] line;
		while (tsf.getLine() != null)
		{
			String ana1 = tsf.getField("ana1");
			if (ana1 != null)
				words.add(parseConcatenativeCelexAnalysis(ana1));
		}
	}

	public MorphologicalWord parseConcatenativeCelexAnalysis(String ana)
	{
		MorphologicalWord w = new MorphologicalWord();
		try
		{
			String text = "";
			String[] parts = ana.split("\\+");
			int position=0;
			for (String part: parts)
			{
				String[] ml = part.split("@");
				if (ml.length > 1)
				{
					String morpheme = ml[0];
					text += morpheme;
					String label = ml[1];
					Morpheme m = new Morpheme(w);
					w.morphemes.add(m);
					m.text = morpheme;
					m.label = label;
					m.firstPosition = position;
					m.lastPosition = position + morpheme.length()-1;
					for (int i = 0; i < morpheme.length(); i++)
					{
						Position p = new Position(w,position + i);
						p.morpheme = m;
						if (i == morpheme.length() -1)
							p.label = label;
						else
							p.label = Position.dummyLabel;
						w.positions.add(p);
					}
					position += morpheme.length();
				}
			}
			w.text = text;
		} catch (Exception e)
		{
			e.printStackTrace();
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("!Error parsing " + ana);
		}
		w.closed = true;
		//w.addMorphemes();
		return w;
	}
}
