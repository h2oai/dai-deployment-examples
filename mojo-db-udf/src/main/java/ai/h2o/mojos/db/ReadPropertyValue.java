/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package daimojorunner_db;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Properties;

/**
 *
 * @author ericgudgion
 */
class ReadPropertyValue {
    	String result = "";
	InputStream inputStream;
 
public String getPropValue(String name, String defaultSetting) throws IOException {
    
    Boolean verbose = Boolean.parseBoolean(System.getProperty("verbose", "false"));

        try {
                Properties prop = new Properties();
                String propFileName = System.getProperty("propertiesfilename", "DAIMojoRunner_DB.properties");
                if (verbose) {
                    System.out.println("Using properties file: "+propFileName);
                }

                try {
                    inputStream = new FileInputStream(propFileName);
                } catch (Exception e){
                    inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
                }
                
                if (inputStream != null) {
                        prop.load(inputStream);
                } else {
                        throw new FileNotFoundException("property file '" + propFileName + "' not found in the classpath");
                }
                
                try {
                    result = prop.getProperty(name);
                    int a =result.length();
                } catch (Exception ex) { 
                    if (verbose) {
                        System.out.println("Propertry "+name+" not found in properties file, setting to default");
                    }
                    result = prop.getProperty(name,defaultSetting);
                }
                if (verbose) {
                    System.out.println("Property "+name+" = "+result );
                }
        } catch (Exception e) {
                System.out.println("Exception: " + e);
        } finally {
                inputStream.close();
        }
        return result;
}

    
}
