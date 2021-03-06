package com.hanyang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import gate.Annotation;
import gate.AnnotationSet;
import gate.Document;
import gate.SimpleAnnotation;
import gate.util.OffsetComparator;
import gate.util.Out;

public class ProcessParameter {
	public JSONObject generateParameter(JSONObject openAPI, String paraStr, String fullText, List<JSONObject> infoJson,
			Annotation anno, Document doc, ProcessMethod processMe) throws JSONException {

		JSONObject sectionJson = matchURL(paraStr, fullText, infoJson, anno.getStartNode().getOffset(), doc,
				"parameter");

		// if the sectionJson is null, showing that it doesn't match
		if (sectionJson.length() != 0) {

			String url = sectionJson.getString("url");
			String action = sectionJson.getString("action");

			if (processMe.isRealUrl(url)) {
				// 1. we add url/action into openAPI now.
				// because here we have known that each table have match one url
				// some urls would not be used.
				// processMe.addUrl(openAPI, url, action);

				try {
					JSONObject urlObject = openAPI.getJSONObject("paths").getJSONObject(url);
					// 1. find the action
					JSONObject actionObject = urlObject.getJSONObject(action);

					// parser the parameters
					JSONArray paraArray = parseParameter(url, actionObject, paraStr, anno, doc);

					actionObject.put("parameters", paraArray);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}

		return openAPI;
	}

	public JSONArray parseParameter(String url, JSONObject actionObject, String paraStr, Annotation anno, Document doc)
			throws JSONException {

		JSONArray paraArray = new JSONArray();

		if (Settings.TEMPLATE.equals("table")) {
			paraArray = parseTable(url, actionObject, paraStr, anno, doc);
		} else if (Settings.TEMPLATE.equals("list")) {
			paraArray = parseList(url, actionObject, paraStr, anno, doc);
		}

		return paraArray;
	}

	private JSONArray parseList(String url, JSONObject actionObject, String paraStr, Annotation anno, Document doc)
			throws JSONException {
		JSONArray paraArray = new JSONArray();
		Long startL = anno.getStartNode().getOffset();
		Long endL = anno.getEndNode().getOffset();

		AnnotationSet listSet = doc.getAnnotations("Original markups").get("dt", startL, endL + 1);

		if (!listSet.isEmpty()) {

			// dl dt dd
			Out.prln("dl dt dd list");

			// get dt list and sort
			List dtList = new ArrayList(listSet);
			Collections.sort(dtList, new OffsetComparator());

			// get dd
			for (int i = 0; i < dtList.size(); i++) {
				Annotation dtElement = (Annotation) dtList.get(i);
				Long endDt = dtElement.getEndNode().getOffset();

				String dtStr = gate.Utils.stringFor(doc, dtElement);
				if (!dtStr.isEmpty()) {
					// find value
					AnnotationSet ddSet = doc.getAnnotations("Original markups").get("dd", endDt, endDt + 100);
					// get dd list and sort
					List ddList = new ArrayList(ddSet);
					Collections.sort(ddList, new OffsetComparator());
					// each time get the first dd (nearest dd)
					Annotation ddElement = (Annotation) ddList.get(0);
					String ddStr = gate.Utils.stringFor(doc, ddElement);
					// construct json
					JSONObject keyObject = new JSONObject();

					String key = dtStr.split(" ")[0];
					String value = ddStr;

					//validate the key
					if (!isValidParaKey(key)) {
						continue;
					}
					
					keyObject.put("name", key);
					keyObject.put("description", value);
					keyObject.put("in", findParaType(url, actionObject, key));
					keyObject.put("type", findType(value));
					keyObject.put("required", true);
					paraArray.put(keyObject);
				}

			}
		} else {

			// we use the annotation directly

			Long startUl = anno.getStartNode().getOffset();
			Long endUl = anno.getEndNode().getOffset();

			String ulStr = gate.Utils.stringFor(doc, anno);
			if (!ulStr.isEmpty()) {
				// find value
				AnnotationSet aSet = doc.getAnnotations("Original markups").getContained(startUl, endUl + 1).get("li");
				// get dd list and sort
				List aList = new ArrayList(aSet);
				if (!aList.isEmpty()) {

					Collections.sort(aList, new OffsetComparator());
					// each time get the first dd (nearest dd)
					for (int i = 0; i < aList.size(); i++) {

						Annotation liElement = (Annotation) aList.get(i);
						String liStr = gate.Utils.stringFor(doc, liElement);
						// construct json
						String[] pArray = liStr.split("\n");
						if (pArray.length == 1) {
							pArray = liStr.split(" ");
						}
						
						JSONObject keyObject = new JSONObject();
						
						String key, value;
						if (pArray.length == 1) {
							key = pArray[0];
							value = null;
						} else {
							key = pArray[0];
							value = pArray[1];
						}
							
						//validate the key
						if (!isValidParaKey(key)) {
							continue;
						}

						keyObject.put("name", key);
						keyObject.put("description", value);
						keyObject.put("in", findParaType(url, actionObject, key));
						keyObject.put("type", findType(value));
						keyObject.put("required", true);
						paraArray.put(keyObject);
					}

				}

			}

		}

		return paraArray;
	}

	private boolean isValidParaKey(String key) {
		if (key.toLowerCase().equals("path") |key.toLowerCase().equals("require")| key.toLowerCase().equals("optional") | key.toLowerCase().equals("required")) {
			return false;
		}
		return true;
	}

	private String findType(String value) {
		if (value.toLowerCase().contains("string")) {
			return "string";
		} else if (value.toLowerCase().contains("boolean")) {
			return "boolean";
		}
		return "integer";
	}

	public String extactType(String type) {
		if (StringUtils.isNumeric(type)) {
			return "integer";
		} else if ("true".equalsIgnoreCase(type) || "false".equalsIgnoreCase(type)) {
			return "boolean";
		}
		return "integer";
	}
	
	private String findParaType(String url, JSONObject actionObject, String key) throws JSONException {
		// find parameter type
		// Possible values are "query", "header", "path", "formData" or "body".
		if (actionObject.has("request")) {
			String request = actionObject.getString("request");
			if (request.startsWith("{") | request.startsWith("[")) {
				return "body";
			}
		}

		if (url.contains(key)) {
			return "path";
		}
		return Settings.PARAIN;
	}

	private JSONArray parseTable(String url, JSONObject actionObject, String paraStr, Annotation anno, Document doc)
			throws JSONException {
		// Here we need to get tbody, not the whole table annotation
		List trList = getTbody(paraStr, anno, doc);

		JSONArray paraArray = new JSONArray();
		// Iterate the row element
		// Iterator<Annotation> trIter = trSet.iterator();
		boolean titleRow = true; // first time check the first row
		// Set defaut property key name
		int NAME_INDEX = -1;
		int DESCRIPTION_INDEX = -1;
		int TYPE_INDEX = -1;
		int REQUIRED_INDEX = -1;

		for (int i = 0; i < trList.size(); i++) {
			Annotation trElement = (Annotation) trList.get(i);
			String trStr = gate.Utils.stringFor(doc, trElement);
			Long startTd = trElement.getStartNode().getOffset();
			Long endTd = trElement.getEndNode().getOffset();
			// add offset 1 to avoid extract less td annotation
			AnnotationSet tdSet = doc.getAnnotations("Original markups").get("td", startTd, endTd + 1);
			List tdList = new ArrayList(tdSet);
			Collections.sort(tdList, new OffsetComparator());

			// If it's first time, check the rowname
			if (titleRow) {
				// close checking the title row.
				titleRow = false;
				// check the first row
				if (StringUtils.containsIgnoreCase(trStr, "name")
						|| StringUtils.containsIgnoreCase(trStr, "description")) {
					// check the property name
					for (int j = 0; j < tdList.size(); j++) {
						Annotation tdElement = (Annotation) tdList.get(j);
						String tdStr = gate.Utils.stringFor(doc, tdElement);
						if (!tdStr.isEmpty()) {
							if (StringUtils.containsIgnoreCase(tdStr, "name")) {
								NAME_INDEX = j;
							} else if (StringUtils.containsIgnoreCase(tdStr, "description")) {
								DESCRIPTION_INDEX = j;
							} else if (StringUtils.containsIgnoreCase(tdStr, "example")) {
								TYPE_INDEX = j;
							} else if (StringUtils.containsIgnoreCase(tdStr, "require")) {
								REQUIRED_INDEX = j;
							}
						}
					}
					// match the title row, continue
					continue;
				}

			}

			// set defaut value
			JSONObject keyObject = new JSONObject();
			try {
				String key = trStr.split(" ")[0];
				String value = trStr.substring(trStr.indexOf(trStr.split(" ")[1]));
				
				if (key.contains("\n")) {
					key = key.split("\n")[0];
				}
				//validate the key
				if (!isValidParaKey(key)) {
					continue;
				}
				
				keyObject.put("name", key);
				keyObject.put("description", value);
				keyObject.put("in", findParaType(url, actionObject, key));
				keyObject.put("type", findType(value));
				keyObject.put("required", true);
			} catch (Exception e) {
				e.printStackTrace();
			}

			// if we have already identified the key-value
			// change the value to the new one
			if (NAME_INDEX != -1) {
				keyObject.put("name", gate.Utils.stringFor(doc, (SimpleAnnotation) tdList.get(NAME_INDEX)));
			}
			if (DESCRIPTION_INDEX != -1) {
				keyObject.put("description",
						gate.Utils.stringFor(doc, (SimpleAnnotation) tdList.get(DESCRIPTION_INDEX)));
			}
			if (TYPE_INDEX != -1) {
				String type = gate.Utils.stringFor(doc, (SimpleAnnotation) tdList.get(TYPE_INDEX));
				keyObject.put("type", extactType(type));
			}
			if (REQUIRED_INDEX != -1) {
				keyObject.put("required", gate.Utils.stringFor(doc, (SimpleAnnotation) tdList.get(REQUIRED_INDEX)));
			}

			paraArray.put(keyObject);

		}

		return paraArray;
	}

	public List getTbody(String paraStr, Annotation anno, Document doc) {
		// We need to get the precise tbody tag, in the parameter table
		Long startTbody = anno.getStartNode().getOffset();
		Long endTbody = anno.getEndNode().getOffset();
		AnnotationSet tbodySet = doc.getAnnotations("Original markups").get("tbody", startTbody, endTbody);
		// if contain only one tbody
		if (tbodySet.size() == 1) {
			Annotation tbodyAnno = tbodySet.iterator().next();
			Long startTr = tbodyAnno.getStartNode().getOffset();
			Long endTr = tbodyAnno.getEndNode().getOffset();
			AnnotationSet trSet = doc.getAnnotations("Original markups").get("tr", startTr, endTr);

			List trList = new ArrayList(trSet);

			// sort the list by offset
			Collections.sort(trList, new OffsetComparator());

			return trList;
		}

		return null;

	}

	public Map<String, Integer> genUrlLocation(String fullText, List<String> urlList) {
		Map<String, Integer> urlLocation = new HashMap<String, Integer>();
		for (String element : urlList) {
			int location = fullText.indexOf(element);
			urlLocation.put(element, location);
		}
		return urlLocation;

	}

	public void handleParaTemplate(JSONObject openAPI, Document doc, ProcessMethod processMe, String strAll,
			List<JSONObject> infoJson, AnnotationSet annoOrigin) throws JSONException {
		if (Settings.TEMPLATE.equals("table")) {
			// 1 get table annotation
			AnnotationSet annoTable = annoOrigin.get("table");
			searchParameter(openAPI, doc, processMe, strAll, infoJson, annoTable);
		} else if (Settings.TEMPLATE.equals("list")) {
			AnnotationSet annoList = annoOrigin.get("dl");
			if (annoList.isEmpty()) {
				// this is unordered list
				annoList = annoOrigin.get("ul");
			}
			// 2 get list annotation
			searchParameter(openAPI, doc, processMe, strAll, infoJson, annoList);
		}
	}

	public void searchParameter(JSONObject openAPI, Document doc, ProcessMethod processMe, String strAll,
			List<JSONObject> infoJson, AnnotationSet annoTemplate) throws JSONException {
		// 5.3 for each page, set findParaTable = False
		boolean findParaTemplate = false;
		String templateNumber = Settings.NUMBER;
		// int numTemplate = 0;

		// 5.3.2 handle the template context
		Iterator<Annotation> templateIter = annoTemplate.iterator();
		
		// add parameters to those urls
		while (templateIter.hasNext()) {
			Annotation anno = (Annotation) templateIter.next();
			String templateText = gate.Utils.stringFor(doc, anno);
			if (isParaTemplate(templateText, anno, strAll)) {
				findParaTemplate = true;
				Out.prln("==========TABLE or LIST=================");
				Out.prln(templateText);
				generateParameter(openAPI, templateText, strAll, infoJson, anno, doc, processMe);
			}
		}

	}

	public JSONObject matchURL(String paraStr, String fullText, List<JSONObject> infoJson, Long paraLocation,
			Document doc, String source) throws JSONException {
		JSONObject sectionObject = new JSONObject();
		int minimumDistance = Integer.MAX_VALUE;
		Out.prln("--------- location-------");
		Out.prln(paraLocation);
		for (JSONObject it : infoJson) {

			JSONObject entry = it.getJSONObject("url");
			Iterator keys = entry.keys();
			if (keys.hasNext()) {
				String key = (String) keys.next();

				if (source.equals("request")) {
					if (Settings.URL1REQ2) {
						// url location < para location
						minimumDistance = locationNormal(paraLocation, sectionObject, minimumDistance, it, entry, key);
					} else {
						// url location > para location
						minimumDistance = locationReverse(paraLocation, sectionObject, minimumDistance, it, entry, key);
					}
				} else if (source.equals("response")) {
					if (Settings.URL1RES2) {
						minimumDistance = locationNormal(paraLocation, sectionObject, minimumDistance, it, entry, key);
					} else {
						minimumDistance = locationReverse(paraLocation, sectionObject, minimumDistance, it, entry, key);
					}
				} else if (source.equals("parameter")) {
					if (Settings.URL1PARA2) {
						minimumDistance = locationNormal(paraLocation, sectionObject, minimumDistance, it, entry, key);
					} else {
						minimumDistance = locationReverse(paraLocation, sectionObject, minimumDistance, it, entry, key);
					}
				}
	
			}

		}
		return sectionObject;
	}

	private int locationReverse(Long paraLocation, JSONObject sectionObject, int minimumDistance, JSONObject it,
			JSONObject entry, String key) throws JSONException {
		if ((entry.getInt(key) - paraLocation < minimumDistance) && (entry.getInt(key) - paraLocation > 0)) {
			minimumDistance = (int) (entry.getInt(key) - paraLocation);
			sectionObject.put("action", it.getJSONObject("action").keys().next());
			sectionObject.put("url", key);
		}
		return minimumDistance;
	}

	private int locationNormal(Long paraLocation, JSONObject sectionObject, int minimumDistance, JSONObject it,
			JSONObject entry, String key) throws JSONException {
		if ((paraLocation - entry.getInt(key) < minimumDistance) && (paraLocation - entry.getInt(key) > 0)) {
			minimumDistance = (int) (paraLocation - entry.getInt(key));
			sectionObject.put("action", it.getJSONObject("action").keys().next());
			sectionObject.put("url", key);
		}
		return minimumDistance;
	}

	public boolean isParaTemplate(String txt, Annotation anno, String strAll) {
		// not only check "parameter" in the table text
		// but also need to check text just before the table
		// maybe in the table maybe doesn't contain str "parameter"

		// find previous text
		int templateLocation = anno.getStartNode().getOffset().intValue();
		String appendTemplateText;

		// change new method
		
		try {
			//define the max distance = 13
			int distance ;
			if (!Settings.PARAMIDDLE.equals("0"))
				distance = 13;
			else
				distance = Integer.parseInt(Settings.PARAMIDDLE);
			
			int start = (templateLocation - Settings.PARAKEY.length() - distance) > 0 ? (templateLocation - Settings.PARAKEY.length() - distance): 0;
			int end = (templateLocation + Settings.PARAKEY.length() + distance)< strAll.length() ?(templateLocation + Settings.PARAKEY.length() + distance): strAll.length();
			appendTemplateText = strAll.substring(start,end);
			if (Pattern.compile(Settings.PARAKEY, Pattern.CASE_INSENSITIVE).matcher(appendTemplateText).find()) {
				return true;
			}
		} catch (Exception e) {
			
			e.printStackTrace();
		}

		
		return false;
	}

}
