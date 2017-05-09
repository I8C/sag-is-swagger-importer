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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;

import com.softwareag.is.core.iscomm.server.ServerConnection;
import com.softwareag.is.core.iscomm.server.ServerConnectionManager;

import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

public class SwaggerImporter {

	private static final Logger logger = Logger.getLogger(SwaggerImporter.class);
	
	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new DefaultParser();
	    Options options = getOptions();
	    CommandLine cmd = parser.parse( options, args );
        HelpFormatter formatter = new HelpFormatter();
        if(cmd.hasOption("h")){
        	formatter.printHelp( "", options );
        }else if(cmd.hasOption("is") && cmd.hasOption("u") && cmd.hasOption("p") && cmd.hasOption("swf") && cmd.hasOption("pkg")){
        	SwaggerParser swaggerParser = new SwaggerParser();
        	Swagger swaggerModel = swaggerParser.read(cmd.getOptionValue("swf"));
        	String[] conn = cmd.getOptionValue("is").split(":");
        	String host = conn[0];
        	String port="80";
        	if(conn.length>1){
        		port=conn[1];
        	}
        	ServerConnection sc = new ServerConnection(host,port,cmd.getOptionValue("u"),cmd.getOptionValue("p"),false);
        	try{
        		logger.info("Connecting to server " + host + "(" + port + ")");
        		sc.connect();
        		logger.info("Connected to server");
        		
        		RestImplementation ri = new RestImplementation(sc, swaggerModel, cmd.getOptionValue("pkg"),cmd.getOptionValue("acl"));
        		
        		ri.generateImplementation();
        		
        	}catch(Throwable e){
        		logger.error("Error: " + e.getMessage());
        		//logger.error(e.getStackTrace());
        		e.printStackTrace();
        	}finally{
        		logger.info("Disconnecting from server");
        		sc.disconnect();
        		ServerConnectionManager.getInstance().closeServerConnectionManager();
        		logger.info("Disconnected from server");
        		logger.info("BYE");
                System.exit(0);
        	}       	
        }
        
	}
	
	  private static Options getOptions() {
		  Options options = new Options();
		  options.addOption(Option.builder("h").desc("Help").required(false).longOpt("help").build());
		  options.addOption(Option.builder("is").desc("Integration Server hostname or ip and port e.g. localhost:5555").required(false).longOpt("integrationserver").hasArg(true).build());
		  options.addOption(Option.builder("u").desc("Centrasite user").required(false).longOpt("user").hasArg(true).build());
		  options.addOption(Option.builder("p").desc("Centrasite password").required(false).longOpt("pass").hasArg(true).build());
		  options.addOption(Option.builder("swf").desc("Location of Swagger/OpenAPI file").required(false).longOpt("swagger").hasArg(true).build());
		  options.addOption(Option.builder("pkg").desc("Package name").required(false).longOpt("package").hasArg(true).build());
		  options.addOption(Option.builder("acl").desc("Execute acl").required(false).longOpt("acl").hasArg(true).build());
		  return options;
	}
}
