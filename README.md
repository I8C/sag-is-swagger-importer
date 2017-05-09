# Introduction
The i8c swagger importer provides a tool to automatically generate flows on the integration server for implementing a REST API that is defined by a swagger file. The tool will generate the entire folder structure on the IS and will create the method flows as well as the skeleton code for the implementation flows.
# Installation
## Prerequisites
The swagger-importer uses some SoftwareAG proprietary libraries, it is not allowed to redistribute these. The product can be build with the provided maven file, this contains references to the SoftwareAG libraries. The build requires the following software to be pre-installed on the system:
- Maven
- JDK
- Software AG Designer
- Software AG Update Manager 
- Software AG Shared Libraries

Minimal Software AG install should look like this:
![SAG minimal install example](docs/SAG_minimal_install.PNG?raw=true "SAG minimal install example")

Furthermore it's required to run the update manager at least once on the system, this will create a profile and generate the entrust-toolkit-7.2.223.jar. It's not needed to actually install an update, just run it to see if there are any updates is enough.
## Adjusting pom.xml
In the pom.xml file update the lines bellow to match your Software AG installation directory and version: 
```xml
<properties>
	<sag.install.dir>C:/SoftwareAG</sag.install.dir>
	<sag.designer.version>9.12.0.0000-0236</sag.designer.version>
</properties>
```
Also update the systemPath in the lines bellow to point to the correct path for the entrust-toolkit jar on your system.
```xml
<dependency>
    <groupId>com.sag</groupId>
    <artifactId>entrust</artifactId>
    <version>1.0</version>
    <scope>system</scope>
    <systemPath>${sag.install.dir}/UpdateManager/profile/configuration/org.eclipse.osgi/83/0/.cp/lib/entrust-toolkit-7.2.223.jar</systemPath>
 </dependency>
 ```
## Import and run with Software AG Designer
The project can be imported in Software AG Designer as an existing maven project. The build- & classpath should be updated automatically if the pom is valid. Create a run configuration as java application for the class SwaggerImporter (src/be/i8c/sag/wm/is/SwaggerImporter.java). Make sure to provide the mandatory options in the arguments tab ([check the options section for more details](#options)).
# Options
- \-h, (optional) show this option menu on the command line.
- \-is \<server:port>, (required) integration server hostname or ip and port, e.g. localhost:5555.
- \-u \<username>, (required) username for integrartion server.
- \-p \<password>, (required) password for integration server.
- \-swf \<path_to_swagger_file>, (required) location of the the swagger file.
- \-pkg \<package_name>, (required) name of the package where to generate the flows.
- \-acl \<acl_name>, (optional) name of the access control list used to execute the create flows.

Example arguments:
```sh
-is localhost:5555 -u Administrator -p manage -swf swaggerSample.yaml -pkg Default
```
# FAQ
-   Q: I ran the importer with a swagger file but no flows are generated, or the generated flows are incomplete.
   
    A: The swagger file should have the operationId field for each operation.

-   Q: The importer generated flows but they are not visible in the integration server (only in the file system).
   
    A: Restart the integration server and reload the package.

