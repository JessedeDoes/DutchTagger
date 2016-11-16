package nl.namescape.tagging;

import org.ivdnt.openconvert.filehandling.SimpleInputOutputProcess;

public interface TaggerWithOptions extends SimpleInputOutputProcess 
{
	public void setTokenizing(boolean b);
}
