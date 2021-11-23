package it.unipi.smartPark.coap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import it.unipi.smartPark.FireAlarmSystem;
import it.unipi.smartPark.SmartParkManager;
import it.unipi.smartPark.Utils;

public class RegisteringParkSlotsResource extends CoapResource {
	
	private final SmartParkManager smartParkManager;
	private final ThreadPoolExecutor executor;

	public RegisteringParkSlotsResource(String resourcePath, CoAPCollector coapCollector) {
		
		super(resourcePath);
		
		this.smartParkManager = coapCollector.getSmartParkManager();
		this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
		
	}

	public void handleGET(CoapExchange exchange) {
		
		System.out.println(exchange.getRequestText());
		
		exchange.respond("Hello, I'm the CoAP Collector\n");
		
	}
	
	/**
	 * This function will handle the registration requests coming from Park Slot sensors.
	 * 
	 */
	public void handlePOST(CoapExchange exchange) {

		System.out.println("[DBG  - Registering Park Slots Resource] : Received POST request.");

		Map<String, Object> responseJsonObject = Utils.jsonParser(exchange.getRequestText());
		
        if(responseJsonObject == null){
        	
			System.out.println("[ERR  - Registering Park Slots Resource] : Responding: BAD REQUEST");
			exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
			return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
            	if(!SmartParkManager.getFireDetected()) {
                	// Registration can be done only if the alarm system is not started
                	
        	        if(responseJsonObject.containsKey("parkSlotID")) {
        	        	
        	        	String parkSlotID = (String) responseJsonObject.get("parkSlotID");
        	        	
        	        	Boolean parkSlotIsOccupied = false;
        	        	
        	        	if(responseJsonObject.containsKey("occupied")) {
        	        		parkSlotIsOccupied = (Boolean) responseJsonObject.get("occupied");
        	        	}
        	        	SmartParkManager.getRegisteredParkSlots().put(parkSlotID, parkSlotIsOccupied);
        	        	
        	        	System.out.println("[INFO - Registering Park Slots Resource] : Added parkSlotID" + ": " + parkSlotID + " to the list of registered Park Slot sensors");
        	        
        	        	// Check if this is a new parkSlot or it was already counted in smartParkManager
        	        	if(smartParkManager.notCountedParkSlots()){
        	        		smartParkManager.setTotalParkSlots(SmartParkManager.getTotalParkSlots() + 1);
        	        		System.out.println("[DBG  - Registering Park Slots Resource] : Incremented totalParkSlots, new value is: " + SmartParkManager.getTotalParkSlots());
        	 			   
        	 		    }
        	        	
        	        	System.out.println("[DBG  - Registering Park Slots Resource] : Host address: " + exchange.getSourceAddress().getHostAddress());
        	 		   
        	 		    exchange.respond(CoAP.ResponseCode.CREATED);
        	 		   
        	 		    // Find all the resources exposed by the sensor just registered
        	 		    List<String> obsResourcesPath = getParkSensorObservableResources(parkSlotID);
        	 		   
        	 		    registerToObservableResources(obsResourcesPath, parkSlotID);
        	 		    
        	 		    executor.submit(smartParkManager);
        	        }
                }
            }
       }).start();
        /*
        //SmartParkManager.getFirealarmsystem();
		if(!SmartParkManager.getFireDetected()) {
        	// Registration can be done only if the alarm system is not started
        	
	        if(responseJsonObject.containsKey("parkSlotID")) {
	        	
	        	String parkSlotID = (String) responseJsonObject.get("parkSlotID");
	        	
	        	Boolean parkSlotIsOccupied = false;
	        	
	        	if(responseJsonObject.containsKey("occupied")) {
	        		parkSlotIsOccupied = (Boolean) responseJsonObject.get("occupied");
	        	}
	        	SmartParkManager.getRegisteredParkSlots().put(parkSlotID, parkSlotIsOccupied);
	        	
	        	System.out.println("[INFO - Registering Park Slots Resource] : Added parkSlotID" + ": " + parkSlotID + " to the list of registered Park Slot sensors");
	        
	        	// Check if this is a new parkSlot or it was already counted in smartParkManager
	        	if(smartParkManager.notCountedParkSlots()){
	        		smartParkManager.setTotalParkSlots(SmartParkManager.getTotalParkSlots() + 1);
	        		System.out.println("[DBG  - Registering Park Slots Resource] : Incremented totalParkSlots, new value is: " + SmartParkManager.getTotalParkSlots());
	 			   
	 		    }
	        	
	        	System.out.println("[DBG  - Registering Park Slots Resource] : Host address: " + exchange.getSourceAddress().getHostAddress());
	 		   
	 		    exchange.respond(CoAP.ResponseCode.CREATED);
	 		   
	 		    // Find all the resources exposed by the sensor just registered
	 		    List<String> obsResourcesPath = getParkSensorObservableResources(parkSlotID);
	 		   
	 		    registerToObservableResources(obsResourcesPath, parkSlotID);
	 		    
	 		    executor.submit(smartParkManager);
	        }
        }
               */
				
	}

	private void registerToObservableResources(List<String> obsResourcesPath, final String sensorIpAddress) {
		
		String baseServerURI = "coap://[" + sensorIpAddress + "]:5683/";
		
		for(String obsResourcePath : obsResourcesPath) {
			
			String obsResourceURI = baseServerURI + obsResourcePath; 
			
			System.out.println("[INFO - Registering Park Slots Resource] : Sending observing request to [" + sensorIpAddress + "], URI: " + obsResourceURI);
			System.out.println();
			CoapClient client = new CoapClient(obsResourceURI);
			
			CoapObserveRelation relation = client.observe(
															new CoapHandler(){
				
																@Override 
																public void onLoad(CoapResponse response) {
																	System.out.println("[INFO - Registering Park Slots Resource] : Got observe update from Park Slot: " + sensorIpAddress);
																	
																	if(!SmartParkManager.getFireDetected()) {
																		String content = response.getResponseText();
																		
																		//System.out.println("[INFO - Registering Park Slots Resource] : Got observe update from Park Slot: " + sensorIpAddress);
																		System.out.println("[INFO - Registering Park Slots Resource] : Value observed: " + content);
																		
																		Map<String, Object> responseJsonObject = Utils.jsonParser(content);
																		
	
																        if(responseJsonObject == null){
	
																            System.out.println("[ERR  - Registering Park Slots Resource] : JSON parsing observing resource error.");
																			return;
																        }
																        
																        handleObservableMessage(responseJsonObject, sensorIpAddress);
																	}
																}
																

																@Override 
																public void onError() {
																	
																	System.out.println("[ERR  - Registering Park Slots Resource] : Error during observing " + client.getURI() + ". Closing Observe relation");
																	client.shutdown();
																	
																}
															}
														  );
			
			SmartParkManager.getActiveobserverelations().put(sensorIpAddress, relation);
			System.out.println("[DBG  - Registering Park Slots Resource] : Number of active observing relations: " + SmartParkManager.getActiveobserverelations().size());
			
		}
	}

	protected void handleObservableMessage(Map<String, Object> responseJsonObject, String sensorIpAddress) {
		
		if(!SmartParkManager.getRegisteredParkSlots().containsKey(sensorIpAddress)) {
			   // Observable message from non registered server (even if not possible)
			   
			   System.out.println("[INFO - Registering Park Slots Resource] : Observable message from sensor not registered, do nothing. Server IP:" + sensorIpAddress);
			   
			   return;
			   
		}
		
		
		Boolean parkSlotOccupied = false;
     	   
 	    if(responseJsonObject.containsKey("occupied"))
 		   parkSlotOccupied = (Boolean) responseJsonObject.get("occupied");
 	    else {
 	    	System.out.println("[INFO - Registering Park Slots Resource] : Observable message not containing info about the park, do nothing");
 	    	return;
 	    }
 	    
 	    // Update the smartParkManager about the new status of the park slot
 	    SmartParkManager.getRegisteredParkSlots().put(sensorIpAddress, parkSlotOccupied);
 	    
 	    executor.submit(smartParkManager);
 	    
 	    System.out.println();
    }
		

	private List<String> getParkSensorObservableResources(String sensorIpAddress) {
		
		System.out.println("[INFO - Registering Park Slots Resource] : Sending request about exposed resource to [" + sensorIpAddress + "]");
		
		CoapClient client = new CoapClient("coap://[" + sensorIpAddress + "]:5683/.well-known/core");
		
		CoapResponse response = client.get();
		
		System.out.println("[DBG  - Registering Park Slots Resource]: Exposed resources: " + response.getResponseText());
		
		List<String> obsResourcesPath = parseWellKnownResponse(response.getResponseText());
		
		return obsResourcesPath;
	}

	/*
	 * Function to parse the response to the request at the well-known resource
	 * 
	 * @param availableResources the list of resources available returned by the server
	 * @return 					 the list of observable resources among all the resources of the server
	 * 
	 */
	private List<String> parseWellKnownResponse(String availableResources) {
		// Example of response "</.well-known/core>;ct=40,</slotState>;title="Presence sensor";obs"
		
		List<String> obsResourcesPath = new ArrayList<String>();
		
		for(String resource : availableResources.split(",")) {
			
			if(resource.contains("obs")) {
				
				obsResourcesPath.add(resource.split(";")[0].replace("</", "").replace(">", ""));
				
				System.out.println("[DBG  - Registering Park Slots Resource] : Exposed observable resource found: " + resource);
			
			}
		}
		
		return obsResourcesPath;
	}

}
