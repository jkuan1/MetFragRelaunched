package de.ipbhalle.metfraglib.peaklistreader;

import de.ipbhalle.metfraglib.list.DefaultPeakList;
import de.ipbhalle.metfraglib.list.SortedTandemMassPeakList;
import de.ipbhalle.metfraglib.parameter.Constants;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.peak.TandemMassPeak;
import de.ipbhalle.metfraglib.settings.Settings;

public class StringTandemMassPeakListReader extends AbstractPeakListReader {

	public StringTandemMassPeakListReader(Settings settings) {
		super(settings);
	}

	public DefaultPeakList read() throws Exception {
		SortedTandemMassPeakList peakList = null;
		String stringname = (String)this.settings.get(VariableNames.PEAK_LIST_STRING_NAME);
		peakList = new SortedTandemMassPeakList((Double)this.settings.get(VariableNames.PRECURSOR_NEUTRAL_MASS_NAME));
		String[] tmp = stringname.split("\\n");
		for(int i = 0; i < tmp.length; i++) 
		{
			tmp[i] = tmp[i].trim();
			if(tmp[i].startsWith("#") || tmp[i].length() == 0) continue;
			String[] tmp2 = tmp[i].split("\\s+");
			peakList.addElement(new TandemMassPeak(Double.parseDouble(tmp2[0].trim()), Double.parseDouble(tmp2[1].trim())));
		}
		for(int i = 0; i < peakList.getNumberElements(); i++)
			peakList.getElement(i).setID(i);
		peakList.calculateRelativeIntensities(Constants.DEFAULT_MAXIMUM_RELATIVE_INTENSITY);
		return peakList;
	}

	public static DefaultPeakList readSingle(String peaklist, double precursorMass) {
		SortedTandemMassPeakList peakList = null;
		peakList = new SortedTandemMassPeakList(precursorMass);
		String[] tmp = peaklist.split("\\n");
		for(int i = 0; i < tmp.length; i++) 
		{
			tmp[i] = tmp[i].trim();
			if(tmp[i].startsWith("#") || tmp[i].length() == 0) continue;
			String[] tmp2 = tmp[i].split("\\s+");
			peakList.addElement(new TandemMassPeak(Double.parseDouble(tmp2[0].trim()), Double.parseDouble(tmp2[1].trim())));
		}
		for(int i = 0; i < peakList.getNumberElements(); i++)
			peakList.getElement(i).setID(i);
		peakList.calculateRelativeIntensities(Constants.DEFAULT_MAXIMUM_RELATIVE_INTENSITY);
		return peakList;
	}
	
}