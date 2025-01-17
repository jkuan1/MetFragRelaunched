package de.ipbhalle.metfraglib.database;

import java.io.IOException;
import java.io.StringReader;
import java.rmi.RemoteException;
import java.util.ArrayList;

import org.apache.axis2.AxisFault;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.openscience.cdk.ChemFile;
import org.openscience.cdk.ChemObject;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.io.MDLV2000Reader;
import org.openscience.cdk.tools.manipulator.ChemFileManipulator;

import com.chemspider.www.MassSpecAPIStub;
import com.chemspider.www.MassSpecAPIStub.EMolType;
import com.chemspider.www.MassSpecAPIStub.ExtendedMolCompoundInfo;
import com.chemspider.www.MassSpecAPIStub.GetExtendedMolCompoundInfoArray;
import com.chemspider.www.MassSpecAPIStub.GetExtendedMolCompoundInfoArrayResponse;
import com.chemspider.www.MassSpecAPIStub.SearchByFormulaAsyncResponse;
import com.chemspider.www.MassSpecAPIStub.SearchByMassAsync;
import com.chemspider.www.MassSpecAPIStub.SearchByFormulaAsync;
import com.chemspider.www.MassSpecAPIStub.SearchByMassAsyncResponse;
import com.chemspider.www.SearchStub;
import com.chemspider.www.SearchStub.AsyncSimpleSearch;
import com.chemspider.www.SearchStub.GetAsyncSearchResult;
import com.chemspider.www.SearchStub.GetAsyncSearchResultPart;
import com.chemspider.www.SearchStub.GetAsyncSearchResultResponse;
import com.chemspider.www.SearchStub.GetAsyncSearchStatusAndCountResponse;

import de.ipbhalle.metfraglib.additionals.MathTools;
import de.ipbhalle.metfraglib.candidate.TopDownPrecursorCandidate;
import de.ipbhalle.metfraglib.interfaces.ICandidate;
import de.ipbhalle.metfraglib.list.CandidateList;
import de.ipbhalle.metfraglib.parameter.VariableNames;
import de.ipbhalle.metfraglib.settings.Settings;

public class OnlineChemSpiderDatabase extends AbstractDatabase {
	
	private String chemSpiderToken;
	
	public OnlineChemSpiderDatabase(Settings settings) {
		super(settings);
		this.chemSpiderToken = (String)settings.get(VariableNames.CHEMSPIDER_TOKEN_NAME);
		Logger.getLogger("org.apache.axiom.util.stax.dialect.StAXDialectDetector").setLevel(Level.ERROR);
	}

	/**
	 * 
	 */
	public ArrayList<String> getCandidateIdentifiers(double monoisotopicMass, double relativeMassDeviation) throws Exception {
		logger.info("Fetching candidates from ChemSpider");
		double mzabs = MathTools.calculateAbsoluteDeviation(monoisotopicMass, relativeMassDeviation);
		MassSpecAPIStub stub = null;
		try {
			stub = this.initMassSpecAPIStub();
		} catch (Exception e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			return null;
		}
		
		SearchByMassAsync sbma = new SearchByMassAsync();
		
		sbma.setMass(monoisotopicMass);
		sbma.setRange(mzabs);
		sbma.setToken(this.chemSpiderToken);
		SearchByMassAsyncResponse sbmar = null;
		try {
			sbmar = stub.searchByMassAsync(sbma);
		} catch (RemoteException e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			throw new Exception();
		}
		int[] csids = null;
		while (csids == null) {
			csids = this.getAsyncSearchStatusIfResultReady(sbmar.getSearchByMassAsyncResult());
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
				throw new Exception();
			}
		}
		ArrayList<String> csids_vec = new ArrayList<String>();
		for(int i = 0; i < csids.length; i++) {
			csids_vec.add(String.valueOf(csids[i]));
		}
		return csids_vec;
	}

	/**
	 * 
	 */
	public ArrayList<String> getCandidateIdentifiers(String formula) throws Exception {
		logger.info("Fetching candidates from ChemSpider");
		String molecularFormula = formula.replaceAll("([0-9]+)", "_{$1}");
		molecularFormula = formula.replaceAll("\\[_\\{([0-9]+)\\}([A-Z][a-z]{0,3})\\]", "^\\{$1\\}$2");
		
		MassSpecAPIStub stub = null;
		try {
			stub = this.initMassSpecAPIStub();
		} catch (Exception e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			throw new Exception();
		}
        SearchByFormulaAsync sbfa = new SearchByFormulaAsync();
        sbfa.setFormula(molecularFormula);
        sbfa.setToken(this.chemSpiderToken);
        SearchByFormulaAsyncResponse sbmar = null;
		try {
			sbmar = stub.searchByFormulaAsync(sbfa);
		} catch (RemoteException e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			throw new Exception();
		}
		int[] csids = null;
		while (csids == null) {
			csids = this.getAsyncSearchStatusIfResultReady(sbmar.getSearchByFormulaAsyncResult());
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
				throw new Exception();
			}
		}
		ArrayList<String> csids_vec = new ArrayList<String>();
		for(int i = 0; i < csids.length; i++) {
			csids_vec.add(String.valueOf(csids[i]));
		}
		return csids_vec;
	}

	/**
	 * 
	 */
	public ArrayList<String> getCandidateIdentifiers(ArrayList<String> identifiers) {
		logger.info("Fetching candidates from ChemSpider");
		ArrayList<String> uniqueCsidArray = new ArrayList<String>();
        for(int i = 0; i < identifiers.size(); i++) {
                if(!uniqueCsidArray.contains(identifiers.get(i)))
                        uniqueCsidArray.add(identifiers.get(i));
        }

        return uniqueCsidArray;
	}

	/**
	 * 
	 */
	public ICandidate getCandidateByIdentifier(String identifier) {
		MassSpecAPIStub stub = null;
		try {
			stub = this.initMassSpecAPIStub();
		} catch (Exception e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			return null;
		}
      
		GetExtendedMolCompoundInfoArray gemcia = new GetExtendedMolCompoundInfoArray();
		com.chemspider.www.MassSpecAPIStub.ArrayOfInt aoi_msas = new com.chemspider.www.MassSpecAPIStub.ArrayOfInt();
		
		int[] csids = {Integer.parseInt(identifier)};

		aoi_msas.set_int(csids);
		
		gemcia.setCSIDs(aoi_msas);
		gemcia.setIncludeExternalReferences(true);
		gemcia.setIncludeReferenceCounts(true);
		
		EMolType eMolType = EMolType.e2D;
		gemcia.setEMolType(eMolType);
		GetExtendedMolCompoundInfoArrayResponse gemciar = null;
		
		gemcia.setToken(this.chemSpiderToken);
		try {
			gemciar = stub.getExtendedMolCompoundInfoArray(gemcia);
		} catch (RemoteException e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			return null;
		}
		ExtendedMolCompoundInfo[] emci = gemciar.getGetExtendedMolCompoundInfoArrayResult().getExtendedMolCompoundInfo();
		if(emci == null || emci.length == 0) return null;

		String smiles = emci[0].getSMILES();
		String compoundName = emci[0].getCommonName();
		
		if(smiles != null) smiles = smiles.replace("\n", "").replace("\r", "");
		else smiles = "";
		if(compoundName != null) compoundName = compoundName.replace("\n", "").replace("\r", "");
		else compoundName = "";
		
		ICandidate precursorCandidate = new TopDownPrecursorCandidate(emci[0].getInChI(), String.valueOf(emci[0].getCSID()));
		precursorCandidate.setProperty(VariableNames.INCHI_KEY_1_NAME, emci[0].getInChIKey().split("-")[0]);
		precursorCandidate.setProperty(VariableNames.INCHI_KEY_2_NAME, emci[0].getInChIKey().split("-")[1]);
		precursorCandidate.setProperty(VariableNames.INCHI_KEY_NAME, emci[0].getInChIKey());
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_XLOGP_NAME, emci[0].getXLogP());
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_ALOGP_NAME, emci[0].getALogP());
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_NUMBER_EXTERNAL_REFERENCES_NAME, emci[0].getExternalReferences() == null ? 0d : (double)emci[0].getExternalReferences().getExtRef().length);
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_DATA_SOURCE_COUNT, (double)emci[0].getDataSourceCount());
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_NUMBER_PUBMED_REFERENCES_NAME, (double)emci[0].getPubMedCount());
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_REFERENCE_COUNT, (double)emci[0].getReferenceCount());
		precursorCandidate.setProperty(VariableNames.CHEMSPIDER_RSC_COUNT, (double)emci[0].getRSCCount());
		precursorCandidate.setProperty(VariableNames.MOLECULAR_FORMULA_NAME, this.processFormula(emci[0].getMF()));
		precursorCandidate.setProperty(VariableNames.SMILES_NAME, smiles);
		precursorCandidate.setProperty(VariableNames.MONOISOTOPIC_MASS_NAME, emci[0].getMonoisotopicMass());
		precursorCandidate.setProperty(VariableNames.COMPOUND_NAME_NAME, compoundName);
		
		return precursorCandidate;
	}

	/**
	 * 
	 */
	public CandidateList getCandidateByIdentifier(ArrayList<String> identifiers) throws Exception {
		ArrayList<String> uniqueCsidArray = new ArrayList<String>();
        for(int i = 0; i < identifiers.size(); i++) {
        	if(!uniqueCsidArray.contains(identifiers.get(i))) uniqueCsidArray.add(identifiers.get(i));
        }
        CandidateList candidateList = new CandidateList();
        if(identifiers.size() == 0) return new CandidateList();
        int[] csids = null;
        String rid = "";
        /*
         * in case only one id is to be fetched
         */
        String removeID = "222";
        boolean lastIDToRemove = false;
        if(uniqueCsidArray.size() == 1) {
        	if(uniqueCsidArray.get(0).equals(removeID))
        		removeID = "111";
        	uniqueCsidArray.add(removeID);
        	lastIDToRemove = true;
        }
        	
    	AsyncSimpleSearch ass = new AsyncSimpleSearch();
	    String query = "";
	    if(uniqueCsidArray.size() != 0) query += uniqueCsidArray.get(0);
	    for(int i = 1; i < uniqueCsidArray.size(); i++)
	            query += "," + uniqueCsidArray.get(i);
	    ass.setQuery(query);
	    ass.setToken(this.chemSpiderToken);
	    SearchStub thisSearchStub = null;
		try {
			thisSearchStub = this.initSearchStub();
		} catch (AxisFault e1) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			throw new Exception();
		}

        rid = "";
		try {
			rid = thisSearchStub.asyncSimpleSearch(ass).getAsyncSimpleSearchResult();
		} catch (RemoteException e2) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			throw new Exception();
		}			
		while (csids == null) {
			csids = this.getAsyncSearchStatusIfResultReady(rid);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
				throw new Exception();
			}
		}
        /*
         * retrieve inchis and additional info
         */
		int start = 0;
		int count = Math.min(250, csids.length);
		SearchStub searchStub = null;
		try {
			searchStub = this.initSearchStub();
		} catch (AxisFault e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			throw new Exception();
		}
		do {
			ArrayList<ExtendedMolCompoundInfo> emci = getExtendedPrecursorCandidates(rid, searchStub, start, count);
			for(int i = 0; i < emci.size(); i++) {
				if(lastIDToRemove && emci.get(i).getCSID() == Integer.parseInt(removeID)) 
					continue;
					
				ICandidate precursorCandidate = new TopDownPrecursorCandidate(emci.get(i).getInChI(), String.valueOf(emci.get(i).getCSID()));
				
				String smiles = emci.get(i).getSMILES();
				String compoundName = emci.get(i).getCommonName();
				
				if(smiles != null) smiles = smiles.replace("\n", "").replace("\r", "");
				else smiles = "";
				if(compoundName != null) compoundName = compoundName.replace("\n", "").replace("\r", "");
				else compoundName = "";
				
				precursorCandidate.setProperty(VariableNames.INCHI_KEY_1_NAME, emci.get(i).getInChIKey().split("-")[0]);
				precursorCandidate.setProperty(VariableNames.INCHI_KEY_2_NAME, emci.get(i).getInChIKey().split("-")[1]);
				precursorCandidate.setProperty(VariableNames.INCHI_KEY_NAME, emci.get(i).getInChIKey());
				precursorCandidate.setProperty("CHEMSPIDER_XLOGP", emci.get(i).getXLogP());
				precursorCandidate.setProperty("CHEMSPIDER_ALOGP", emci.get(i).getALogP());
				precursorCandidate.setProperty(VariableNames.CHEMSPIDER_NUMBER_EXTERNAL_REFERENCES_NAME, emci.get(i).getExternalReferences() == null ? 0d : (double)emci.get(i).getExternalReferences().getExtRef().length);
				precursorCandidate.setProperty(VariableNames.CHEMSPIDER_DATA_SOURCE_COUNT, (double)emci.get(i).getDataSourceCount());
				precursorCandidate.setProperty(VariableNames.CHEMSPIDER_NUMBER_PUBMED_REFERENCES_NAME, (double)emci.get(i).getPubMedCount());
				precursorCandidate.setProperty(VariableNames.CHEMSPIDER_REFERENCE_COUNT, (double)emci.get(i).getReferenceCount());
				precursorCandidate.setProperty(VariableNames.CHEMSPIDER_RSC_COUNT, (double)emci.get(i).getRSCCount());
				precursorCandidate.setProperty(VariableNames.MOLECULAR_FORMULA_NAME, this.processFormula(emci.get(i).getMF()));
				precursorCandidate.setProperty(VariableNames.SMILES_NAME, smiles);
				precursorCandidate.setProperty(VariableNames.MONOISOTOPIC_MASS_NAME, emci.get(i).getMonoisotopicMass());
				precursorCandidate.setProperty(VariableNames.COMPOUND_NAME_NAME, compoundName);
				
				candidateList.addElement(precursorCandidate);
			}
			start += count;
		}
		while(start < csids.length);
		
		return candidateList;
	}
	
	/**
	 * 
	 * @param rid
	 * @param searchStub
	 * @param start
	 * @param count
	 * @return
	 */
	private ArrayList<ExtendedMolCompoundInfo> getExtendedPrecursorCandidates(String rid, SearchStub searchStub, int start, int count) {
		int neededCounts = count;
		ArrayList<ExtendedMolCompoundInfo> extendedMolCompoundInfoArrayList = new ArrayList<ExtendedMolCompoundInfo>();
		do {
			MassSpecAPIStub stub = null;
			try {
				stub = this.initMassSpecAPIStub();
			} catch (Exception e) {
				this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
				return new ArrayList<ExtendedMolCompoundInfo>();
			}
	        GetAsyncSearchResultPart gasrp = new GetAsyncSearchResultPart();
			gasrp.setRid(rid);
			gasrp.setStart(start);
			gasrp.setCount(count);
			gasrp.setToken(this.chemSpiderToken);
			com.chemspider.www.SearchStub.GetAsyncSearchResultPartResponse gasrpr = null;
			try {
				gasrpr = searchStub.getAsyncSearchResultPart(gasrp);
			} catch (Exception e) {
				this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
				return new ArrayList<ExtendedMolCompoundInfo>();
			}
			
			com.chemspider.www.SearchStub.ArrayOfInt aoi = gasrpr.getGetAsyncSearchResultPartResult();
			GetExtendedMolCompoundInfoArray gemcia = new GetExtendedMolCompoundInfoArray();
			com.chemspider.www.MassSpecAPIStub.ArrayOfInt aoi_msas = new com.chemspider.www.MassSpecAPIStub.ArrayOfInt();
			aoi_msas.set_int(aoi.get_int());
			gemcia.setCSIDs(aoi_msas);
			gemcia.setIncludeExternalReferences(true);
			gemcia.setIncludeReferenceCounts(true);
			
			EMolType eMolType = EMolType.e2D;
			gemcia.setEMolType(eMolType);
			GetExtendedMolCompoundInfoArrayResponse gemciar = null;
			gemcia.setToken(this.chemSpiderToken);
			
			try {
				gemciar = stub.getExtendedMolCompoundInfoArray(gemcia);
			} catch (Exception e) {
				count = (int)Math.ceil((double)count / 2.0);
				continue;
			}
			ExtendedMolCompoundInfo[] array = gemciar.getGetExtendedMolCompoundInfoArrayResult().getExtendedMolCompoundInfo();
			for(int i = 0; i < array.length; i++)
				extendedMolCompoundInfoArrayList.add(array[i]);
			start += count;
			neededCounts = neededCounts - count;
			count = neededCounts;
		} while(neededCounts != 0);
		return extendedMolCompoundInfoArrayList;
	}
	
	/**
	 * 
	 * @param sdfString
	 * @return
	 * @throws CDKException
	 */
	protected ArrayList<IAtomContainer> getAtomContainerFromString(String sdfString) {
        MDLV2000Reader reader = new MDLV2000Reader(new StringReader(sdfString));

        java.util.List<IAtomContainer> containersList;
        java.util.ArrayList<IAtomContainer> ret = new ArrayList<IAtomContainer>();

        ChemFile chemFile = null;
		try {
			chemFile = (ChemFile)reader.read((ChemObject)new ChemFile());
		} catch (CDKException e) {
			try {
				reader.close();
			} catch (IOException e1) {
			}
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			return new ArrayList<IAtomContainer>();
		}
        containersList = ChemFileManipulator.getAllAtomContainers(chemFile);
        for (IAtomContainer container: containersList) {
        	ret.add(container);
        }
        try {
			reader.close();
		} catch (IOException e) {
			this.logger.error("Error: Could not perform database query. This could be caused by a temporal database timeout. Try again later.");
			return new ArrayList<IAtomContainer>();
		}
        return ret;
	}

	private String processFormula(String preFormula) {
		preFormula = preFormula.replaceAll("_\\{([0-9]+)\\}", "$1");
		preFormula = preFormula.replaceAll("\\^\\{([0-9]+)\\}([A-Z][a-z]{0,3})", "\\[$1$2\\]");
		return preFormula;
	}
	
	/**
	 * 
	 * @param rid
	 * @param token
	 * @return
	 */
	private int[] getAsyncSearchStatusIfResultReady (String rid) throws Exception {
		try {
			final SearchStub stub = this.initSearchStub();
			com.chemspider.www.SearchStub.GetAsyncSearchStatusAndCount getGetAsyncSearchStatusAndCountInput = new com.chemspider.www.SearchStub.GetAsyncSearchStatusAndCount();
			getGetAsyncSearchStatusAndCountInput.setRid(rid);
			getGetAsyncSearchStatusAndCountInput.setToken(this.chemSpiderToken);
			final GetAsyncSearchStatusAndCountResponse thisGetAsyncSearchStatusAndCountInputResponse = stub.getAsyncSearchStatusAndCount(getGetAsyncSearchStatusAndCountInput);
			
			String thisstatus = thisGetAsyncSearchStatusAndCountInputResponse.getGetAsyncSearchStatusAndCountResult().getStatus().toString();
			
			
			if(thisstatus.equals("ResultReady")) {
				//	System.out.println("AsyncSearchStatus = ResultReady");
		  		GetAsyncSearchResult gasr = new GetAsyncSearchResult();
	        	gasr.setToken(this.chemSpiderToken);
	        	gasr.setRid(rid);
		  		GetAsyncSearchResultResponse gasrr = null;
		  		try {
		  			gasrr = stub.getAsyncSearchResult(gasr);
				} catch (RemoteException e) {
					e.printStackTrace();
				}
	        	com.chemspider.www.SearchStub.ArrayOfInt aoi = gasrr.getGetAsyncSearchResultResult();
				stub._getServiceClient().getOptions().setProperty(HTTPConstants.CHUNKED, false);
	        	stub._getServiceClient().getOptions().setProperty(HTTPConstants.CONNECTION_TIMEOUT, 3 * 60 * 1000);
	        	stub._getServiceClient().getOptions().setProperty(HTTPConstants.SO_TIMEOUT, 3 * 60 * 1000);
	        	if(aoi.get_int() == null) aoi.set_int(new int[0]);
	 	       	return aoi.get_int();
			}  
			else if(thisstatus.equals("Created")) { 
			//	System.out.println("AsyncSearchStatus = Created");
			  	return null;
			}
			else if(thisstatus.equals("Scheduled")) {
				//	System.out.println("AsyncSearchStatus = Scheduled");
				return null;
			}
			else if(thisstatus.equals("Processing")) {
				//	System.out.println("AsyncSearchStatus = Processing");
				return null;
			}
			else if(thisstatus.equals("PartialResultReady")) {
				//	System.out.println("AsyncSearchStatus = PartialResultReady");
				return null;
			}
			else if(thisstatus.equals("Failed")) {
				//	System.out.println("AsyncSearchStatus = Failed");
				logger.error("Problem with AsyncSearch - AsyncSearchStatus = Failed");
				return new int[0];
			}
			else if(thisstatus.equals("Suspended")) {
				//System.out.println("AsyncSearchStatus = Suspended");
				logger.error("Problem with AsyncSearch - AsyncSearchStatus = Suspended");
				return new int[0];
			}
			else if(thisstatus.equals("TooManyRecords")) {
				//	System.out.println("AsyncSearchStatus = TooManyRecords");
				logger.error("Problem with AsyncSearch - AsyncSearchStatus = TooManyRecords");
				return new int[0];
			}
		} catch (Exception e) {
			logger.error("Problem retrieving ChemSpider webservices", e);
		}
		return new int[0];
	}
	
	protected MassSpecAPIStub initMassSpecAPIStub() throws Exception {
		MassSpecAPIStub stub = new MassSpecAPIStub();
		stub._getServiceClient().getOptions().setProperty(HTTPConstants.CHUNKED, false);
		stub._getServiceClient().getOptions().setProperty(HTTPConstants.CONNECTION_TIMEOUT, 3 * 60 * 1000);
		stub._getServiceClient().getOptions().setProperty(HTTPConstants.SO_TIMEOUT, 3 * 60 * 1000);
		stub._getServiceClient().getOptions().setCallTransportCleanup(true);
		//set proxy if available
		if(this.settings.containsKey(VariableNames.CHEMSPIDER_PROXY_SERVER) 
				&& this.settings.containsKey(VariableNames.CHEMSPIDER_PROXY_PORT)
				&& this.settings.get(VariableNames.CHEMSPIDER_PROXY_SERVER) != null 
				&& this.settings.get(VariableNames.CHEMSPIDER_PROXY_PORT) != null) 
		{
			HttpTransportProperties.ProxyProperties pp = new HttpTransportProperties.ProxyProperties();
			try {
				pp.setProxyName((String)this.settings.get(VariableNames.CHEMSPIDER_PROXY_SERVER));
				pp.setProxyPort(Integer.parseInt((String)this.settings.get(VariableNames.CHEMSPIDER_PROXY_PORT)));
			} catch(Exception e) {
				this.logger.error("Error: Could not set proxy settings. Please check input.");
				throw new Exception();
			}
			stub._getServiceClient().getOptions().setProperty(HTTPConstants.PROXY,pp);
		}
		
		return stub;
	}
	
	protected SearchStub initSearchStub() throws Exception {
		SearchStub searchStub = new SearchStub();
		searchStub._getServiceClient().getOptions().setProperty(HTTPConstants.CHUNKED, false);
		searchStub._getServiceClient().getOptions().setProperty(HTTPConstants.CONNECTION_TIMEOUT, 3 * 60 * 1000);
		searchStub._getServiceClient().getOptions().setProperty(HTTPConstants.SO_TIMEOUT, 3 * 60 * 1000);
		
		//set proxy if available
		if(this.settings.containsKey(VariableNames.CHEMSPIDER_PROXY_SERVER) 
				&& this.settings.containsKey(VariableNames.CHEMSPIDER_PROXY_PORT)
				&& this.settings.get(VariableNames.CHEMSPIDER_PROXY_SERVER) != null 
				&& this.settings.get(VariableNames.CHEMSPIDER_PROXY_PORT) != null) 
		{
			HttpTransportProperties.ProxyProperties pp = new HttpTransportProperties.ProxyProperties();
			try {
				pp.setProxyName((String)this.settings.get(VariableNames.CHEMSPIDER_PROXY_SERVER));
				pp.setProxyPort(Integer.parseInt((String)this.settings.get(VariableNames.CHEMSPIDER_PROXY_PORT)));
			} catch(Exception e) {
				this.logger.error("Error: Could not set proxy settings. Please check input.");
				throw new Exception();
			}
			searchStub._getServiceClient().getOptions().setProperty(HTTPConstants.PROXY,pp);
		}
		
		return searchStub;
	}
	
	public void nullify() {
		// TODO Auto-generated method stub
		
	}

}
