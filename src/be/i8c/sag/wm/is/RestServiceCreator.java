/*
* Copyright (c) 2017, i8c N.V. (Integr8 Consulting; http://www.i8c.be)
* All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package be.i8c.sag.wm.is;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import org.apache.log4j.Logger;

import com.softwareag.is.core.iscomm.server.ServerConnection;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataFactory;
import com.wm.data.IDataUtil;
import com.wm.lang.flow.FlowBranch;
import com.wm.lang.flow.FlowElement;
import com.wm.lang.flow.FlowInvoke;
import com.wm.lang.flow.FlowMap;
import com.wm.lang.flow.FlowMapCopy;
import com.wm.lang.flow.FlowMapDelete;
import com.wm.lang.flow.FlowMapSet;
import com.wm.lang.flow.FlowRoot;
import com.wm.lang.flow.FlowSequence;
import com.wm.lang.flow.MalformedExpressionException;
import com.wm.lang.flow.MapCopy;
import com.wm.lang.flow.MapWmPathInfo;
import com.wm.lang.ns.NSField;
import com.wm.lang.ns.NSName;
import com.wm.lang.ns.NSNode;
import com.wm.lang.ns.NSRecord;
import com.wm.lang.ns.NSService;
import com.wm.lang.ns.NSSignature;
import com.wm.lang.ns.Namespace;
import com.wm.lang.ns.WmPathInfo;
import com.wm.util.Values;

import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;

public class RestServiceCreator {

	private static final Logger logger = Logger.getLogger(RestServiceCreator.class);

	private static enum HTTP_MEHODS{GET,POST,PUT,DELETE};
	
	private Namespace ns;
	private HttpMethod http;
	private NSService nssApi;
	private NSNode docFolder;
	private HashMap<Operation,NSService> nssImpls = new  HashMap<Operation,NSService>();
	private HashMap<Operation,String> paths = new  HashMap<Operation,String>();
	
	public RestServiceCreator(Namespace ns, HttpMethod http, NSNode docFolder) {
		super();
		this.ns = ns;
		this.docFolder = docFolder;
		this.http = http;
	}
	
	public NSSignature getRestImplSignature(Operation operation) {
		//input
		NSRecord inRecord = new NSRecord(ns, null, 0);
	    inRecord.setType(2);
	    inRecord.setComment("");
	    NSRecord request = new NSRecord(ns, "request", NSRecord.DIM_SCALAR);
	    NSRecord params = new NSRecord(ns, "params", NSRecord.DIM_SCALAR);
	    NSRecord headers = new NSRecord(ns, "headers", NSRecord.DIM_SCALAR);
	    NSRecord resource = new NSRecord(ns, "resource", NSRecord.DIM_SCALAR);
	    resource.addField(new NSField(ns, "id", NSField.FIELD_STRING, 0));
	    resource.addField(new NSField(ns, "path", NSField.FIELD_STRING, 0));
	    request.addField(resource);
	    request.addField(headers);
		for(Parameter param:operation.getParameters()){
			if(param.getIn().equals("body")){
				BodyParameter bp = (BodyParameter) param;
				Model schema = bp.getSchema();
				NSRecordCreator nsrC = new NSRecordCreator(ns, schema);
				request.addField(nsrC.getNSRecord("body", docFolder));
			}else if(param.getIn().equals("path")){
			}else if(param.getIn().equals("query")){
				params.addField(new NSField(ns, param.getName(), NSField.FIELD_STRING, 0));
			}else{
				logger.warn("Parameter of type: " + param.getIn() + " not supported.");
			}
		}
		request.addField(params);
		inRecord.addField(request);
		//output
		NSRecord outRecord = new NSRecord(ns);
	    outRecord.setType(2);
	    outRecord.setComment("");
	    NSRecord response = new NSRecord(ns, "response", NSRecord.DIM_SCALAR);
	    NSRecord headersOut = new NSRecord(ns, "headers", NSRecord.DIM_ARRAY);
	    headersOut.addField(new NSField(ns, "fieldName", NSField.FIELD_STRING, 0));
	    headersOut.addField(new NSField(ns, "fieldValue", NSField.FIELD_STRING, 0));
	    response.addField(headersOut);
	    response.addField(new NSField(ns, "responseCode", NSField.FIELD_STRING, 0));
	    NSField responseReason = new NSField(ns, "responseReason", NSField.FIELD_STRING, 0);
	    responseReason.setOptional(true);
	    response.addField(responseReason);
	    NSRecord body = new NSRecord(ns, "body", NSRecord.DIM_SCALAR);
	    for(String key:operation.getResponses().keySet()){
	    	Response resp = operation.getResponses().get(key);
			NSRecordCreator nsrC = new NSRecordCreator(ns, null);
			body.addField(nsrC.parseProperty(resp.getSchema(), key, docFolder.getNSName().getFullName()));
		}
	    if(body.getActualFieldCount()>0)
	    	response.addField(body);
	    outRecord.addField(response);
		NSSignature nsSignature = new NSSignature(inRecord, outRecord);
		return nsSignature;
	}
	
	public NSSignature getRestSignature()
	  {
		String type;
		if(HTTP_MEHODS.valueOf(http.toString().toUpperCase())!= null){
			type = "_" + http.toString().toLowerCase();
		}else
			type="_default";
	    NSRecord inRecord = new NSRecord(ns, null, 0);
	    inRecord.setType(2);
	    inRecord.setComment("");
	    
	    NSField resField = new NSField(ns, NSRecord.TYPE, "$resourceID", 1, 0);
	    resField.setOptional(true);
	    resField.setComment("");
	    inRecord.addField(resField);
	    
	    NSField pathField = new NSField(ns, NSRecord.TYPE, "$path", 1, 0);
	    pathField.setOptional(true);
	    pathField.setComment("");
	    inRecord.addField(pathField);
	    if ((type != null) && (type.equals("_default")))
	    {
	      NSField methodField = new NSField(ns, NSRecord.TYPE, "$httpMethod", 1, 0);
	      methodField.setComment("");
	      inRecord.addField(methodField);
	    }
	    NSRecord outRecord = new NSRecord(ns);
	    outRecord.setType(2);
	    outRecord.setComment("");
	    
	    NSSignature nsSignature = new NSSignature(inRecord, outRecord);
	    return nsSignature;
	  }
	
	public void setFlowContent(FlowRoot fr){
		FlowSequence mainSeq = createTryCatch();
		
		FlowInvoke getTransportInfo = new FlowInvoke(new Values());
		getTransportInfo.setService(NSName.create("pub.flow:getTransportInfo"));
		mainSeq.getNodeAt(0).addNode(getTransportInfo);
		
		// IMPLEMENTATION
		FlowBranch fb = new FlowBranch(new Values());
		fb.setIsCondition(true);
		
		for(Operation op:nssImpls.keySet()){
			FlowInvoke apiImpl = new FlowInvoke(new Values());
			apiImpl.setService(nssImpls.get(op).getNSName());
			apiImpl.setName(getLabel(paths.get(op)));
			// MAPPING
			apiImpl = setImplMapping(apiImpl,op);
			fb.addNode(apiImpl);
		}
		
		mainSeq.getNodeAt(0).addNode(fb);
		
		// CATCH
		FlowInvoke getLastError = new FlowInvoke(new Values());
		getLastError.setService(NSName.create("pub.flow:getLastError"));
		mainSeq.getNodeAt(1).addNode(getLastError);
		mainSeq.getNodeAt(1).addNode(getErrorFlowMap());
		FlowInvoke debugLog = new FlowInvoke(new Values());
		debugLog.setService(NSName.create("pub.flow:debugLog"));
		debugLog.setComment("Default logging");
		debugLog = setDebugLogMapping(debugLog);
		mainSeq.getNodeAt(1).addNode(debugLog);
		fr.addNode(mainSeq);
		
		// RESPONSE
		FlowBranch fb_response = new FlowBranch(new Values());
		fb_response.setIsCondition(true);
		
		FlowSequence fs_resp_a = new FlowSequence(new Values());
		fs_resp_a.setComment("create response string");
		fs_resp_a.setName("%response/body% != $null");
		
		FlowBranch fb_responseCode = new FlowBranch(new Values());
		fb_responseCode.setBranchSwitch("/response/responseCode");
		//JSON STRING
		HashSet<String> responses = new HashSet<String>();
		for(Operation op:nssImpls.keySet()){
			for(String key:op.getResponses().keySet()){
				if(op.getResponses().get(key).getSchema()!=null)
					responses.add(key);
			}
		}
		for(String responseCode:responses){
			FlowInvoke documentToJSONString = new FlowInvoke(new Values());
			documentToJSONString.setService(NSName.create("pub.json:documentToJSONString"));
			documentToJSONString.setName(responseCode.equals("default")?"$default":responseCode);
			documentToJSONString = setDocumentToJSONMapping(documentToJSONString, responseCode);
			fb_responseCode.addNode(documentToJSONString);
		}
		fs_resp_a.addNode(fb_responseCode);
		//SETRESPONSE
		FlowInvoke setResponse = new FlowInvoke(new Values());
		setResponse.setService(NSName.create("pub.flow:setResponse"));
		setResponse = setJSONResponseMapping(setResponse,false);
		fs_resp_a.addNode(setResponse);
		fb_response.addNode(fs_resp_a);
		
		FlowSequence fs_resp_b = new FlowSequence(new Values());
		fs_resp_b.setComment("create empty response string");
		fs_resp_b.setName("$default");
		
		FlowInvoke setEmptyResponse = new FlowInvoke(new Values());
		setEmptyResponse.setService(NSName.create("pub.flow:setResponse"));
		setEmptyResponse = setJSONResponseMapping(setEmptyResponse,true);
		fs_resp_b.addNode(setEmptyResponse);
		
		fb_response.addNode(fs_resp_b);
		
		fr.addNode(fb_response);
		
		//RESPONSECODE
		FlowBranch fb_responsecode = new FlowBranch(new Values());
		fb_responsecode.setIsCondition(true);
			//NORMAL
		FlowInvoke setResponseCode = new FlowInvoke(new Values());
		setResponseCode.setService(NSName.create("pub.flow:setResponseCode"));
		setResponseCode = setResponseCodeMapping(setResponseCode,false);
		setResponseCode.setName("%response/responseCode% != $null");
		fb_responsecode.addNode(setResponseCode);
			//EMPTY
		FlowInvoke setEmptyResponseCode = new FlowInvoke(new Values());
		setEmptyResponseCode.setService(NSName.create("pub.flow:setResponseCode"));
		setEmptyResponseCode = setResponseCodeMapping(setEmptyResponseCode,true);
		setEmptyResponseCode.setName("$default");
		fb_responsecode.addNode(setEmptyResponseCode);
		
		fr.addNode(fb_responsecode);
		
		//RESPONSECODE
		FlowBranch fb_headers = new FlowBranch(new Values());
		fb_headers.setIsCondition(true);
			//NORMAL
		FlowInvoke setResponseHeaders = new FlowInvoke(new Values());
		setResponseHeaders.setService(NSName.create("pub.flow:setResponseHeaders"));
		setResponseHeaders = setResponseHeadersMapping(setResponseHeaders);
		setResponseHeaders.setName("%response/headers% != $null");
		fb_headers.addNode(setResponseHeaders);
		
		fr.addNode(fb_headers);
	}
	
	private FlowInvoke setResponseHeadersMapping(FlowInvoke fi) {
		FlowMap fmi = fi.getInputMap();
		if(fmi==null){
			fmi = new FlowMap(null);
			fmi.setMode(FlowMap.MODE_INPUT);
		}
		FlowMapCopy fmc = getFlowMapCopy("/response/headers","/headers",true);
		fmi.addNode(fmc);
		fi.setInputMap(fmi);
		
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		fmo.addNode(getFlowMapDelete("/headers", true));
		fi.setOutputMap(fmo);
		return fi;
	}

	private FlowInvoke setResponseCodeMapping(FlowInvoke fi, boolean empty) {
		FlowMap fmi = fi.getInputMap();
		if(fmi==null){
			fmi = new FlowMap(null);
			fmi.setMode(FlowMap.MODE_INPUT);
		}
		if(empty){
			fmi.addNode(getFlowMapSet("responseCode","500"));
		}else{
			FlowMapCopy fmc = getFlowMapCopy("/response/responseCode","/responseCode",false);
			fmi.addNode(fmc);
			FlowMapCopy fmc2 = getFlowMapCopy("/response/responseReason","/reasonPhrase",false);
			fmi.addNode(fmc2);
		}
		fi.setInputMap(fmi);
		
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		fmo.addNode(getFlowMapDelete("/responseCode", false));
		fmo.addNode(getFlowMapDelete("/reasonPhrase", false));
		fi.setOutputMap(fmo);
		
		return fi;
	}

	private FlowInvoke setJSONResponseMapping(FlowInvoke fi, boolean empty){
		FlowMap fmi = fi.getInputMap();
		if(fmi==null){
			fmi = new FlowMap(null);
			fmi.setMode(FlowMap.MODE_INPUT);
		}
		if(empty){
			fmi.addNode(getFlowMapSet("responseString",""));
		}else{
			FlowMapCopy fmc = getFlowMapCopy("/jsonString","/responseString",false);
			fmi.addNode(fmc);
			fmi.addNode(getFlowMapSet("contentType","application/json"));
		}
		fi.setInputMap(fmi);
		
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		if(!empty){
			fmo.addNode(getFlowMapDelete("/jsonString", false));
		}
		fmo.addNode(getFlowMapDelete("/responseString", false));
		fmo.addNode(getFlowMapDelete("/contentType", false));
		fi.setOutputMap(fmo);
		
		return fi;
	}
	
	private FlowInvoke setDocumentToJSONMapping(FlowInvoke fi, String responseCode){
		FlowMap fmi = fi.getInputMap();
		if(fmi==null){
			fmi = new FlowMap(null);
			fmi.setMode(FlowMap.MODE_INPUT);
		}
		FlowMapCopy fmc = getFlowMapCopy("/response/body/"+responseCode,"/document",true);
		fmi.addNode(fmc);
		fi.setInputMap(fmi);
		
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		fmo.addNode(getFlowMapDelete("/document", true));
		fi.setOutputMap(fmo);
		
		return fi;
	}
	
	private FlowInvoke setDebugLogMapping(FlowInvoke fi){
		HashMap<String,String> params = new HashMap<String,String>();
		params.put("message", "Error in api: %lastError/errorDump%");
		params.put("function", "REST_API");
		params.put("level", "Error");
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		HashMap<String,String> refs = new HashMap<String,String>();
		refs.put("lastError", "pub.event:exceptionInfo");
		fmo.addNode(getFlowMapDelete("lastError", false, refs));
		fi.setOutputMap(fmo);
		return setFlowInputMap(fi, params);
	}
	
	private FlowInvoke setFlowInputMap(FlowInvoke fi, HashMap<String,String> params){
		FlowMap fmi = fi.getInputMap();
		if(fmi==null){
			fmi = new FlowMap(null);
			fmi.setMode(FlowMap.MODE_INPUT);
		}
		for(String key:params.keySet()){
			fmi.addNode(getFlowMapSet(key,params.get(key)));
		}
		logger.debug("Added input mapping to flow " + fi.getNSName());
		fi.setInputMap(fmi);
		
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		for(String key:params.keySet()){
			fmo.addNode(getFlowMapDelete(key, false));
		}
		fi.setOutputMap(fmo);
		
		return fi;
	}
	
	private FlowMap getErrorFlowMap(){
		FlowMap mapErrorResponse = new FlowMap(null);
		mapErrorResponse.setMode(FlowMap.MODE_STANDALONE);
		mapErrorResponse.setComment("Set ResponseCode 500");
		mapErrorResponse.addNode(getFlowMapSet("response/responseCode","500"));
		return mapErrorResponse;
	}
	
	private FlowMapSet getFlowMapSet(String path, String value){
		FlowMapSet fms = new FlowMapSet(new Values());
		fms.setName("Setter");
		fms.setField(getPath(path, false));
		fms.setInput(value);
		return fms;
	}
	
	private String getPath(String path, boolean isRecord, HashMap<String, String> references) {
		path=path.startsWith("/")?path.substring(1):path;
		path=path.endsWith("/")?path.substring(0,path.lastIndexOf("/")):path;
		String[] parts = path.split("/");
		int[] type = new int[parts.length];
		int[] dim = new int[parts.length];
		NSName[] refs = new NSName[parts.length];
		if(references==null)
			references = new HashMap<String, String>();
		for(int i=0;i<parts.length;i++){
			if(references.containsKey(parts[i])){
				type[i]=4;
				refs[i]=NSName.create(references.get(parts[i]));
			}else if(i==parts.length-1 && !isRecord){
				type[i]=1;
				refs[i]=null;
			}else{
				type[i]=2;
				refs[i]=null;
			}	
			dim[i]=0;
		}
		if(references.size()>0)
			return WmPathInfo.getPath(parts, type, dim, refs);
		else	
			return WmPathInfo.getPath(parts, type, dim);
	}
	
	private String getPath(String path, boolean isRecord){
		return getPath(path, isRecord, null);
	}
	
	private FlowInvoke setImplMapping(FlowInvoke fi, Operation op){
		FlowMap fmi = fi.getInputMap();
		if(fmi==null){
			fmi = new FlowMap(new Values());
			fmi.setMode(FlowMap.MODE_INPUT);
		}
		NSRecord source = fmi.getSource(ns);
		if(source==null){
			source = new NSRecord(ns); 
		}
		NSSignature nss = getRestImplSignature(op);
		NSRecord request = ((NSRecord)nss.getInput().getFieldByName("request"));
		
		fmi.addNode(getFlowMapCopy("$resourceID", "request/resource/id",false));
		fmi.addNode(getFlowMapCopy("$path", "request/resource/path",false));
		// HEADERS
		HashMap<String,String> refs = new HashMap<String,String>();
		refs.put("transport", "pub.flow:transportInfo");
		fmi.addNode(getFlowMapCopy("/transport/http/requestHdrs", "request/headers",true,refs));
		// BODY
		NSRecord body;
		if((body =(NSRecord)request.getFieldByName("body")) != null){
			for(NSField nsf:body.getActualFields()){
				boolean isRecord = nsf instanceof NSRecord;
				NSField from = source.addField(nsf.getName(), nsf.getType(), nsf.getDimensions());
				fmi.addNode(getFlowMapCopy("/" + nsf.getName(),"/request/body/" + nsf.getName(),isRecord));
			}
		}
		// QUERY PARAMS
		for(NSField nsf:((NSRecord)request.getFieldByName("params")).getActualFields()){
			NSField from = source.addField(nsf.getName(), nsf.getType(), nsf.getDimensions());
			fmi.addNode(getFlowMapCopy("/" + nsf.getName(), "/request/params/" + nsf.getName(), false));
		}
		
		fi.setInputMap(fmi);
		
		FlowMap fmo = fi.getOutputMap();
		if(fmo==null){
			fmo = new FlowMap(null);
			fmo.setMode(FlowMap.MODE_OUTPUT);
		}
		fmo.addNode(getFlowMapCopy("/response", "/response",true));
		fmo.addNode(getFlowMapDelete("/request", true));
		fmo.addNode(getFlowMapDelete("/transport", true, refs));
		// BODY
		if((body =(NSRecord)request.getFieldByName("body")) != null){
			for(NSField nsf:body.getActualFields()){
				fmo.addNode(getFlowMapDelete("/" + nsf.getName(), nsf instanceof NSRecord));
			}
		}
		// QUERY PARAMS
		for(NSField nsf:((NSRecord)request.getFieldByName("params")).getActualFields()){
			fmo.addNode(getFlowMapDelete("/" + nsf.getName(), nsf instanceof NSRecord));
		}
		fi.setOutputMap(fmo);
		
		return fi;
	}
	
	private FlowMapCopy getFlowMapCopy(String from, String to, boolean isRecord, HashMap<String,String> references){
		FlowMapCopy mc = new FlowMapCopy(null);
		mc.setMapFrom(getPath(from, isRecord,references));
		mc.setMapTo(getPath(to, isRecord,references));
		logger.debug("Adding mapping from: " + from + " to " + to);
		return mc;
	}

	private FlowMapCopy getFlowMapCopy(String from, String to, boolean isRecord){
		FlowMapCopy mc = new FlowMapCopy(null);
		mc.setMapFrom(getPath(from, isRecord));
		mc.setMapTo(getPath(to, isRecord));
		logger.debug("Adding mapping from: " + from + " to " + to);
		return mc;
	}
	
	private FlowMapDelete getFlowMapDelete(String field,boolean isRecord, HashMap<String,String> references){
		FlowMapDelete md = new FlowMapDelete(null);
		md.setField(getPath(field, isRecord, references));
		logger.debug("Dropping field: " + field);
		return md;
	}
	
	private FlowMapDelete getFlowMapDelete(String field,boolean isRecord){
		return getFlowMapDelete(field,isRecord,null);
	}
	
	private String getLabel(String path) {
		String label="$default";
		if(path.matches("(.*)(\\{\\w+\\})(.+)")){
			//use $path variable
			label="%$path% == '" + path.replaceAll("^(.*?)(\\{\\w+\\})(.+)", "$3") + "'";
		}else if(path.matches("(.*)(\\{\\w+\\})")){
			//use $resourceID variable
			label="%$resourceID% != $null && %$path% == $null";
		}
		return label;
	}

	private FlowSequence createTryCatch() {
		FlowSequence mainSeq = new FlowSequence(new Values());
		mainSeq.setExitOn(Arrays.asList(FlowSequence.exitonOptions).indexOf("SUCCESS"));
		mainSeq.setComment("Main");
		
		FlowSequence trySeq = new FlowSequence(new Values());
		trySeq.setExitOn(Arrays.asList(FlowSequence.exitonOptions).indexOf("FAILURE"));
		trySeq.setComment("Try");
		mainSeq.addNode(trySeq);
		
		FlowSequence catchSeq = new FlowSequence(new Values());
		catchSeq.setExitOn(Arrays.asList(FlowSequence.exitonOptions).indexOf("DONE"));
		catchSeq.setComment("Catch");
		mainSeq.addNode(catchSeq);
		
		return mainSeq;
	}

	public void addNssImpl(Operation op,NSService nssImpl) {
		this.nssImpls.put(op,nssImpl);
	}
	
	public void addPath(Operation op,String key) {
		this.paths.put(op,key);
	}

	public void setNssApi(NSService nssApi) {
		this.nssApi = nssApi;
	}

	public NSService getNssApi() {
		return nssApi;
	}
}
