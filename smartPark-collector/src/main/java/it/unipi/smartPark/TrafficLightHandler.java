package it.unipi.smartPark;

import java.sql.Timestamp;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;

public class TrafficLightHandler implements Runnable {

	/**
	 * Functions that checks the number of occupied park lots. 
	 * If all the lots are now occupied triggers the message towards the smart traffic light actuator(s);
	 * if the park was full and now has one lot free sends the message towards the smart traffic light.
	 */
	@Override
	public void run() {
		
		SmartParkManager.setAndGetParkLotsOccupied();
		
		System.out.println("[INFO - Traffic Light Handler] : Park lots occupied are: " + 
							SmartParkManager.getParkLotsOccupied()+ "/" + SmartParkManager.getTotalParkLots());
		
		Boolean parkIsFull = (SmartParkManager.getParkLotsOccupied() == SmartParkManager.getTotalParkLots());
		for(Entry<String, String> trafficLight : SmartParkManager.getRegisteredTrafficLights().entrySet()) {
				
				String trafficLightIPAddress = trafficLight.getKey();
				
				String jsonCommand = null;
				String status = null;
				
				String trafficLightPreviousColor = trafficLight.getValue();
				
				if(parkIsFull) {
					System.out.println("[INFO - Traffic Light Handler] : All park lots occupied");
					
					jsonCommand= "{\"color\":\"r\"}";
					trafficLight.setValue("r");
					
					status = "red";
					
				}  else {
					
					jsonCommand = "{\"color\":\"g\"}";
					trafficLight.setValue("g");
					
					status = "green";
				}
				
				// Send the command only if the new color is different from the previous one
				if(!trafficLightPreviousColor.equals(trafficLight.getValue())) {
					
					Set<WebLink> resources = Utils.discoverResources(trafficLightIPAddress); 
					
					for(WebLink resource : resources) {
						
						if(resource.getAttributes().getResourceTypes().contains("trafficLightLeds")) {
							
							System.out.println("[INFO - Traffic Light Handler] : Sending command: " + jsonCommand);
							
							String baseServerURI = "coap://[" + trafficLightIPAddress + "]:5683";
							
							String resourcePath = baseServerURI + resource.getURI();
							
							Timestamp timestamp = new Timestamp(System.currentTimeMillis());
							
							TelemetryDatabaseHandler.saveUpdate("smart_traffic_light", timestamp, trafficLightIPAddress, status);
							
							System.out.println("[INFO - Traffic Light Handler] : Sending command to [" + trafficLightIPAddress + "] to switch the traffic light color!");
							System.out.println();
							
							CoapClient clientPutRequest = new CoapClient(resourcePath);
							CoapResponse response = clientPutRequest.put(jsonCommand, MediaTypeRegistry.APPLICATION_JSON);
							
							handleResponse(response);
							
							
						}
					
					}
				}
		}

	}

	private void handleResponse(CoapResponse response) {
		if(ResponseCode.isClientError(response.getCode())) {
			
			System.out.println("[ERR  - Traffic Light Handler] : Client error: " + response.getCode());
		} else if(ResponseCode.isServerError(response.getCode())) {
			
			System.out.println("[ERR - Traffic Light Handler] : Server error: " + response.getCode());
		} else {
			
			System.out.println("[INFO - Traffic Light Handler] : OK: Request fulfilled");
			
		}
		
	}

}

