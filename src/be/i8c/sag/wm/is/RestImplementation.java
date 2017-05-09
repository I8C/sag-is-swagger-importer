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

import com.softwareag.is.core.iscomm.server.DefaultNamespaceAPI;
import com.softwareag.is.core.iscomm.server.ServerConnection;
import com.wm.data.IData;
import com.wm.data.IDataCursor;
import com.wm.data.IDataUtil;
import com.wm.lang.flow.FlowRoot;
import com.wm.lang.flow.FlowService;
import com.wm.lang.ns.NSInterface;
import com.wm.lang.ns.NSName;
import com.wm.lang.ns.NSNode;
import com.wm.lang.ns.NSPackage;
import com.wm.lang.ns.NSRecord;
import com.wm.lang.ns.NSService;
import com.wm.lang.ns.NSServiceType;
import com.wm.lang.ns.NSSignature;
import com.wm.lang.ns.Namespace;

import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;

public class RestImplementation {

	private static final Logger logger = Logger.getLogger(RestImplementation.class);

	private ServerConnection sc;
	private Swagger swaggerModel;
	private String packageName;
	private DefaultNamespaceAPI nsApi;
	
	private NSPackage nspPkg;
	
	private NSInterface rootFolder = null;
	private NSInterface nsiApi = null;
	private NSInterface nsiImpl;
	private NSInterface nsiDoc;
	private HashMap<String,NSInterface> nsiResources = new HashMap<String,NSInterface>();
	private HashMap<String,NSInterface> nsiResourceImpls = new HashMap<String,NSInterface>();
	
	private HashMap<String,RestServiceCreator> rsCs = new HashMap<String,RestServiceCreator>();
	private Namespace ns;
	private String acl;
	
	public RestImplementation(ServerConnection sc, Swagger swaggerModel, String acl) throws RestImplementationValidationException {
		this(sc, swaggerModel, swaggerModel.getInfo().getTitle(), acl);
	}

	public RestImplementation(ServerConnection sc, Swagger swaggerModel, String packageName, String acl) throws RestImplementationValidationException {
		super();
		this.sc = sc;
		this.ns = sc.getNamespace();
		this.swaggerModel = swaggerModel;
		validate(swaggerModel);
		this.packageName = packageName;
		this.nsApi = sc.getDefaultNamespaceAPI();
		this.acl = acl;
	}

	private void validate(Swagger swaggerModel) throws RestImplementationValidationException{
		String basePathRegex = "^\\/((rest)|(ws))\\/.*\\/v[0-9]+$";
		if(swaggerModel.getInfo()==null || swaggerModel.getInfo().getTitle()==null){
			throw new RestImplementationValidationException("Swagger file should have info section with title");
		}else if(swaggerModel.getInfo()==null || swaggerModel.getInfo().getVersion()==null){
			throw new RestImplementationValidationException("Swagger file should have info section with version");
		}else if(!swaggerModel.getBasePath().matches(basePathRegex)){
			throw new RestImplementationValidationException("Swagger file should have basepath thet mathes " + basePathRegex);
		}
	}
	
	public void generateImplementation() throws RestImplementationGeneratorException{
		// CREATE PACKAGE
		nspPkg = createPackage();
		//CREATE FOLDERS
		// ROOT FOLDERS
		String[] folders = swaggerModel.getBasePath().split("/");
		String version = null;
		rootFolder = createFolder(normalizeName(folders[2]), null);
		nsiApi = rootFolder;
		for(int i = 3 ; i < folders.length; i++){
			nsiApi = createFolder(normalizeName(folders[i]), nsiApi);
			version = folders[i];
		}
		nsiImpl = createFolder("impl", rootFolder);
		nsiDoc = createFolder("doc", rootFolder);
		//VERSION FOLDERS
		nsiImpl = createFolder(version, nsiImpl);
		// RESOURCE FOLDERS
		for(String key:swaggerModel.getPaths().keySet()){
			NSInterface parent = nsiApi;
			NSInterface parentImpl = nsiImpl;
			String[] parts = key.split("/");
			for(String part:parts){
				if(!part.trim().equals("")){
					if(part.matches("\\{\\w+\\}")){
						break;
					}else{
						parent = createFolder(normalizeName(part), parent);
						parentImpl = createFolder(normalizeName(part), parentImpl);
					}
				}
			}
			nsiResources.put(key, parent);
			nsiResourceImpls.put(key, parentImpl);
		}
		// CREATE RECORDS
		for(String key:swaggerModel.getDefinitions().keySet()){
			createRecord(key, nsiDoc, swaggerModel.getDefinitions().get(key));	
		}
		// CREATE SERVICES
		for(String key:swaggerModel.getPaths().keySet()){	
			for(HttpMethod http:swaggerModel.getPath(key).getOperationMap().keySet()){
				// API SERVICE
				String name = "_" + http.toString().toLowerCase();
				NSName apiServiceName = NSName.create(nsiResources.get(key).getNSName().getFullName(), name);
				RestServiceCreator rsC;
				if(rsCs.containsKey(apiServiceName.getFullName()))
					rsC = rsCs.get(apiServiceName.getFullName());
				else{
					rsC = new RestServiceCreator(ns, http, nsiDoc);
					rsCs.put(apiServiceName.getFullName(),rsC);
					NSService apiService = createService(apiServiceName, rsC);
					rsC.setNssApi(apiService);
				}
				if(acl!=null)
					setExecuteACL(rsC.getNssApi(),acl);
				String operationId = swaggerModel.getPath(key).getOperationMap().get(http).getOperationId();
				if(operationId==null){
					logger.warn("OperationId should be filled in for operation " + http + " on path " + key);
				}else{
					NSName implserviceName = NSName.create(nsiResourceImpls.get(key).getNSName().getFullName(), operationId);
					NSService implservice = createImplService(implserviceName, rsC, swaggerModel.getPath(key).getOperationMap().get(http));
					rsC.addNssImpl(swaggerModel.getPath(key).getOperationMap().get(http),implservice);
					rsC.addPath(swaggerModel.getPath(key).getOperationMap().get(http), key);
				}
			}
		}
		// CREATE API IMLPEMENTATIONS
		for(RestServiceCreator rsC :rsCs.values()){
			createServiceImplementation(rsC);
		}
	}
	
	private void createServiceImplementation(RestServiceCreator rsC) throws RestImplementationGeneratorException {
		try{
			// EDIT FLOW
			FlowService fs = (FlowService)rsC.getNssApi();
			if( fs !=null){
				FlowRoot fr = fs.getFlowRoot();
				if(!fr.hasNodes() || fr.getNodes().length<=0){
					rsC.setFlowContent(fr);
					logger.debug(fr.getAsData().toString());
					fs.setFlowRoot(fr);
				}
			}
			
			nsApi.putNode(rsC.getNssApi(), true);
		}catch (Exception e) {
			logger.error("Error creating service implementation for service " + rsC.getNssApi().getNSName().getFullName() + ".");
			throw new RestImplementationGeneratorException("Error creating service implementation for service " + rsC.getNssApi().getNSName().getFullName() + ".",e);
		}
		
	}

	private NSService createService(NSName name, RestServiceCreator rsC) throws RestImplementationGeneratorException {
		try{
			NSService service;
			if(!nsApi.nodeExists(name)){
				NSServiceType stype = NSServiceType.create(NSServiceType.SVC_FLOW, NSServiceType.SVCSUB_DEFAULT);
				service = nsApi.createService(nspPkg, name , stype );
			    service.setSignature(rsC.getRestSignature());
			    service.setInputAuditFields(new String[0][]);
			    service.setOutputAuditFields(new String[0][]);
			    service.setPipelineOption(1);
			    service.setPackage(nspPkg);
			    nsApi.makeNode(service);
			}else
				service = (NSService) nsApi.getNode(name);
		    return service;
		} catch (Exception e) {
			logger.error("Error creating service " + name.getFullName() + ".");
			throw new RestImplementationGeneratorException("Error creating service " + name.getFullName() + ".",e);
		}
	}
	
	private void setExecuteACL(NSService service, String acl) throws RestImplementationGeneratorException{
		try {
			IData aclInfo  = nsApi.getAclInfo(service.getNSName().getFullName());
			IDataCursor idc = aclInfo.getCursor();
			IDataUtil.put(idc, "acl", acl);
			nsApi.setACLInfo(service.getNSName().getFullName(), aclInfo);
			logger.debug("Execute ACL " + acl + " set for service " + service.getNSName().getFullName() + ".");
		} catch (Exception e) {
			logger.error("Error setting ACL data for service " + service.getNSName().getFullName() + ".");
			throw new RestImplementationGeneratorException("Error setting ACL data for service " + service.getNSName().getFullName() + ".",e);
		}
	}
	
	private NSService createImplService(NSName name, RestServiceCreator rsC, Operation implOperation) throws RestImplementationGeneratorException {
		try{
			NSService service;
			if(!nsApi.nodeExists(name)){
				NSServiceType stype = NSServiceType.create(NSServiceType.SVC_FLOW, NSServiceType.SVCSUB_DEFAULT);
				service = nsApi.createService(nspPkg, name , stype );
			    service.setSignature(rsC.getRestImplSignature(implOperation));
			    service.setInputAuditFields(new String[0][]);
			    service.setOutputAuditFields(new String[0][]);
			    service.setPipelineOption(1);
			    service.setPackage(nspPkg);
			    nsApi.makeNode(service);
			}else
				service = (NSService) nsApi.getNode(name);
		    return service;
		} catch (Exception e) {
			logger.error("Error creating service " + name.getFullName() + ".");
			throw new RestImplementationGeneratorException("Error creating service " + name.getFullName() + ".",e);
		}
	}
	
	private NSRecord createRecord(String recordname, NSNode parent, Model model) throws RestImplementationGeneratorException{
		try{
		NSName name = NSName.create(parent.getNSName().getFullName(), recordname);
		NSRecord rec;
		if(!nsApi.nodeExists(name)){
			NSRecordCreator nsrC = new NSRecordCreator(ns,model);
			rec = nsrC.getNSRecord(recordname, parent, nspPkg);
			nsApi.makeNode(rec);
			logger.info("Record " + recordname + " created");
		}else
			rec = (NSRecord) nsApi.getNode(name);
		return rec;
		} catch (Exception e) {
			logger.error("Error creating record " + recordname + ".");
			throw new RestImplementationGeneratorException("Error creating record " + recordname + ".",e);
		}
	}

	private NSInterface createFolder(String foldername, NSNode base) throws RestImplementationGeneratorException {
		try {
			NSName folderName;
			if(base != null){
				String parentName = base.getNSName().getFullName();
				folderName = NSName.create(parentName + "." + foldername);
			}else{
				folderName = NSName.create(foldername);
			}
			NSInterface folder;
			if(!nsApi.nodeExists(folderName)){
				folder = sc.createInterface(nspPkg, folderName);
				logger.info("Folder " + folderName + " created in package " + nspPkg.getName());
			}else{
				folder = (NSInterface) nsApi.getNode(folderName);
				if(!nsInerfaceInNodes(nsApi.getNodes(nspPkg),folder)){
					logger.warn("Folder " + foldername + " already exists, but is not in this package.");
					folder = sc.createInterface(nspPkg, folderName);
					logger.info("Folder " + folderName + " created in package " + nspPkg.getName());
				}else{
					if(folder.getPackage()==null){
						folder.setPackage(nspPkg);
					}
					logger.debug("Folder " + folderName + " already exists in package " + nspPkg.getName());
				}
			}
			return folder;
		} catch (Exception e) {
			logger.error("Error creating folder " + foldername + ".");
			throw new RestImplementationGeneratorException("Error creating folder " + foldername + ".",e);
		}
		
	}
	
	private boolean nsInerfaceInNodes(NSNode[] nodes, NSInterface folder){
		boolean found=false;
		for(NSNode nsn:nodes){
			if(nsn instanceof NSInterface){
				NSInterface nsi = (NSInterface)nsn;
				if(nsi.equals(folder)){
					found=true;
					break;
				}else{
					found=nsInerfaceInNodes(nsi.getNodes(),folder);
					if(found)
						break;
				}
			}
		}
		return found;
	}

	private NSPackage createPackage() throws RestImplementationGeneratorException {
		try {
			if(nsApi.getPackage(packageName) == null){
				nsApi.createPackage(packageName);
				nsApi.activatePackage(packageName);
				logger.info("Package " + packageName + " created");
			}
			sc.refresh();
			NSPackage nsPkg = nsApi.getPackage(packageName);
			return nsPkg;
		} catch (Exception e) {
			logger.debug("Error creating package " + packageName + ".");
			throw new RestImplementationGeneratorException("Error creating package " + packageName + ".",e);
		}
		
	}

	private String normalizeName(String title) {
		title = title.replaceAll("[\\s:\\/\\.]", "_");
		return title;
	}
	
}
