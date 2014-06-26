package es.us.isa.aws.scraper.ec2;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Map.Entry;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import es.us.isa.aws.aux.table.ConstraintTable;

public class AmazonEC2Scraper {

	private double maxCostHour;
	private double maxUpfrontCost;
	
	private String currentGenPage;
	private String prevGenPage;
	private String dedicatedPage;
	private String propertiesDir;

	protected Properties osProp;
	protected Properties zoneProp;
	protected Properties apiNames;
	protected Properties attNames;
	
	private Collection<String> instanceTypes;
	
	private ConstraintTable charsTable;
	private List<List<String>> charConstraints;
	private ConstraintTable pricingTable;
	private List<List<String>>pricingConstraints;

	public AmazonEC2Scraper(String currentGenPage, String prevGenPage,
			String dedicatedPage, String propertiesDir) {
		super();
		this.currentGenPage = currentGenPage;
		this.prevGenPage = prevGenPage;
		this.dedicatedPage = dedicatedPage;
		this.propertiesDir = propertiesDir;
	}

	private void loadPropertyFiles() {
		osProp = new Properties();
		zoneProp = new Properties();
		apiNames = new Properties();
		attNames = new Properties();
		try {
			osProp.load(new FileInputStream(new File(propertiesDir
					+ "/OS.properties")));
			zoneProp.load(new FileInputStream(new File(propertiesDir
					+ "/zones.properties")));
			apiNames.load(new FileInputStream(propertiesDir
					+ "/apiName.properties"));
			attNames.load(new FileInputStream(propertiesDir
					+ "/attNames.properties"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void parseEC2Constraints(String targetPath) {
		maxCostHour = 0;
		maxUpfrontCost = 0;
		try {
			System.setOut(new PrintStream(targetPath));
			parseEC2();
			System.out.println("Max cost hour = "+maxCostHour);
			System.out.println("Max upfront cost = "+maxUpfrontCost);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	protected void parseEC2() {
		loadPropertyFiles();

		Document currentGenDoc = getJSoupDoc(currentGenPage);
		Document prevGenDoc = getJSoupDoc(prevGenPage);
		Document dedicated = getJSoupDoc(dedicatedPage);

		initsCharsTable();
		parseInstanceCharacteristics(currentGenDoc, prevGenDoc);
		
		initPricingTable();
		parsePublic(currentGenDoc, prevGenDoc);
		parseDedicated(dedicated);
		
		postProcessTables();
	}

	private void postProcessTables() {
		String[][] array1 = list2dToArray(charConstraints);
		String[][] array2 = list2dToArray(pricingConstraints);
		charsTable.setCombinations(array1);
		pricingTable.setCombinations(array2);
	}

	private String[][] list2dToArray(List<List<String>> list2d) {
		String[][] result = new String[list2d.size()][];
		int i = 0;
		for (List<String> l2:list2d){
			String[] array = l2.toArray(new String[1]);
			result[i] = array;
			i++;
		}
		return result;
	}

	private void initPricingTable() {
		pricingTable = new ConstraintTable();
		String[] header = new String[]{"Instance","Location","OS",
				"Dedication","Use","EC2.upfrontCost","Instance.costHour"};
		pricingTable.setHeader(header);
		pricingConstraints = new LinkedList<List<String>>();
		
	}

	private void initsCharsTable() {
		charsTable = new ConstraintTable();
		String[] header = new String[]{"Instance","Instance.cores","Instance.ram", 
				"Instance.defaultStorage", "Instance.ecu", "Instance.ssdBacked"};
		charsTable.setHeader(header);
		charConstraints = new LinkedList<List<String>>();
	}

	private void parseDedicated(Document dedicated) {
		System.out.println();
		System.out.println("## Dedicated Instances Pricing ");
		System.out.println();
		// we just parse on demand prices for dedicated instances
		// we consider all the instances
		instanceTypes = getAllTypes();
		parseEC2Tables(dedicated, false, "Dedicated");

	}

	private Document getJSoupDoc(String htmlFile) {
		Document doc = null;
		File input = new File(htmlFile);
		try {
			doc = Jsoup.parse(input, "UTF-8", "");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return doc;
	}

	protected void parseInstanceCharacteristics(Document currentGen,
			Document prevGen) {
		System.out.println();
		System.out.println("## Current Gen Instances");
		System.out.println();
		parseCharacteristics(currentGen);
		System.out.println();
		System.out.println("## Previous Gen Instances");
		System.out.println();
		parseCharacteristics(prevGen);
	}

	private void parsePublic(Document currentGenDoc, Document prevGenDoc) {
		System.out.println();
		System.out.println("## Pricing ");
		System.out.println();
		// we just parse on demand prices for the previous generation
		
		//XXX we avoid to parse m1.small instance for old gen instances,
		// since it's also described in the new gen instances. change it
		// if Amazon fixes it
		apiNames.remove("m1.small");
		//here we just consider the previous generation instances
		instanceTypes = getPrevGenTypes();
		parseEC2Tables(prevGenDoc, true, "Public");
		// since in the new generation html we have all the prices
		// for reserved instances
		
		//XXX we add m1.small again
		apiNames.setProperty("m1.small","M1_small");
		//now, for the first iteration, we just consider the
		// new generation instances
		instanceTypes = getNewGenTypes();
		parseEC2Tables(currentGenDoc, false, "Public");
	}

	protected void parseEC2Tables(Document doc, boolean justOnDemand,
			String dedication) {
		String costAtt = this.attNames.getProperty("cost");
		String upfrontCostAtt = this.attNames.getProperty("upfrontCost");
		String[] aYearArray = { "OneYearLight", "OneYearMedium", "OneYearHeavy" };
		String[] threeYearsArray = { "ThreeYearsLight", "ThreeYearsMedium",
				"ThreeYearsHeavy" };

		boolean reserved = false;
		// String[] reservingArray =
		// {"NOT PayInAdvance","Light","Medium","Heavy"};
		String[] ossArray = { osProp.getProperty("Linux"),
				osProp.getProperty("RHEL"), osProp.getProperty("SLES"),
				osProp.getProperty("Windows"),
				osProp.getProperty("SQLStandard"), osProp.getProperty("SQLWeb") };
		String[] areasArray = { "VA", "ORE", "CA", "IR", "SIN", "Tokyo",
				"Sydney", "SaoPaulo" };

		Elements reservingCategories = doc
				.select("div[class=parbase section tabs]");
		int limit = reservingCategories.size();
		if (justOnDemand) {
			limit = 1;
		}
		
		// initialise the collection of instance types
		Collection<String> types = new ArrayList<String>(instanceTypes);
		
		// for each reserving category {On Demand, Light, Medium, Heavy}
		for (int i = 0; i < limit; i++) {
			//reset instance types
			types = new ArrayList<String>(instanceTypes);
			Element category = reservingCategories.get(i);
			Elements oss = category.select("div[class^=pricing-table]");
			if (i == 1) {
				reserved = true;
			}
			// for each OS
			for (int j = 0; j < oss.size(); j++) {
				//reset instance types
				types = new ArrayList<String>(instanceTypes);
				Element os = oss.get(j);
				Elements areas = os.select("div[class^=content]");
				// for each area
				for (int k = 0; k < areas.size(); k++) {
					//reset instance types
					types = new ArrayList<String>(instanceTypes);
					Element area = areas.get(k);
					Elements instances = area.select("tr[class=sizes]");
					
					// and for each instance
					for (int l = 0; l < instances.size(); l++) {
						Element instance = instances.get(l);
						Elements rows = instance.select("td");
						String instanceName = this.processInstances(rows.get(0)
								.text());
						if (instanceName != null) {
							//we remove the instance type
							types.remove(instanceName);
							// it is not a micro instance.
							// we do not consider micro instances
							
							
							if (!reserved) {
								
								String costHour = this.processPricePerHour(rows
										.select(".rate").text());
								String onDemand = "OnDemand";
								if (costHour != null) {
									
									String s = "(" + instanceName
											+ " AND " + areasArray[k] + " AND "
											+ ossArray[j] + " AND "
											+ dedication + " AND "+onDemand+")"
											+ " IMPLIES (" +upfrontCostAtt+ "==0 AND " 
											+ costAtt + "==" + costHour + ");";
									processConfiguration(s);
									
									List<String> auxList = new ArrayList<String>();
									auxList.add(instanceName);
									auxList.add(areasArray[k]);
									auxList.add(ossArray[j]);
									auxList.add(dedication);
									auxList.add(onDemand);
									auxList.add("0");
									auxList.add(costHour);
									pricingConstraints.add(auxList);
									
									checkMaxCostHour(costHour);
								}
								else{
									String s = "(" + areasArray[k] + " AND "
											+ ossArray[j] + " AND "
											+ dedication + " AND "+onDemand+")"
											+ " IMPLIES NOT " + instanceName + ";";
									processConfiguration(s);
								}
							} else {
								String fixed1 = this.processPrice(rows.get(1)
										.text());
								if (fixed1 != null) {
									String hour1 = this
											.processPricePerHour(rows.get(2)
													.text());

									String s1 = "(" + instanceName + " AND "
											+ areasArray[k] + " AND "
											+ ossArray[j] + " AND "
											+ dedication + " AND "
											+ aYearArray[i - 1] + ") IMPLIES ("
											+ upfrontCostAtt + "==" + fixed1
											+ " AND " + costAtt + "==" + hour1
											+ ");";
									processConfiguration(s1);
									
									List<String> auxList = new ArrayList<String>();
									auxList.add(instanceName);
									auxList.add(areasArray[k]);
									auxList.add(ossArray[j]);
									auxList.add(dedication);
									auxList.add(aYearArray[i - 1]);
									auxList.add(fixed1);
									auxList.add(hour1);
									pricingConstraints.add(auxList);
									
//									checkMaxPrice(hour1,maxCostHour);
									checkMaxUpfrontCost(fixed1);
								} else {
									// we got a N/A
									String s = "(" + instanceName + " AND "
											+ areasArray[k] + " AND "
											+ ossArray[j] + " AND "
											+ dedication + ") IMPLIES NOT "
											+ aYearArray[i - 1] + ";";
									processConfiguration(s);

								}

								String fixed3 = this.processPrice(rows.get(3)
										.text());
								if (fixed3 != null) {
									String hour3 = this
											.processPricePerHour(rows.get(4)
													.text());

									String s2 = "(" + instanceName + " AND "
											+ areasArray[k] + " AND "
											+ ossArray[j] + " AND "
											+ dedication + " AND "
											+ threeYearsArray[i - 1] + ") IMPLIES ("
											+ upfrontCostAtt + "==" + fixed3
											+ " AND " + costAtt + "==" + hour3
											+ ");";
									processConfiguration(s2);
									
									List<String> auxList = new ArrayList<String>();
									auxList.add(instanceName);
									auxList.add(areasArray[k]);
									auxList.add(ossArray[j]);
									auxList.add(dedication);
									auxList.add(threeYearsArray[i - 1]);
									auxList.add(fixed3);
									auxList.add(hour3);
									pricingConstraints.add(auxList);

//									checkMaxPrice(hour3,maxCostHour);
									checkMaxUpfrontCost(fixed3);
								} else {
									// we got a N/A
									String s = "(" + instanceName + " AND "
											+ areasArray[k] + " AND "
											+ ossArray[j] + " AND "
											+ dedication + ") IMPLIES NOT "
											+ threeYearsArray[i - 1] + ";";
									processConfiguration(s);
								}
							}
							
						}
						
					}// end for each instance
					for (String instanceName:types){
						//for each type undefined, we forbid it
						String s = "(" + areasArray[k] + " AND "
								+ ossArray[j] + " AND "
								+ dedication;
//						+ ") IMPLIES NOT "
//								+ threeYearsArray[i - 1] + ";";
						if (!reserved){
							s += " AND OnDemand)";
						}
						else{
							s += " AND ("+aYearArray[i - 1]+" OR "+ threeYearsArray[i - 1] +"))";
						}
						s += " IMPLIES NOT "+instanceName+";";
						processConfiguration(s);
					}
					
				}
			}
			// XXX if we go further than a single iteration,
			// we are parsing reserved instances. 
			// so we need to consider all the instances
			instanceTypes = getAllTypes();
		}
	}
	
	private void checkMaxCostHour(String s){
		try{
			Double aux = Double.parseDouble(s);
			if (aux > maxCostHour){
				maxCostHour = aux;
			}
		}
		catch(NumberFormatException exp){}
	}
	
	private void checkMaxUpfrontCost(String s){
		try{
			Double aux = Double.parseDouble(s);
			if (aux > maxUpfrontCost){
				maxUpfrontCost = aux;
			}
		}
		catch(NumberFormatException exp){}
	}

	/**
	 * This method returns all the instance types we consider
	 * @return Collection<String> all the instance types that we consider
	 */
	private Collection<String> getAllTypes() {
		Collection<String> result = new ArrayList<String>();
		Collection<Object> values = this.apiNames.values();
		for (Object o:values){
			result.add(o.toString());
		}
		return result;
	}
	
	/**
	 * This method returns all the instance types we consider
	 * @return Collection<String> all the instance types that we consider
	 */
	private Collection<String> getNewGenTypes() {
		Collection<String> result = new ArrayList<String>();
		Collection<Object> values = this.apiNames.values();
		for (Object o:values){
			String aux = o.toString();
			if (aux.startsWith("M3") ||
					aux.startsWith("C3") ||
					aux.startsWith("G2") ||
					aux.startsWith("R3") ||
					aux.startsWith("I2") ||
					aux.startsWith("HS1") ||
					aux.equals("M1_small"))
			{
				result.add(aux);
			}
		}
		return result;
	}
	
	/**
	 * This method returns all the instance types we consider
	 * @return Collection<String> all the instance types that we consider
	 */
	private Collection<String> getPrevGenTypes() {
		Collection<String> result = new ArrayList<String>();
		Collection<Object> values = this.apiNames.values();
		for (Object o:values){
			String aux = o.toString();
			if (aux.startsWith("C1") ||
					aux.startsWith("CC2") ||
					aux.startsWith("CG1") ||
					aux.startsWith("M2") ||
					aux.startsWith("CR1") ||
					aux.startsWith("HI1"))
			{
				result.add(aux);
			}
			else if (aux.startsWith("M1") && !aux.equals("M1_small")){
				result.add(aux);
			}
			
		}
		return result;
	}

	private void parseCharacteristics(Document doc) {
		String[] areasArray = { "VA", "ORE", "CA", "IR", "SIN", "Tokyo",
				"Sydney", "SaoPaulo" };
		Map<String, Collection<String>> areaInstances = new HashMap<String, Collection<String>>();
		Map<String, Map<String, String>> instanceCharacteristics = new HashMap<String, Map<String, String>>();

		Elements reservingCategories = doc
				.select("div[class=parbase section tabs]");
		// for each reserving category {On Demand, Light, Medium, Heavy}
		// XXX we just take the first table, i.e. on demand
		for (int i = 0; i < 1; i++) {
			Element category = reservingCategories.get(i);
			Elements oss = category.select("div[class^=pricing-table]");
			// for each OS
			// XXX just considering linux 'cause we already know
			// constraints about OSS
			for (int j = 0; j < 1; j++) {
				Element os = oss.get(j);
				Elements areas = os.select("div[class^=content]");
				// for each area

				for (int k = 0; k < areas.size(); k++) {
					Element area = areas.get(k);
					Elements instances = area.select("tr[class=sizes]");
					// and for each instance
					Collection<String> instancesCol = new ArrayList<String>();
					for (int l = 0; l < instances.size(); l++) {
						Element instance = instances.get(l);
						Elements rows = instance.select("td");
						String instanceName = processInstanceId(rows.get(0)
								.text());
						// XXX with this boolean, we exclude instances with
						// undefined price
						boolean priceIsDefined = rows.get(5).text()
								.contains("$");
						if (k == 0) {
							// if we're processing Virginia, then obtain
							// instance characteristics
							Map<String, String> chars = new HashMap<String, String>();
							chars.put("cores", rows.get(1).text());
							chars.put("ecu", rows.get(2).text());
							String ram = rows.get(3).text();
							chars.put("ram", ram);
							chars.put("disk", rows.get(4).text());

							instanceCharacteristics.put(instanceName, chars);
						}
						if (priceIsDefined) {
							instancesCol.add(instanceName);
						}

					}
					areaInstances.put(areasArray[k], instancesCol);
				}
			}
		}

		// print att Values
		String coresAtt = this.attNames.getProperty("cores");
		String diskAtt = this.attNames.getProperty("disk");
		String ramAtt = this.attNames.getProperty("ram");
		String ecuAtt = this.attNames.getProperty("ecu");
		String ssdAtt = this.attNames.getProperty("ssd");

		Set<Entry<String, Map<String, String>>> entries1 = instanceCharacteristics
				.entrySet();
		for (Entry<String, Map<String, String>> e : entries1) {
			String iName = this.processInstances(e.getKey());
			if (iName != null) {
				Map<String, String> chars = e.getValue();
				String s1 = this.processCPU(chars.get("cores"));
				String s2 = this.processRAM(chars.get("ram"));
				String s3 = this.processDisk(chars.get("disk"));
				String s4 = this.processECU(chars.get("ecu"));
				boolean isSSD = this.isSSDDisk(chars.get("disk"));
				String s5 = "0";
				if (isSSD) {
					s5 = "1";
				}
				System.out.println(iName + " IMPLIES " + "(" + coresAtt + "=="
						+ s1 + " AND " + ramAtt + "==" + s2 + " AND " + diskAtt
						+ "==" + s3 + " AND " + ecuAtt + "==" + s4 + " AND "
						+ ssdAtt + "==" + s5 + ");");
				List<String> aux = new ArrayList<String>();
				aux.add(iName);
				aux.add(s1);
				aux.add(s2);
				aux.add(s3);
				aux.add(s4);
				aux.add(s5);
				charConstraints.add(aux);
			}

		}
	}



	private String processCPU(String s) {
		return s;
	}

	private String processDisk(String s) {
		String[] splitString = s.split(" ");
		if (splitString.length > 2) {
			int i1 = Integer.parseInt(splitString[0]);
			int i2 = Integer.parseInt(splitString[2]);
			int result = i1 * i2;
			return "" + result;
		} else {
			return splitString[0];
		}

	}

	private boolean isSSDDisk(String s) {
		boolean result = false;
		if (s.contains("SSD")) {
			result = true;
		}
		return result;

	}

	private String processRAM(String s) {
		return s;
	}

	private String processInstances(String s) {
		String instanceId = processInstanceId(s);
		return this.apiNames.getProperty(instanceId);
	}

	private String processPrice(String s) {
		// XXX we remove '$' character
		// if (s.contains("N/A")){
		if (!s.contains("$")) {
			return null;
		} else {
			return s.substring(1, s.length());
		}

	}

	private String processPricePerHour(String s) {
		String[] splitString = s.split(" ");
		String result = splitString[0];
		return processPrice(result);
	}

	private String processInstanceId(String id) {
		return excludeExtraCharacters(id);
	}

	private String excludeExtraCharacters(String id) {
		String[] components = id.split(" ");
		return components[0];
	}

	private String processECU(String string) {
		return string;
	}
	
	protected void processConfiguration(String config){
		System.out.println(config);
	}
	
	public ConstraintTable getCharsTable() {
		return charsTable;
	}

	public ConstraintTable getPricingTable() {
		return pricingTable;
	}

	public static void main(String... args) {
		AmazonEC2Scraper scraper = new AmazonEC2Scraper(
				"./ec2-by-date/2014-6-24/current-pricing.html",
				"./ec2-by-date/2014-6-24/prev-gen-pricing.html",
				"./ec2-by-date/2014-6-24/dedicated-pricing.html",
				"./properties");

		scraper.parseEC2Constraints("./NewAmazonEC2Constrains.txt");
	}

	
}