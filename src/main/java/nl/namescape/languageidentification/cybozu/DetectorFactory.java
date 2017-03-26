package nl.namescape.languageidentification.cybozu;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import net.arnx.jsonic.JSON;
import net.arnx.jsonic.JSONException;

import com.cybozu.labs.langdetect.util.LangProfile;
import nl.namescape.languageidentification.cybozu.util.*;

/**
 * Language Detector Factory Class
 * 
 * This class manages an initialization and constructions of {@link Detector}. 
 * 
 * Before using language detection library, 
 * load profiles with {@link DetectorFactory#loadProfile(String)} method
 * and set initialization parameters.
 * 
 * When the language detection,
 * construct Detector instance via {@link DetectorFactory#create()}.
 * See also {@link Detector}'s sample code.
 * 
 * <ul>
 * <li>4x faster improvement based on Elmer Garduno's code. Thanks!</li>
 * </ul>
 * 
 * <ul>
 * <li>Added possibility to load profiles from jar (Jesse)
 * </ul>
 * @see Detector
 * @author Nakatani Shuyo
 */
public class DetectorFactory {
    public HashMap<String, double[]> wordLangProbMap;
    public ArrayList<String> langlist;
    public Long seed = null;
    private DetectorFactory() {
        wordLangProbMap = new HashMap<String, double[]>();
        langlist = new ArrayList<String>();
    }
    static private DetectorFactory instance_ = new DetectorFactory();

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *  
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public static void loadProfile(String profileDirectory) throws LangDetectException {
        loadProfile(new File(profileDirectory));
    }

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *  
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public static void loadProfile(File profileDirectory) throws LangDetectException 
    {
        File[] listFiles = profileDirectory.listFiles();
        if (listFiles == null)
            throw new LangDetectException(ErrorCode.NeedLoadProfileError, "Not found profile: " + profileDirectory);
            
        int langsize = listFiles.length, index = 0;
        for (File file: listFiles) {
            if (file.getName().startsWith(".") || !file.isFile()) continue;
            FileInputStream is = null;
            try {
                is = new FileInputStream(file);
                LangProfile profile = JSON.decode(is, LangProfile.class);
                addProfile(profile, index, langsize);
                ++index;
            } catch (JSONException e) {
                throw new LangDetectException(ErrorCode.FormatError, "profile format error in '" + file.getName() + "'");
            } catch (IOException e) {
                throw new LangDetectException(ErrorCode.FileLoadError, "can't open '" + file.getName() + "'");
            } finally {
                try {
                    if (is!=null) is.close();
                } catch (IOException e) {}
            }
        }
    }
    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *  
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    
    public static void loadProfileFromJar(String profileDirectory) throws LangDetectException 
    {
    	
    	 Pattern pattern;
    	 System.err.println("loading from " + profileDirectory);
    	 pattern = Pattern.compile(profileDirectory + ".*[a-z].*");

    	 final Collection<String> listOfProfiles = ResourceList.getResources(pattern);
    	
    	 int langsize = listOfProfiles.size(), index = 0;
    	 for (String profileName: listOfProfiles)
    	 {
    		 InputStream is = DetectorFactory.class.getResourceAsStream("/"+ profileName);
    		  try {
                 
                  LangProfile profile = JSON.decode(is, LangProfile.class);
                  addProfile(profile, index, langsize);
                  ++index;
              } catch (JSONException e) {
                  throw new LangDetectException(ErrorCode.FormatError, "profile format error in '" + profileName + "'");
              } catch (IOException e) {
                  throw new LangDetectException(ErrorCode.FileLoadError, "can't open '" + profileName + "'");
              } finally {
                  try {
                      if (is!=null) is.close();
                  } catch (IOException e) {}
              }
    	 }
    }
    
    public static void loadProfileFromJar() throws LangDetectException 
    {
    	loadProfileFromJar("profiles");
    }
    /**
     * @param profile
     * @param langsize 
     * @param index 
     * @throws LangDetectException 
     */
    static /* package scope */ void addProfile(LangProfile profile, int index, int langsize) throws LangDetectException {
        String lang = profile.name;
        if (instance_.langlist.contains(lang)) {
             // throw new LangDetectException(ErrorCode.DuplicateLangError, 
             //		"duplicate the same language profile for " + lang);
        	return;
        }
        instance_.langlist.add(lang);
        for (String word: profile.freq.keySet()) {
            if (!instance_.wordLangProbMap.containsKey(word)) {
                instance_.wordLangProbMap.put(word, new double[langsize]);
            }
            int length = word.length();
            if (length >= 1 && length <= 3) {
                double prob = profile.freq.get(word).doubleValue() / profile.n_words[length - 1];
                instance_.wordLangProbMap.get(word)[index] = prob;
            }
        }
    }

    /**
     * for only Unit Test
     */
    static /* package scope */ void clear() {
        instance_.langlist.clear();
        instance_.wordLangProbMap.clear();
    }

    /**
     * Construct Detector instance
     * 
     * @return Detector instance
     * @throws LangDetectException 
     */
    static public Detector create() throws LangDetectException {
        return createDetector();
    }

    /**
     * Construct Detector instance with smoothing parameter 
     * 
     * @param alpha smoothing parameter (default value = 0.5)
     * @return Detector instance
     * @throws LangDetectException 
     */
    public static Detector create(double alpha) throws LangDetectException {
        Detector detector = createDetector();
        detector.setAlpha(alpha);
        return detector;
    }

    static private Detector createDetector() throws LangDetectException {
        if (instance_.langlist.size()==0)
            throw new LangDetectException(ErrorCode.NeedLoadProfileError, "need to load profiles");
        Detector detector = new Detector(instance_);
        return detector;
    }
    
    public static void setSeed(long seed) {
        instance_.seed = seed;
    }
    
    public static final List<String> getLangList() {
        return Collections.unmodifiableList(instance_.langlist);
    }
    
    public static void main(String[] args)
    {
    	String s = args[0];
    	boolean readFromFile = false;
    	File f = new File(s);
    	if (f.exists())
    	{
    		readFromFile = true;
    		try
    		{
    			BufferedInputStream b = new BufferedInputStream(new FileInputStream(f));
    			InputStreamReader r = new InputStreamReader(b);
    			BufferedReader br = new BufferedReader(r);
    			String l;
    			s="";
    			while ((l = br.readLine()) != null)
    			{
    				s += l + " ";
    			}
    		} catch (Exception e)
    		{
    			
    		}
    	}
    	try 
    	{
			loadProfileFromJar();
			Detector detector = DetectorFactory.create();
			detector.append(s);
			String lang = detector.detect();
			System.err.println("Language detected: " + lang);
		} catch (LangDetectException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}
