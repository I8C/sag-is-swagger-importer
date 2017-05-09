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

import java.util.Map;

import org.apache.log4j.Logger;

import com.wm.lang.ns.NSField;
import com.wm.lang.ns.NSName;
import com.wm.lang.ns.NSNode;
import com.wm.lang.ns.NSPackage;
import com.wm.lang.ns.NSRecord;
import com.wm.lang.ns.NSRecordRef;
import com.wm.lang.ns.Namespace;

import io.swagger.models.ComposedModel;
import io.swagger.models.Model;
import io.swagger.models.ModelImpl;
import io.swagger.models.RefModel;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

public class NSRecordCreator {
	
	private static final Logger logger = Logger.getLogger(NSRecordCreator.class);
	private Namespace ns;
	private Model model;
	
	public NSRecordCreator(Namespace ns, Model model) {
		super();
		this.ns = ns;
		this.model = model;
	}
	
	public NSRecord getNSRecord(String recordname, String folder){
		NSRecord modelRec = createFromModel(model, recordname, folder);
		return modelRec;
		
	}
	
	public NSRecord getNSRecord(String recordname, NSNode folder){
		NSRecord modelRec = createFromModel(model, recordname, folder.getNSName().getFullName());
		return modelRec;
		
	}
	
	public NSRecord getNSRecord(String recordname, NSNode folder, NSPackage pkg){
		NSRecord modelRec = createFromModel(model, recordname, folder.getNSName().getFullName());
		modelRec.setNSName(NSName.create(folder.getNSName().getFullName(), recordname));
		modelRec.setPackage(pkg);
		return modelRec;
		
	}
	
	private NSRecord createFromModel(Model model, String name, String folder){
		NSRecord modelRec = null;
		if(model instanceof RefModel){
			modelRec = new NSRecordRef(ns, name,NSName.create(folder, ((RefModel)model).getSimpleRef()), NSRecord.DIM_SCALAR);
		}
		else if(model instanceof ComposedModel){
			ComposedModel cm = (ComposedModel)model;
			modelRec = new NSRecord(ns, name, NSRecord.DIM_SCALAR);
			for(Model m:cm.getAllOf()){
				NSRecord sub = createFromModel(m, name, folder);
				for(NSField field:sub.getFields()){
					modelRec.addField(field);
				}
			}
		}else if(model instanceof ModelImpl){
			modelRec = swaggerModelToNSRecord(model, name,folder);
		}else{
			logger.error("Model of type: " + model.getClass() + " not supported.");
		}
		return modelRec;
	}

	private NSRecord swaggerModelToNSRecord(Model model, String name,String folder){
		NSRecord modelRec = parseProperties(model.getProperties(),name,folder);
		return modelRec;
	}
	
	public NSRecord parseProperties(Map<String, Property> map, String name, String folder){
		NSRecord record = new NSRecord(ns, name, NSRecord.DIM_SCALAR);
		for(String key:map.keySet()){
			NSField f=null;
			Property prop = map.get(key);
			f=parseProperty(prop, key, folder);
			record.addField(f);
		}
		return record;
		
	}
	
	public NSField parseProperty(Property prop, String name, String folder){
		return parseProperty(prop, name, folder, NSRecord.DIM_SCALAR);
		
	}

	private NSField parseProperty(Property prop, String name, String folder, int dimArray) {
		NSField f;	
		if(prop==null){
			f=null;
		}else if(prop instanceof ObjectProperty){
			f = parseProperties(((ObjectProperty)prop).getProperties(),name, folder);
		}else if(prop instanceof RefProperty){
			f = new NSRecordRef(ns, name,NSName.create(folder, ((RefProperty)prop).getSimpleRef()), dimArray);
		}else if(prop instanceof ArrayProperty){
			f = parseProperty((((ArrayProperty)prop).getItems()),name,folder, NSRecord.DIM_ARRAY);
		}else{
			f = new NSField(ns, name, NSField.FIELD_STRING, 0);
		}
		return f;
	}

}
