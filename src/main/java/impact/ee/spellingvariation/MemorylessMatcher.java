package impact.ee.spellingvariation;
import impact.ee.trie.Trie;
import impact.ee.trie.Trie.TrieNode;
import impact.ee.util.Options;
import impact.ee.util.StringUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;



class MatchState implements Comparable<MatchState>
{
	TrieNode lexnode;
	int position;
	int cost;
	MatchState parentState;
	RuleInfo rule;

	MatchState(Trie.TrieNode  n, int p)
	{
		lexnode = n; position = p;
		parentState = null; rule = null;
	}

	public int compareTo(MatchState other)
	{
		return cost - other.cost;
	}
}

/**
 * This class tries to find the best matches in a (trie-shaped) word list for a target word, given
 * a set of weighted patterns
 * <p>Typical usage</p>
 * <pre style='font-size:9pt'>
 * MemorylessMatcher matcher = new MemorylessMatcher("some_pattern_filename");
 * Trie lexicon = new Trie();
 * lexicon.loadWordlist("some_wordlist_filename");
 * ....
 * matcher.matchWordToLexicon(lexicon, s);
 * </pre>
 * Because there are possibly many different matches, 
 * you need to implement a callback to get at the matches:
 * <pre style='font-size:9pt'>
 * MemorylessMatcher.Callback myCallback = new Callback()
 * {
 *  public void handleMatch(String targetWord, String matchedWord, String matchInfo, int cost, double p)
 *  {
 *   System.out.printf("%s\t%s\n", targetWord,matchedWord);
 *  }
 * };
 * matcher.callback = myCallback;
 * </pre> 
 */
public class MemorylessMatcher
{
	protected static final double costScale = 100.0;
	static final int BONUS=0;
	int MAX_SUGGESTIONS = 1;
	static int MAX_ITEMS=50000;
	static int MAX_PENALTY = 3000;
	int MAX_PENALTY_INCREMENT=300;

	double MIN_JOINT_PROBABILITY=1e-6;
	double MIN_CONFIDENCE = 1e-3;
	public boolean addWordBoundaries = false;
	boolean allowDeletions = true;
	boolean allowInsertions = true;
	boolean VERBOSE = true;
	String targetWord = null;
	Trie lextrie;
	Trie ruletrie = new Trie();
	Vector<MatchState> activeItems = new Vector<MatchState>();

	java.util.PriorityQueue<MatchState> queue = new java.util.PriorityQueue<MatchState>();

	/**
	 * You need to override this callback class  to retrieve the matching candidates.
	 * @author jesse
	 *
	 */
	public static abstract class Callback
	{
		/**
		 * 
		 * @param targetWord the word you submitted to the matcher
		 * @param matchedWord the match proposed
		 * @param matchInfo printable representation of alignment of targetWord and matcherWord
		 * @param cost a discretization of the <i>p</i> parameter according to <i>cost= 100 * -ln(p)</i>
		 * @param p some probability score, computed from the applied pattern weights
		 */
		public abstract void handleMatch(String targetWord, String matchedWord, String matchInfo, int cost, double p);
	}

	protected Callback callback = new Callback()
	{
		public void handleMatch(String targetWord, String matchedWord, String matchInfo, int cost, double p)
		{
			if (VERBOSE)
			{
				System.out.printf("%s-->%s [%s] (%d=%e)\n", targetWord, matchedWord, matchInfo, cost, p);
			} else
			{
				System.out.printf("%s\t%s\n", targetWord,matchedWord);
			}
		}
	};

	void init()
	{
		try
		{
			String min_joint = Options.getOption("minimumJointProbablity");
			if (min_joint != null)
				this.MIN_JOINT_PROBABILITY = Double.parseDouble(min_joint);
			String min_conf = Options.getOption("minimumConfidence");
			if (min_conf  != null)
				this.MIN_CONFIDENCE = Double.parseDouble(min_conf);
			String allow_d = Options.getOption("allowDeletions");
			if (allow_d != null)
				allowDeletions = Boolean.parseBoolean(allow_d);
			String allow_i = Options.getOption("allowInsertions");
			if (allow_i != null)
				allowInsertions = Boolean.parseBoolean(allow_i);
			String maxSuggestions = Options.getOption("maximumSuggestions");
			if (maxSuggestions != null)
				this.MAX_SUGGESTIONS = Integer.parseInt(maxSuggestions);
			String maxPenalty = Options.getOption("maximumPenalty");
			if (maxPenalty != null)
				MemorylessMatcher.MAX_PENALTY = Integer.parseInt(maxPenalty);
			addWordBoundaries = Options.getOptionBoolean("addWordBoundaries", addWordBoundaries);
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void setMaxSuggestions(int k)
	{
		this.MAX_SUGGESTIONS = k;
	}

	public void setMaxPenaltyIncrement(int x)
	{
		this.MAX_PENALTY_INCREMENT=x;
	}
	
	public MemorylessMatcher(String ruleFileName)
	{
		init();
		try
		{
			FileInputStream fis = new FileInputStream(ruleFileName);
			InputStreamReader isr = new InputStreamReader(fis, "UTF8");

			BufferedReader f = new BufferedReader(isr);
			readRules(f);
		} catch (Exception e)
		{
		}
	}

	public MemorylessMatcher(MultigramSet set) // not used and unsafe
	{
		init();
		getRulesFromMultigramSet(set);
	}

	public MemorylessMatcher()
	{

	}

	public void setCallback(Callback c)
	{
		this.callback = c;
	}

	@Deprecated
	protected boolean readRulesOldVersion(BufferedReader f)
	{
		String tokens[];
		String s;
		try
		{
			while ((s = f.readLine()) != null) // 	this is silly 
			{
				tokens = s.split("\t"); 
				RuleInfo rule = new RuleInfo(tokens[0],  tokens[1], Double.parseDouble(tokens[2]));
				storeRule(rule);
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
		//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf( "XXX %d\n", ruletrie.root.nofTransitions());
		return true;
	}

	/**
	 * Rules are stored in a tab-separated file with format
	 * modern→historical		jointProbability	probabilityGivenLHS probabilityGivenRHS
	 * @param f
	 * @return
	 */
	protected boolean readRules(BufferedReader f)
	{
		String tokens[];
		String s;
		try
		{
			while ((s = f.readLine()) != null) // 	this is silly 
			{
				tokens = s.split("\t");
				try
				{
					String[] lhsrhs = tokens[0].split("→"); // TODO replace this with something safer!
					String left,right;
					if (tokens[0].startsWith("→"))
					{
						left=""; right = lhsrhs[1];
					} else if (tokens[0].endsWith("→"))
					{
						left = lhsrhs[0]; right="";
					} else
					{
						left=lhsrhs[0]; right=lhsrhs[1];
					}
					RuleInfo rule = new RuleInfo(left, right, 
							Double.parseDouble(tokens[1]),
							Double.parseDouble(tokens[2]),
							Double.parseDouble(tokens[3])
					);
					storeRule(rule);
				} catch (Exception e)
				{
					e.printStackTrace();
					org.ivdnt.openconvert.log.ConverterLog.defaultLog.println(tokens[0]);
				}
			}
		} catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
		// org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf( "XXX %d\n", ruletrie.root.nofTransitions());
		return true;
	}

	protected void getRulesFromMultigramSet(MultigramSet set)
	{
		for (JointMultigram m: set)
		{
			RuleInfo rule = new RuleInfo(m.lhs, m.rhs, set.getScore(m.id));
			storeRule(rule);
		}
	}

	/**  Patterns are stored  in a trie of tries.
	 * <ul>
	  <li> The right hand sides are stored the first level trie
	  <li> to every final state in the in right hand sides-trie we link the trie consisting of all left hand sides going with this rhs
	  <li> Rule information (f.i. weight) is stored in the final states of the left-hand-side tries
	 *</ul>
	 */
	private void storeRule(RuleInfo rule)
	{
		if (rule.joint_probability < this.MIN_JOINT_PROBABILITY)
			return;
		if (!allowDeletions && rule.rhs.equals(""))
			return;
		if (rule.p_cond_rhs < this.MIN_CONFIDENCE)
			return;
		if (rule.lhs.endsWith(Alphabet.finalBoundaryString) || rule.rhs.endsWith(Alphabet.finalBoundaryString))
			this.addWordBoundaries = true;
		if (rule.lhs.startsWith(Alphabet.initialBoundaryString) || rule.rhs.startsWith(Alphabet.initialBoundaryString))
			this.addWordBoundaries = true;
		TrieNode rnode = ruletrie.root.putWord(rule.rhs);
		Trie ltrie = (Trie) rnode.data;
		if (ltrie == null) rnode.data = ltrie = new Trie();
		TrieNode lnode = ltrie.root.putWord(rule.lhs);
		lnode.data = rule;
	}

	int incrementCost(RuleInfo r, int cost)
	{
		if (r == null)
		{
			return cost - BONUS;
		}
		return cost + r.cost;
	}

	MatchState findItem(TrieNode lexnode, int pos)
	{
		@SuppressWarnings("unchecked")
		Vector<MatchState>  items = (Vector<MatchState>) lexnode.data;
		if (items == null)
		{
			return null;
		}

		for (int i=0; i < items.size(); i++)
		{
			if (items.get(i).position == pos) return items.get(i);
		}
		return null;
	}

	void additem(TrieNode lexnode, MatchState  item)
	{
		@SuppressWarnings("unchecked")
		Vector<MatchState> items = (Vector<MatchState>) lexnode.data;
		if (items == null)
		{
			items = new  Vector<MatchState>();
			lexnode.data = items;
		}
		items.addElement(item);
		activeItems.addElement(item);
	}

	// rule=null betekent echo karakter[pos] naar output

	void tryNewItem(MatchState item, TrieNode lexnode, int pos, int newCost, RuleInfo rule)
	{
		MatchState nextitem;

		if ((nextitem = findItem(lexnode, pos)) != null) // dit zou toch  zo af en toe moeten voorkomen??
		{	
			if (newCost < nextitem.cost)
			{
				//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("decrease cost of item at pos %d from %d to %d\n", pos, nextitem.cost, newCost);

				queue.remove(nextitem); nextitem.cost = newCost; queue.offer(nextitem);

				nextitem.parentState = item;
				nextitem.rule = rule;
				nextitem.cost = newCost;
			}
		}  else
		{
			if (rule != null)
			{
				//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("new item at pos %d, cost %d (%d), rule %s.%s !!!\n", pos, newCost, item.cost, rule.lhs, rule.rhs);
			} else
			{
				//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("new item at pos %d, cost %d (%d), character '%c' !!!\n", pos, newCost, item.cost, targetWord[pos-1]);
			}
			nextitem = new MatchState(lexnode, pos);
			nextitem.cost = newCost;
			queue.offer(nextitem);

			nextitem.parentState = item;
			nextitem.rule = rule;
			nextitem.cost = newCost;
			additem(lexnode, nextitem);
		}
	}

	// try to match the left hand sides of rules in the lexicon

	void lhsRecursion(MatchState item, TrieNode lhsNode,  TrieNode  lexnode, int pos, int cost)
	{
		if (lhsNode.isFinal) // pas op halve zool lexnode hoeft niet final te zijn
		{
			RuleInfo  rule = (RuleInfo) lhsNode.data;
			int newCost = incrementCost(rule, cost);
			// org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("found rule at %d: '%s'->'%s' (%d->%d)\n", pos, rule.lhs, rule.rhs, rule.cost, newCost);
			if (rule != null)
			{
				//if (newCost <= MAX_PENALTY)
				tryNewItem(item, lexnode, pos + rule.rhs.length(), newCost, rule);
			}
			else
			{
				org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Fatal error:  final node in rule trie without rule information");	
				System.exit(1);
				//tryNewItem(item, lexnode, pos + 1, newCost, rule); // TODO why this?? can this happen?
			}
		}
		int k = lhsNode.nofTransitions();
		for (int i=0; i < k; i++)
		{
			Trie.Transition t = lhsNode.transition(i);
			TrieNode nextNodeInLexicon;
			if ((nextNodeInLexicon = lexnode.delta(t.character)) != null)
			{
				lhsRecursion(item, t.node, nextNodeInLexicon, pos, cost);
			}
		}
	}

	void relaxTransitions(MatchState item)
	{
		TrieNode rhsNode = ruletrie.root;
		int positionInWord = item.position;
		TrieNode nextlexnode;

		if ((positionInWord < targetWord.length())
				&& (nextlexnode = item.lexnode.delta(targetWord.charAt(positionInWord))) != null)
		{
			tryNewItem(item, nextlexnode, positionInWord + 1, item.cost - BONUS, null);
		}

		while (rhsNode != null) // try to find matching right hand sides of rules
		{
			Trie lhstrie = (Trie) rhsNode.data;

			if (lhstrie != null && rhsNode.isFinal) // right hand side of some rule is triggered
			{
				TrieNode lhsNode = lhstrie.root;
				//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("recognized right hand side of some rule at %d-%d\n", item.pos, pos);
				lhsRecursion(item, lhsNode, item.lexnode, item.position, item.cost); // pos die je door moet geven??
			}
			if (positionInWord >= targetWord.length()) break;
			rhsNode = rhsNode.delta(targetWord.charAt(positionInWord++));
		}
	}


	void outputSuggestion(MatchState item)
	{
		String matchedWord = "", matchInfo= "";
		MatchState theItem = item;

		while (theItem != null)
		{
			if (theItem.rule != null)
			{
				matchInfo =  "[" + theItem.rule.lhs +  "->" + theItem.rule.rhs +  "]"  + matchInfo;
				matchedWord = theItem.rule.lhs + matchedWord;
			} else if (theItem.position > 0)
			{
				matchInfo = targetWord.charAt(theItem.position -1) + matchInfo;
				matchedWord = targetWord.charAt(theItem.position -1) + matchedWord;
			}
			theItem = theItem.parentState;
		}
		double p = Math.exp( (item.cost)/-costScale);
		if (callback != null)
		{
			matchedWord = Alphabet.removeBoundaryMarkers(matchedWord);
			callback.handleMatch(targetWord, matchedWord, matchInfo, item.cost, p);
		}
	}

	// Beware: lexicon wordt vervuild met datanodes.
	// om het netjes te doen moet je dus eigenlijk kopie trekken

	private void removeDataPointersInLexicon()
	{
		//fprintf(stdout,"Number of items produced: %d\n",  activeItems.size);
		for (int i=0; i < activeItems.size(); i++)
		{
			MatchState item = activeItems.get(i);
			@SuppressWarnings("unchecked")
			Vector<MatchState> items = (Vector<MatchState>) item.lexnode.data;  
			if (items != null)
			{
				item.lexnode.data = null;
			}
		}
		activeItems.clear();
	}


	/**
		Main function. Synchronized because:
		<ol>
		<li> temporary data pointers pollute the lexicon
		<li> instance state variables are used
		</ol>
	 */

	synchronized public boolean matchWordToLexicon(Trie lexicon, String  target)
	{
		this.lextrie = lexicon;
		this.targetWord = target;
		if (this.addWordBoundaries)
			this.targetWord = Alphabet.initialBoundaryString + this.targetWord + Alphabet.finalBoundaryString;
		//this.queue =  fh_makekeyheap();

		
		MatchState startitem = new MatchState(lextrie.root, 0);
		activeItems.addElement(startitem);
		startitem.cost = 0;
		//startitem.heapEl = fh_insertkey(queue, 0, (Object) startitem);

		queue.offer(startitem);

		boolean stop = false;
		int L = targetWord.length();
		int nofSuggestions = 0;
		int bestCost = 0;

		boolean found = false;

		while (!stop) // queue empty of 'te duur'
		{
			//Item  item = (Item) fh_extractmin(queue);
			MatchState item = queue.poll();
			if (item == null) break;

			//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("######\nextracted item has pos %d of %d, cost %d\n", item.pos, L, item.cost);
			//if (item == startitem) { org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("Hola: weer startitem, DIT KAN NIET\n"); }
			int penaltyIncrement = 0;
			if (found)
				penaltyIncrement = item.cost - bestCost;
			else
				bestCost = item.cost;

			if (item.lexnode.isFinal && item.position == L)
			{
				// output suggestie

				if (item.cost <= MAX_PENALTY && penaltyIncrement <= MAX_PENALTY_INCREMENT)
				{
					found = true; 
					outputSuggestion(item);
					nofSuggestions++;
				}
			}
			// queue.size() was activeItems.size
			if (nofSuggestions >= MAX_SUGGESTIONS || queue.size() > MAX_ITEMS || item.cost > MAX_PENALTY)
			{
				break;
			}
			relaxTransitions(item);
			//fh_delete(queue, item.heapEl);
			// add all transitions for item [moet dan helaas gediscretiseerd]
			// evt kan je op reeds bekende items uitkomen
		}
		removeDataPointersInLexicon();
		//fh_deleteheap(queue);
		queue.clear();
		return found;
		//org.ivdnt.openconvert.log.ConverterLog.defaultLog.printf("took %u msec\n", (endUsecs - startUsecs)/1000);
	}

	public static void usage()
	{
		org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Usage: java spellingvariation.MemorylessMatcher <pattern file> <word list> [<input file>]");
	}

	class Match
	{
		String target;
		String match;
		String reference;
		String patterns;
		boolean correct;
		boolean referenceInLexicon;
		double score;
		
		public Match(String target, String match,String reference, 
				boolean correct, boolean referenceInLexicon, double score, String patterns)
		{
			this.target=target;
			this.match=match;
			this.reference=reference;
			this.correct=correct;
			this.referenceInLexicon = referenceInLexicon;
			this.score=score;
			this.patterns = patterns;
		}
		
		String asXML()
		{
			return String.format("<match value='%s' correct='%s' OOV='%s' score='%f' appliedPatterns='%s'/>", 
					esc(match), new Boolean(correct).toString(), 
					new Boolean(!referenceInLexicon).toString(), score, esc(patterns));
		}
	}
	
	class TestCallback extends Callback
	{
		int itemsTested=0;
		int noMatch = 0;
		int correctMatches = 0;
		String reference="";
		String currentId="";
		boolean referenceInLexicon = false;
		int nItemsinLexicon = 0;
		List<String> targets = new ArrayList<String>();
		Map<String,String> id2target = new HashMap<String,String>();
		Map<String,String> id2reference = new HashMap<String,String>();
		Map<String,List<Match>> matches = new HashMap<String,List<Match>>();
		
		public void addMatch(Match match) // dit is allemaal fout....
		{
			List<Match> soFar = matches.get(currentId);
			if (soFar == null)
			{
				soFar = new ArrayList<Match>();
				matches.put(currentId, soFar);
			}
			soFar.add(match);
		}
		
		public void handleMatch(String targetWord, String matchedWord, String matchInfo, int cost, double p)
		{
			Match match = new Match(targetWord, matchedWord, reference, matchedWord.equals(reference),
					referenceInLexicon, p, matchInfo);
			addMatch(match);
			if (matchedWord.equals(reference))
			{
				correctMatches++;
			} else
			{
				//org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("!!Wrong  match: " +targetWord + "  =~ " +  matchedWord + " (reference = " + reference + ")");
			}
			//System.out.printf("%s -> %s %s %e %d\n" ,targetWord, matchedWord, matchInfo, p, cost);
		}
		
		public void dumpResults()
		{
			System.out.println("<results>");
			for (String h: targets)
			{
				boolean found=false;
				List<Match> matchList = matches.get(h);
				String mXML="";
				if (matchList != null)
				{
					for (Match m: matchList)
					{
						mXML += m.asXML();
						found |= m.correct;
					}
				}
				String z = String.format("<matching target='%s' reference='%s' found='%s'>%s</matching>",  
						esc(this.id2target.get(h)) , 
						esc(id2reference.get(h)), 
						new Boolean(found).toString(),
						mXML );
				System.out.println(z);
			}
			System.out.println("</results>");
		}
		
	
	}

	public static String esc(String s)
	{
		return StringUtils.xmlSingleQuotedEscape(s);
	}
	
	public void  test(Trie lexicon, BufferedReader in)
	{
		this.lextrie = lexicon;
		TestCallback cb = new TestCallback();
		this.callback = cb;

		try
		{
			String s;
			int k=1;
			while ((s=in.readLine()) != null)
			{
				String[] parts = s.split("\\t");
				//String lemma=parts[0];  
				String id = parts[0]; String modern = parts[1]; String historical = parts[2];
				
				cb.reference = modern;
				cb.currentId = String.format("%06d",k);
				cb.targets.add(cb.currentId);
				cb.referenceInLexicon = lexicon.contains(modern, this.addWordBoundaries);
				if (cb.referenceInLexicon) cb.nItemsinLexicon++;
				cb.id2reference.put(cb.currentId, cb.reference);
				cb.id2target.put(cb.currentId, historical);
				
				cb.itemsTested++;
				boolean found = this.matchWordToLexicon(lexicon, historical);
				if (!found)
				{
					cb.noMatch++;
				}
				k++;
			}
			cb.dumpResults();
			double recallOverall = cb.correctMatches / (double) cb.itemsTested;
			double modernLexiconCoverage = 0;
			double recallInVocabulary = 0;
			try
			{ 
				recallInVocabulary = cb.correctMatches / (double) cb.nItemsinLexicon;
				modernLexiconCoverage = cb.nItemsinLexicon / (double) cb.itemsTested;
			} catch (Exception e)
			{
				
			}
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Items tested: " + cb.itemsTested + "; correct matches:  " + cb.correctMatches + "; no match at all: " + cb.noMatch);
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Coverage of modern lexicon on modern equivalents: "  + modernLexiconCoverage);
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Overall recall: " + recallOverall);
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("Recall of words in modern lexicon: " + recallInVocabulary);
		} catch (Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}		
	}

	public static void main(String[] args)
	{
		new Options(args);
		if (args.length < 2)
		{
			usage();
			System.exit(1);
		}
		MemorylessMatcher matcher = new MemorylessMatcher(Options.getOption("patternInput"));
		Trie lexicon = new Trie();
		lexicon.loadWordlist(Options.getOption("lexicon"), 
				false, 
				Options.getOptionBoolean("addWordBoundaries", false));
		boolean found= false;
		java.io.BufferedReader stdin = null;

		

		if ((Options.getOption("testFile")) != null)
		{
			try
			{
				Reader reader = new InputStreamReader(
						new FileInputStream(Options.getOption("testFile")), "UTF-8");
				stdin  = new BufferedReader(reader);

			} catch (Exception e)
			{
				e.printStackTrace();
				stdin = null;
			}
		} else
		{
			try
			{
				stdin = new BufferedReader(new java.io.InputStreamReader(System.in, "UTF-8"));
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
		String cmd = Options.getOption("command");
		if (cmd != null && cmd.equals("test"))
		{
			org.ivdnt.openconvert.log.ConverterLog.defaultLog.println("testing on labeled input");
			matcher.test(lexicon, stdin);
			System.exit(0);
		}
		MemorylessMatcher.Callback c = new MemorylessMatcher.Callback ()
		{
			public void handleMatch(String targetWord, String matchedWord, String matchInfo, int cost, double p)
			{
				System.out.printf("%s -> %s %s %e\n" ,targetWord, matchedWord, matchInfo, p);
			}
			@SuppressWarnings("unused")
			void noMatch(String targetWord)
			{

			}
		};
		matcher.callback = c;
		String s;
		try 
		{ 
			while ( (s = stdin.readLine()) != null)
			{
				found = matcher.matchWordToLexicon(lexicon, s);
				if (!found)
				{
					System.out.println("No match " + s);
				}
			}
		} catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}
