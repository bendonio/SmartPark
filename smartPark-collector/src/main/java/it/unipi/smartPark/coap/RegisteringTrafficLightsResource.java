package it.unipi.smartPark.coap;

import java.sql.Timestamp;
import java.util.Map;
import java.util.Set;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;

import it.unipi.smartPark.SmartParkManager;
import it.unipi.smartPark.TelemetryDatabaseHandler;
import it.unipi.smartPark.Utils;

public class RegisteringTrafficLightsResource extends CoapResource {
	
	public RegisteringTrafficLightsResource(String resourcePath, CoAPCollector coapCollector) {
		
		super(resourcePath);
		
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
		if(!SmartParkManager.getFireDetected()) {
            	
        	    if(responseJsonObject.containsKey("trafficLightID")) {
        	    	// Insert the ID of the traffic light actuator and the "r" (red) or "g" (green) value in the list of registered ones, 
        	    	// and turn on the traffic light. 
        	    	
        		    String trafficLightID = (String) responseJsonObject.get("trafficLightID");
        		    
        		    if(SmartParkManager.getParkLotsOccupied() == SmartParkManager.getTotalParkLots())
        		    	SmartParkManager.getRegisteredTrafficLights().put(trafficLightID, "r");	// overwrite duplicates
        		    else
        		    	SmartParkManager.getRegisteredTrafficLights().put(trafficLightID, "g");	// overwrite duplicates
        		   
        		    System.out.println("[INFO - Registering Traffic Light Resource] : Added traffic light : " + trafficLightID + " to the list of registered Traffic Lights");
        		    System.out.println();
        		    
        		    exchange.respond(CoAP.ResponseCode.CREATED);
        		    
        		    SmartParkManager.setTotalTrafficLights(SmartParkManager.getTotalTrafficLights() +1 );
        		    
        		    turnOnTrafficLight(trafficLightID);
        		    
        	    } 
            	   
		} else {
        	String payload = "{\"reason\": \"Alarm system is ON\"}";
        	exchange.respond(CoAP.ResponseCode.FORBIDDEN, payload, MediaTypeRegistry.APPLICATION_JSON);
        }
	}

	private void turnOnTrafficLight(String trafficLightIPAddress) {
		
		Set<WebLink> resources = Utils.discoverResources(trafficLightIPAddress);
		//clientDiscovery.discover("rt=trafficLightLeds"); // This filtering is not working, that's why follows the foreach cycle
		
		for(WebLink resource : resources) {
			
			if(resource.getAttributes().getResourceTypes().contains("trafficLightLeds")) {
				
				String baseServerURI = "coap://[" + trafficLightIPAddress + "]:5683";
				
				String jsonCommand = null;
				
				int occupiedParkLots = SmartParkManager.getParkLotsOccupied();
					
				String status = null;
				// Turn on traffic light RED if all park lots are already registered and occupied, GREEN otherwise
				if(occupiedParkLots == SmartParkManager.getTotalParkLots()) {
					jsonCommand = "{\"mode\":\"on\", \"color\":\"r\"}";
					status = "red";
				}else { 
					jsonCommand = "{\"mode\":\"on\", \"color\":\"g\"}";
					status = "green";
				}
				
				Timestamp timestamp = new Timestamp(System.currentTimeMillis());

				TelemetryDatabaseHandler.saveUpdate("smart_traffic_light", timestamp, trafficLightIPAddress, status);
				
				String resourcePath = baseServerURI + resource.getURI();
				
				System.out.println("[INFO - Registering Traffic Light Resource] : Sending command to [" + trafficLightIPAddress + "] to turn on the traffic light");
				System.out.println();
				
				CoapClient clientPutRequest = new CoapClient(resourcePath);
				CoapResponse response = clientPutRequest.put(jsonCommand, MediaTypeRegistry.APPLICATION_JSON);
				
				System.out.println("[INFO - Registering Traffic Light Resource] : Response code is: " + response.getCode());
				System.out.println("[INFO - Registering Traffic Light Resource] : Response text is: " + response.getResponseText());
				System.out.println();
			}
		}
		
		
	}

}
