package it.unipi.smartPark;

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
		
		// Send a message to the parking slots to stop their job.
		
		sendFireAlarmToParkSlots(startFireAlarmJsonString);
		
		started = true;
		
		
	}

	private void sendFireAlarmToParkSlots(String startFireAlarmJsonString) {
		
		System.out.println("[INFO - Alarm System] : Sending alarm to registered park slots");
		
		for(Entry<String, Boolean> registeredParkSlot : SmartParkManager.getRegisteredParkSlots().entrySet()) {
			
			String parkSlotIPAddress = registeredParkSlot.getKey();
			Set<WebLink> parkSlotResources = Utils.discoverResources(parkSlotIPAddress);
			
			for(WebLink parkSlotResource : parkSlotResources) {
				
				if(parkSlotResource.getAttributes().getResourceTypes().contains("alarmSystem")) {
					
					String baseServerURI = "coap://[" + parkSlotIPAddress + "]:5683";
					
					System.out.println("[DBG  - Alarm System] : Sending command: " + startFireAlarmJsonString);
					
					String resourcePath = baseServerURI + parkSlotResource.getURI();
					
					System.out.println("[INFO - Alarm System] : Sending PUT request to [" + parkSlotIPAddress + "] to start the alarm");
					System.out.println();
					
					CoapClient clientPutRequest = new CoapClient(resourcePath);
					CoapResponse response = clientPutRequest.put(startFireAlarmJsonString, MediaTypeRegistry.APPLICATION_JSON);
					
					System.out.println("[DBG  - Alarm System] : Response code is: " + response.getCode());
					System.out.println("[DBG  - Alarm System] : Response text is: " + response.getResponseText());
					System.out.println();
					
				}
			}
			
		}
		
		
	}

	private void sendFireAlarmToTrafficLights(String startFireAlarmJsonString) {
		
		System.out.println("[INFO - Alarm System] : Sending alarm to registered traffic lights");
		
		for(Entry<String, String> trafficLight : SmartParkManager.getRegisteredTrafficLights().entrySet()) {
			
			String trafficLightIPAddress = trafficLight.getKey();
			Set<WebLink> trafficLightResources = Utils.discoverResources(trafficLightIPAddress);
			
			for(WebLink trafficLightResource : trafficLightResources) {
				
				if(trafficLightResource.getAttributes().getResourceTypes().contains("alarmSystem")) {
					
					String baseServerURI = "coap://[" + trafficLightIPAddress + "]:5683";
					
					System.out.println("[DBG  - Alarm System] : Sending command: " + startFireAlarmJsonString);
					
					String resourcePath = baseServerURI + trafficLightResource.getURI();
					
					System.out.println("[INFO - Alarm System] : Sending PUT request to [" + trafficLightIPAddress + "] to start the alarm");
					System.out.println();
					
					CoapClient clientPutRequest = new CoapClient(resourcePath);
					CoapResponse response = clientPutRequest.put(startFireAlarmJsonString, MediaTypeRegistry.APPLICATION_JSON);
					
					System.out.println("[DBG  - Alarm System] : Response code is: " + response.getCode());
					System.out.println("[DBG  - Alarm System] : Response text is: " + response.getResponseText());
					System.out.println();
					
				}
			}
			
		}
		
	}

	private void sendFireAlarmToFlameDetectors(String startFireAlarmJsonString) {
		
		MqttMessage startAlarmMessage = new MqttMessage(startFireAlarmJsonString.getBytes());
		try {
			
			for(Entry<String, Boolean> flameDetector : SmartParkManager.getRegisteredFlameDetectors().entrySet()) {
				
				String flameDetectorID = flameDetector.getKey();
				
				System.out.println("[DBG  - Alarm System] : Publishing to topic: " + String.format(flameDetectorAlarmTopic, flameDetectorID));
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
		
		//TODO: make it stop.
		
		started = false;
		
		SmartParkManager.setFireDetected(false);
		
	}

}
