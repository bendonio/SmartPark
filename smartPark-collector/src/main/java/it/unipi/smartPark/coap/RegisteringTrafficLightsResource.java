package it.unipi.smartPark.coap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.print.attribute.standard.Media;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.server.resources.CoapExchange;

import it.unipi.smartPark.SmartParkManager;
import it.unipi.smartPark.Utils;

public class RegisteringTrafficLightsResource extends CoapResource {
	
	private final SmartParkManager smartParkManager;
	
	public RegisteringTrafficLightsResource(String resourcePath, CoAPCollector coapCollector) {
		
		super(resourcePath);
		
		this.smartParkManager = coapCollector.getSmartParkManager();
		
	}
	
	/**
	 * This function will handle the registration requests coming from Traffic Light actuators.
	 * 
	 */
	public void handlePOST(CoapExchange exchange) {

		System.out.println("[INFO - Registering Traffic Light Resource] : Received request for traffic light registration.");

		Map<String, Object> responseJsonObject = Utils.jsonParser(exchange.getRequestText());
		
        if(responseJsonObject == null){
        	
			System.out.println("[ERR  - Registering Traffic Light Resource] : Responding: BAD REQUEST");
			exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
			return;
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
            	for(String key : responseJsonObject.keySet()){
                	
            	    if(key.equals("trafficLightID")) {
            	    	
            		    // Insert the ID of the traffic light actuator and the "r" (red) value in the list of registered ones, and turn on the traffic light. 
            		    // In this case value = ipv6 address 
            		   
            		    String trafficLightIPAddress = (String) responseJsonObject.get(key);
            		    smartParkManager.getRegisteredTrafficLights().put(trafficLightIPAddress, "r");	// overwrite duplicates
            		   
            		    System.out.println("[INFO - Registering Traffic Light Resource] : Added " + key + ": " + trafficLightIPAddress + " to the list of registered Traffic Light actuators");
            		   
            		    exchange.respond(CoAP.ResponseCode.CREATED);
            		    
            		    turnOnTrafficLight(trafficLightIPAddress);
            		    
            	    } 
                	   
                }
            }
        }).start();
        
        /*for(String key : responseJsonObject.keySet()){
        	
    	    if(key.equals("trafficLightID")) {
    	    	
    		    // Insert the ID of the traffic light actuator and the "r" (red) value in the list of registered ones, and turn on the traffic light. 
    		    // In this case value = ipv6 address 
    		   
    		    String trafficLightIPAddress = (String) responseJsonObject.get(key);
    		    smartParkManager.getRegisteredTrafficLights().put(trafficLightIPAddress, "r");	// overwrite duplicates
    		   
    		    System.out.println("[INFO - Registering Traffic Light Resource] : Added " + key + ": " + trafficLightIPAddress + " to the list of registered Traffic Light actuators");
    		   
    		    exchange.respond(CoAP.ResponseCode.CREATED);
    		    
    		    turnOnTrafficLight(trafficLightIPAddress);
    		    
    	    } 
        	   
        }*/
               
				
	}

	private void turnOnTrafficLight(String trafficLightIPAddress) {
		
		Set<WebLink> resources = Utils.discoverResources(trafficLightIPAddress);//clientDiscovery.discover("rt=trafficLightLeds"); // This filtering is not working, that's why follows the foreach cycle
		
		for(WebLink resource : resources) {
			
			System.out.println("[DBG  - Registering Traffic Light Resource] : Resource: " + resource);
			
			if(resource.getAttributes().getResourceTypes().contains("trafficLightLeds")) {
				
				String baseServerURI = "coap://[" + trafficLightIPAddress + "]:5683";
				
				String jsonCommand = null;
				
				int occupiedParkSlots = 0;
					
				for(Entry<String, Boolean> registeredParkSlot : SmartParkManager.getRegisteredParkSlots().entrySet()) {
					
					if(registeredParkSlot.getValue() == true){
						occupiedParkSlots++;
					}
				}
				// Turn on traffic light RED if all park slots are already registered and occupied, GREEN otherwise
				if(occupiedParkSlots == SmartParkManager.getTotalParkSlots())
					jsonCommand = "{\"mode\":\"on\", \"color\":\"r\"}";
				else 
					jsonCommand = "{\"mode\":\"on\", \"color\":\"g\"}";
				
				System.out.println("[DBG  - Registering Traffic Light Resource] : Sending command: " + jsonCommand);
				
				String resourcePath = baseServerURI + resource.getURI();
				
				System.out.println("[INFO - Registering Traffic Light Resource] : Sending PUT request to [" + trafficLightIPAddress + "] to turn on the traffic light, URI: " + resourcePath);
				System.out.println();
				
				CoapClient clientPutRequest = new CoapClient(resourcePath);
				CoapResponse response = clientPutRequest.put(jsonCommand, MediaTypeRegistry.APPLICATION_JSON);
				
				System.out.println("[DBG  - Registering Traffic Light Resource] : Response code is: " + response.getCode());
				System.out.println("[DBG  - Registering Traffic Light Resource] : Response text is: " + response.getResponseText());
				System.out.println();
			}
		}
		
		//CoapResponse response = client.get();
		
		//System.out.println("INFO: Exposed resources: " + response.getResponseText());
		
		
	}

}
