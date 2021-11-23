package it.unipi.smartPark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.MediaTypeRegistry;

public class SmartParkManager implements Runnable {
	
	private final static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
	
	private final static Map<String, Boolean> registeredParkSlots = new HashMap<String, Boolean>();// Park sensors registered through
																							       // their IPv6 address, and if they are occupied or not.
	private final static Map<String, CoapObserveRelation> activeObserveRelations = new HashMap<String, CoapObserveRelation>(); // Storing observe relation to cancel them 
																															   // when alarm starts.
	
	private static int totalParkSlots;
	private static int parkSlotsOccupied;
	
	private final static Map<String, String> registeredTrafficLights = new HashMap<String, String>(); //ID, Color
	private final int totalTrafficLights = 1; // For the moment I assume there's only 1 traffic light. 
											  //I leave it like this because it could be upgraded in the future.
	
	private final static Map<String, Boolean> registeredFlameDetectors = new HashMap<String, Boolean>(); // ID, Fire detected
	
	
	private static final FireAlarmSystem fireAlarmSystem = new FireAlarmSystem();
	private static Boolean fireDetected = false;
			
	public SmartParkManager(int totalParkSlots) {
		
		this.totalParkSlots = totalParkSlots;
		
		this.parkSlotsOccupied = 0;
		
	}

	public static int getTotalParkSlots() {
		return totalParkSlots;
	}

	public void setTotalParkSlots(int totalParkSlots) {
		this.totalParkSlots = totalParkSlots;
	}

	public static int getParkSlotsOccupied() {
		return parkSlotsOccupied;
	}

	public void setParkSlotsOccupied(int parkSlotsOccupied) {
		
		this.parkSlotsOccupied = parkSlotsOccupied;
	}
	
	public static Map<String, Boolean> getRegisteredParkSlots() {
			
			return registeredParkSlots;
			
	}
	
	public static Map<String, CoapObserveRelation> getActiveobserverelations() {
		return activeObserveRelations;
	}

	public static Map<String, String> getRegisteredTrafficLights() {
		
		return registeredTrafficLights;
		
	}
	
	public static Map<String, Boolean> getRegisteredFlameDetectors() {
		return registeredFlameDetectors;
	}
	
	public static FireAlarmSystem getFirealarmsystem() {
		return fireAlarmSystem;
	}

	public static Boolean getFireDetected() {
		return fireDetected;
	}

	public static void setFireDetected(Boolean fireDetected) {
		SmartParkManager.fireDetected = fireDetected;
	}

	public static void startAlarmSystem() {
		
		System.out.println("[INFO - Smart Park Manager] : Starting alarm system");
		fireAlarmSystem.start();
		
		
		
		System.out.println("[DBG  - Smart Park Manager] : Number of active observing relations: " + activeObserveRelations.size());
            	
    	Set<Entry<String, CoapObserveRelation>> observeRelations = activeObserveRelations.entrySet();
    	
    	Iterator<Entry<String, CoapObserveRelation>> iterator = observeRelations.iterator();
    	
    	while(iterator.hasNext()) {
    		Entry<String, CoapObserveRelation> observeRelation = iterator.next();
    		
    		observeRelation.getValue().reactiveCancel();
    		iterator.remove();
    		
    		System.out.println("[DBG  - Smart Park Manager] : Observe relation of sensor: " + observeRelation.getKey() + " canceled and removed ");
			System.out.println("[DBG  - Smart Park Manager] : Number of active observing relations: " + activeObserveRelations.size());
    		
    	}
	}

	/*
	 * Functions that checks if the number of registered park slots is greater than the initial value of park slots
	 * 
	 */
	public boolean notCountedParkSlots() {
		
		if(this.registeredParkSlots.size() > this.totalParkSlots)
			return true;
		else 
			return false;
	}

	/*
	 * Functions that checks the number of occupied park slots. 
	 * If all the slots are occupied triggers the message towards the smart traffic light actuator(s)
	 * 
	 */
	public void checkOccupiedSlots() {
		
		int counter = 0;
		
		for(Entry<String, Boolean> registeredParkSlot : this.registeredParkSlots.entrySet()) {
			
			if(registeredParkSlot.getValue() == true){
				counter++;
			}
		}
		
		this.parkSlotsOccupied = counter;
		
		System.out.println("[INFO - Smart Park Manager] : Park slots occupied are: " + parkSlotsOccupied + "/" + this.totalParkSlots);
		
		for(Entry<String, String> trafficLight : registeredTrafficLights.entrySet()) {
				
				String trafficLightIPAddress = trafficLight.getKey();
				
				Set<WebLink> resources = Utils.discoverResources(trafficLightIPAddress); 
				
				for(WebLink resource : resources) {
					
					System.out.println("[DBG  - Smart Park Manager] : Resource: " + resource);
					
					if(resource.getAttributes().getResourceTypes().contains("trafficLightLeds")) {
						
						String jsonCommand = null;
						
						String trafficLightPreviousColor = trafficLight.getValue();
						
						if(this.parkSlotsOccupied == this.totalParkSlots) {
							//All the park slots are occupied --> TODO Send a notification to the traffic light actuator
							System.out.println("[INFO - Smart Park Manager] : All park slots occupied");
							
							jsonCommand= "{\"color\":\"r\"}";
							trafficLight.setValue("r");
							
							System.out.println("[INFO - Smart Park Manager] : Sending command: " + jsonCommand);
							
						} else {
							
							jsonCommand = "{\"color\":\"g\"}";
							trafficLight.setValue("g");
							
						}
						
						
						
						if(!trafficLightPreviousColor.equals(trafficLight.getValue())) {
							// Send the update only if the new color is different
						
							System.out.println("[INFO - Smart Park Manager] : Sending command: " + jsonCommand);
	
							String baseServerURI = "coap://[" + trafficLightIPAddress + "]:5683";
							
							String resourcePath = baseServerURI + resource.getURI();
							
							System.out.println("[INFO - Smart Park Manager] : Sending PUT request to [" + trafficLightIPAddress + "] to turn on the traffic light, URI: " + resourcePath);
							System.out.println();
							
							CoapClient clientPutRequest = new CoapClient(resourcePath);
							CoapResponse response = clientPutRequest.put(jsonCommand, MediaTypeRegistry.APPLICATION_JSON);
							
							if(ResponseCode.isClientError(response.getCode())) {
								
								System.out.println("[ERR  - Smart Park Manager] : Client error: " + response.getCode());
							} else if(ResponseCode.isServerError(response.getCode())) {
								
								System.out.println("[ERR - Smart Park Manager] : Server error: " + response.getCode());
							} else {
								
								System.out.println("[INFO - Smart Park Manager] : OK: Request fulfilled");
								
							}
						}
						
						System.out.println();
					}
				}
					
		}
		
	}

	@Override
	public void run() {
		
		checkOccupiedSlots();
		
	}

	public static void stopAlarmSystem() {
		
		fireDetected = false;
		
		fireAlarmSystem.stop();
		
	}

	
}
