package it.unipi.smartPark.coap;

import java.math.BigDecimal;
import java.sql.Timestamp;
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
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;

import it.unipi.smartPark.SmartParkManager;
import it.unipi.smartPark.TelemetryDatabaseHandler;
import it.unipi.smartPark.TrafficLightHandler;
import it.unipi.smartPark.Utils;

public class RegisteringParkLotsResource extends CoapResource {
	
	private final ThreadPoolExecutor executor;

	public RegisteringParkLotsResource(String resourcePath, CoAPCollector coapCollector) {
		
		super(resourcePath);
		
		coapCollector.getSmartParkManager();
		this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
		
	}
	
	/**
	 * This function handles the registration requests coming from Park Lot sensors.
	 * 
	 */
	public void handlePOST(CoapExchange exchange) {

		System.out.println("[INFO - Registering Park Lots Resource] : Received registration request from a park lot sensor.");

		Map<String, Object> responseJsonObject = Utils.jsonParser(exchange.getRequestText());
		
        if(responseJsonObject == null){
        	
			System.out.println("[ERR  - Registering Park Lots Resource] : Responding: BAD REQUEST");
			exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
			return;
        }
        if(!SmartParkManager.getFireDetected()) {
        // Registration can be done only if the alarm system is not started
        	
	        if(responseJsonObject.containsKey("parkLotID")) {
	        	
	        	String parkLotID = (String) responseJsonObject.get("parkLotID");
	        	
	        	Boolean parkLotIsOccupied = false;
	        	
	        	if(responseJsonObject.containsKey("occupied")) {
	        		parkLotIsOccupied = (Boolean) responseJsonObject.get("occupied");
	        	}
	       
	        	SmartParkManager.getRegisteredParkLots().put(parkLotID, parkLotIsOccupied);
	        	
	        	System.out.println("[INFO - Registering Park Lots Resource] : Added park lot : " + parkLotID + " to the list of registered Park Lot sensors");
	        
	        	// Check if this is a new parkLot or it was already counted in smartParkManager
	        	if(SmartParkManager.notCountedParkLots()){
	       
	        		SmartParkManager.setTotalParkLots(SmartParkManager.getTotalParkLots() + 1);
	       	   
	 		    }
	 		   
	 		    exchange.respond(CoAP.ResponseCode.CREATED);
	 		   
	 		    // Find all the resources exposed by the sensor just registered
	 		    List<String> obsResourcesPath = getParkSensorObservableResources(parkLotID);
	 		   
	 		    registerToObservableResources(obsResourcesPath, parkLotID);
	 		    
	 		    TrafficLightHandler trafficLightHandler = new TrafficLightHandler();
	 		    executor.submit(trafficLightHandler);
	        }
        } else {
        	String payload = "{\"reason\": \"Alarm system is ON\"}";
        	exchange.respond(CoAP.ResponseCode.FORBIDDEN, payload, MediaTypeRegistry.APPLICATION_JSON);
        }
    }

	private void registerToObservableResources(List<String> obsResourcesPath, final String sensorIpAddress) {
		
		String baseServerURI = "coap://[" + sensorIpAddress + "]:5683/";
		
		for(String obsResourcePath : obsResourcesPath) {
			
			String obsResourceURI = baseServerURI + obsResourcePath; 
			
			System.out.println("[INFO - Registering Park Lots Resource] : Sending observing request to [" + sensorIpAddress + "], URI: " + obsResourceURI);
			System.out.println();
			CoapClient client = new CoapClient(obsResourceURI);
			
			CoapObserveRelation relation = client.observe(
															new CoapHandler(){
				
																@Override 
																public void onLoad(CoapResponse response) {
																	System.out.println("[INFO - Registering Park Lots Resource] : Got observe update from Park Lot: " + sensorIpAddress);
																	
																	if(!SmartParkManager.getFireDetected()) {
																		String content = response.getResponseText();
																		
																		System.out.println("[INFO - Registering Park Lots Resource] : Value observed: " + content);
																		
																		Map<String, Object> responseJsonObject = Utils.jsonParser(content);
																		
	
																        if(responseJsonObject == null){
	
																            System.out.println("[ERR  - Registering Park Lots Resource] : JSON parsing observing resource error.");
																			return;
																        }
																        
																        new Thread(new Runnable() {
																            @Override
																            public void run() {
																            	handleObservableMessage(responseJsonObject, sensorIpAddress);
																            }
																        }).start();
																	}
																}
																

																@Override 
																public void onError() {
																	
																	System.out.println("[ERR  - Registering Park Lots Resource] : Error during observing " + client.getURI() + ". Closing observe relation");
																	SmartParkManager.getActiveobserverelations().remove(sensorIpAddress);
																	SmartParkManager.getRegisteredParkLots().remove(sensorIpAddress);
																	SmartParkManager.setTotalParkLots(SmartParkManager.getTotalParkLots()-1);
																	client.shutdown();
																	
																}
															}
														  );
			
			SmartParkManager.getActiveobserverelations().put(sensorIpAddress, relation);
			System.out.println();
		}
	}

	protected void handleObservableMessage(Map<String, Object> responseJsonObject, String sensorIpAddress) {
		
		if(!SmartParkManager.getRegisteredParkLots().containsKey(sensorIpAddress)) {
			   // Observable message from non registered server (even if not possible)
			   
			   System.out.println("[INFO - Registering Park Lots Resource] : Observable message from sensor not registered, do nothing. Server IP:" + sensorIpAddress);
			   
			   return;
			   
		}
		
		Boolean parkLotOccupied = false;
		Timestamp readingTimestamp = null;
     	   
 	    if(responseJsonObject.containsKey("occupied"))
 	    	parkLotOccupied = (Boolean) responseJsonObject.get("occupied");
 	    else {
 	    	System.out.println("[INFO - Registering Park Lots Resource] : Observable message not containing info about the park, do nothing");
 	    	return;
 	    }
 	    
 	    if(responseJsonObject.containsKey("timestamp")) {
 	    	Double timestampDouble = (Double) responseJsonObject.get("timestamp");
 	    	String timestampString = String.format("%.0f", Double.parseDouble(timestampDouble.toString()));
 	    	
 	    	readingTimestamp = new Timestamp(new BigDecimal(timestampString).longValue());
 	    	
 	    } else {
 	    	readingTimestamp = new Timestamp(System.currentTimeMillis());
 	    }
 	    
 	    String status = (parkLotOccupied) ? "occupied" : "free";
 	    TelemetryDatabaseHandler.saveUpdate("park_lot", readingTimestamp, sensorIpAddress, status);
 	    
 	    // Update the smartParkManager about the new status of the park lot
 	    SmartParkManager.getRegisteredParkLots().put(sensorIpAddress, parkLotOccupied);
 	    
 	    TrafficLightHandler trafficLightHandler = new TrafficLightHandler();
 	    executor.submit(trafficLightHandler);
 	    
 	    System.out.println();
    }
		

	private List<String> getParkSensorObservableResources(String sensorIpAddress) {
		
		System.out.println("[INFO - Registering Park Lots Resource] : Sending request about exposed resource to [" + sensorIpAddress + "]");
		
		CoapClient client = new CoapClient("coap://[" + sensorIpAddress + "]:5683/.well-known/core");
		
		CoapResponse response = client.get();
		
		List<String> obsResourcesPath = parseWellKnownResponse(response.getResponseText());
		
		return obsResourcesPath;
	}

	/**
	 * Function to parse the response to the request at the well-known resource
	 * 
	 * @param availableResources the list of resources available returned by the server
	 * @return 					 the list of observable resources among all the resources of the server
	 * 
	 */
	private List<String> parseWellKnownResponse(String availableResources) {
		// Example of response "</.well-known/core>;ct=40,</lotState>;title="Presence sensor";obs"
		
		List<String> obsResourcesPath = new ArrayList<String>();
		
		for(String resource : availableResources.split(",")) {
			
			if(resource.contains("obs")) {
				
				obsResourcesPath.add(resource.split(";")[0].replace("</", "").replace(">", ""));
				
			
			}
		}
		
		return obsResourcesPath;
	}

}
