package it.unipi.smartPark;

import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.WebLink;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import it.unipi.smartPark.mqtt.MQTTCollector;

public class FireAlarmSystem {
	
	private static Boolean started = false;
	private final String flameDetectorAlarmTopic = "flame-detector-%s/alarm-start"; 	//Topic to publish info about the alarm
	

	public static Boolean isStarted() {
		return started;
	}

	public void start() {
		
		
		System.out.println("[INFO - Alarm System] : Alarm system started!");
		
		String startFireAlarmJsonString = "{\"alarm\": true}";
		
		// Send a message to the flame detector and the traffic lights to turn on the red led. 
		sendFireAlarmToFlameDetectors(startFireAlarmJsonString);
		sendFireAlarmToTrafficLights(startFireAlarmJsonString);
		
		// Send a message to the parking lots to stop their job.
		sendFireAlarmToParkLots(startFireAlarmJsonString);
		
		started = true;
		
		
	}

	private void sendFireAlarmToParkLots(String startFireAlarmJsonString) {
		
		System.out.println("[INFO - Alarm System] : Sending alarm to registered park lots, then unregistering them");
		
		Set<Entry<String, Boolean>> registeredParkLots = SmartParkManager.getRegisteredParkLots().entrySet();
    	
    	Iterator<Entry<String, Boolean>> registeredParkLotsIterator = registeredParkLots.iterator();
    	
    	while(registeredParkLotsIterator.hasNext()) {
    		Entry<String, Boolean> registeredParkLot = registeredParkLotsIterator.next();
    		
    		String parkLotIPAddress = registeredParkLot.getKey();
			Set<WebLink> parkLotResources = Utils.discoverResources(parkLotIPAddress);
			
			for(WebLink parkLotResource : parkLotResources) {
				
				if(parkLotResource.getAttributes().getResourceTypes().contains("alarmSystem")) {
					
					String baseServerURI = "coap://[" + parkLotIPAddress + "]:5683";
					
					String resourcePath = baseServerURI + parkLotResource.getURI();
					
					System.out.println("[INFO - Alarm System] : Sending PUT request to [" + parkLotIPAddress + "] to start the alarm");
					System.out.println();
					
					CoapClient clientPutRequest = new CoapClient(resourcePath);
					CoapResponse response = clientPutRequest.put(startFireAlarmJsonString, MediaTypeRegistry.APPLICATION_JSON);
					
					System.out.println("[INFO - Alarm System] : Response code is: " + response.getCode());
					System.out.println("[INFO  - Alarm System] : Response text is: " + response.getResponseText());
					System.out.println();
					registeredParkLotsIterator.remove();
					SmartParkManager.setTotalParkLots(SmartParkManager.getTotalParkLots()-1);
					
					
				}
    
    		
			}

    		System.out.println("[INFO - Alarm System] : Park lot: " + registeredParkLot.getKey() + " canceled and removed ");
    	}
    	
    	SmartParkManager.setParkLotsOccupied(0);
		
	}

	private void sendFireAlarmToTrafficLights(String startFireAlarmJsonString) {
		
		System.out.println("[INFO - Alarm System] : Sending alarm to registered traffic lights, then unregistering them");
		
		Set<Entry<String, String>> registeredTrafficLights = SmartParkManager.getRegisteredTrafficLights().entrySet();
    	
    	Iterator<Entry<String, String>> registeredTrafficLightsIterator = registeredTrafficLights.iterator();
    	
    	while(registeredTrafficLightsIterator.hasNext()) {
    		Entry<String, String> registeredTrafficLight = registeredTrafficLightsIterator.next();
    		
    		String trafficLightIPAddress = registeredTrafficLight.getKey();
			Set<WebLink> trafficLightResources = Utils.discoverResources(trafficLightIPAddress);
			
			for(WebLink trafficLightResource : trafficLightResources) {
				
					if(trafficLightResource.getAttributes().getResourceTypes().contains("alarmSystem")) {
					
					String baseServerURI = "coap://[" + trafficLightIPAddress + "]:5683";
					
					String resourcePath = baseServerURI + trafficLightResource.getURI();
					
					System.out.println("[INFO - Alarm System] : Sending PUT request to [" + trafficLightIPAddress + "] to start the alarm");
					System.out.println();
					
					CoapClient clientPutRequest = new CoapClient(resourcePath);
					CoapResponse response = clientPutRequest.put(startFireAlarmJsonString, MediaTypeRegistry.APPLICATION_JSON);
					
					System.out.println("[INFO - Alarm System] : Response code is: " + response.getCode());
					System.out.println("[INFO - Alarm System] : Response text is: " + response.getResponseText());
					System.out.println();
					
					registeredTrafficLightsIterator.remove();
					SmartParkManager.setTotalTrafficLights(SmartParkManager.getTotalTrafficLights()-1);
					
				}
				
			}
    	
    	}
	
	}

	private void sendFireAlarmToFlameDetectors(String startFireAlarmJsonString) {
		
		MqttMessage startAlarmMessage = new MqttMessage(startFireAlarmJsonString.getBytes());
		try {
			
			for(Entry<String, Boolean> flameDetector : SmartParkManager.getRegisteredFlameDetectors().entrySet()) {
				
				String flameDetectorID = flameDetector.getKey();
				
				MQTTCollector.getMqttClient().publish(String.format(flameDetectorAlarmTopic, flameDetectorID), startAlarmMessage);
				
				SmartParkManager.getRegisteredFlameDetectors().put(flameDetectorID, true);
				
			}
		
			
		} catch (MqttException me) {
			
			System.out.println("[ERR  - Alarm System] : Error publishing alarm start message!");
			
			me.printStackTrace();
		}
		
		System.out.println();
	}

	public void stop() {
		
		System.out.println("[INFO - Alarm System] : Stopping alarm system!");
		
		started = false;
		
		SmartParkManager.setFireDetected(false);
		
	}

}
