package it.unipi.smartPark;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.eclipse.californium.core.CoapObserveRelation;

public class SmartParkManager {
	
	private final static Map<String, Boolean> registeredParkLots = new HashMap<String, Boolean>();// Park sensors registered through
																							       // their IPv6 address, and if they are occupied or not.
	
	private final static Map<String, CoapObserveRelation> activeObserveRelations = new HashMap<String, CoapObserveRelation>(); // Storing observe relation to cancel them 
																															   // when alarm starts.
	
	private static int totalParkLots;
	private static int parkLotsOccupied;
	
	private final static Map<String, String> registeredTrafficLights = new HashMap<String, String>(); //ID, Color
	private static int totalTrafficLights = 0; // For the moment I assume there's only 1 traffic light. 
											  	//I leave it like this because it could be upgraded in the future.
	
	private final static Map<String, Boolean> registeredFlameDetectors = new HashMap<String, Boolean>(); // ID, Fire detected
	
	
	private static final FireAlarmSystem fireAlarmSystem = new FireAlarmSystem();
	private static Boolean fireDetected = false;
			
	public SmartParkManager(int totalParkLots) {
		
		SmartParkManager.totalParkLots = totalParkLots;
		
		SmartParkManager.parkLotsOccupied = 0;
		
	}
	
	public SmartParkManager() {
		
		SmartParkManager.totalParkLots = 0;
		
		SmartParkManager.parkLotsOccupied = 0;
		
	}

	public static int getTotalParkLots() {
		return totalParkLots;
	}

	public static void setTotalParkLots(int totalParkLots) {
		SmartParkManager.totalParkLots = totalParkLots;
	}

	public static int setAndGetParkLotsOccupied() {
		int occupiedParkLots = 0;
		for(Entry<String, Boolean> registeredParkLot : SmartParkManager.getRegisteredParkLots().entrySet()) {
			
			if(registeredParkLot.getValue() == true){
				occupiedParkLots++;
			}
		}
		
		SmartParkManager.parkLotsOccupied = occupiedParkLots;
		return occupiedParkLots;
	}

	public static int getParkLotsOccupied() {
		
		return SmartParkManager.parkLotsOccupied;
	}
	
	public static void setParkLotsOccupied(int parkLotsOccupied) {
		
		SmartParkManager.parkLotsOccupied = parkLotsOccupied;
	}
	
	public static int getTotalTrafficLights() {
		return totalTrafficLights;
	}

	public static void setTotalTrafficLights(int totalTrafficLights) {
		SmartParkManager.totalTrafficLights = totalTrafficLights;
	}

	public static Map<String, Boolean> getRegisteredParkLots() {
			
			return registeredParkLots;
			
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

	/**
	 * Functions that triggers the alarm system and cancels all the active observe relation of the collector
	 */
	public static void startAlarmSystem() {
		
		System.out.println("[INFO - Smart Park Manager] : Starting alarm system");
		
		Set<Entry<String, CoapObserveRelation>> observeRelations = activeObserveRelations.entrySet();
    	
    	Iterator<Entry<String, CoapObserveRelation>> iterator = observeRelations.iterator();
    	
    	while(iterator.hasNext()) {
    		Entry<String, CoapObserveRelation> observeRelation = iterator.next();
    		
    		observeRelation.getValue().reactiveCancel();
    		iterator.remove();
    		
    		
    		
    	}
    	fireAlarmSystem.start();
    	System.out.println("[INFO  - Smart Park Manager] : All observe relations canceled and removed ");
    	System.out.println();
	}

	/**
	 * Functions that checks if the number of registered park lots is greater than the initial value of park lots
	 * 
	 */
	public static boolean notCountedParkLots() {
		
		if(registeredParkLots.size() > totalParkLots)
			return true;
		else 
			return false;
	}

	public static void stopAlarmSystem() {
		
		fireDetected = false;
		
		fireAlarmSystem.stop();
		
	}

	
}
