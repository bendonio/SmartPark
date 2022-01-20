package it.unipi.smartPark;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.WebLink;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

public final class Utils {
	
	/**
	 * Function that parses a JSON message returning the corresponding Map of the key-value pairs. 
	 * contained in the JSON message. 
	 * 
	 * @param requestText The JSON message passed as a string
	 * @return	The Map<String, Object> of the key-value pairs found in the JSON message
	 */
	public static Map<String, Object> jsonParser(String requestText) {
			
		Map<String, Object> responseJsonObject = new HashMap<String, Object>();
		
		try {
			
            responseJsonObject = (Map<String, Object>) new Gson().fromJson(requestText, responseJsonObject.getClass());
        
		} catch (JsonParseException exception) {

            System.out.println("[ERR  - Utils] : JSON parsing error.");
            exception.printStackTrace();
			return null;
        }
		return responseJsonObject;
	}

	public static Set<WebLink> discoverResources(String sensorIPAddress) {
		
		String baseServerURI = "coap://[" + sensorIPAddress + "]:5683";
		
		CoapClient clientDiscovery = new CoapClient(baseServerURI);
		
		System.out.println("[INFO - Utils] : Discovering resources of [" + sensorIPAddress + "].");
		
		return clientDiscovery.discover();
		
	}

}
