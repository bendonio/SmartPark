package it.unipi.smartPark.mqtt;

import java.util.Map;

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import it.unipi.smartPark.FireAlarmSystem;
import it.unipi.smartPark.SmartParkManager;
import it.unipi.smartPark.Utils;

public class MQTTCollector implements MqttCallback{
	
	private final SmartParkManager smartParkManager;
	
	private static MqttClient mqttClient ;
	
	private final static String brokerURI = "tcp://[fd00::1]:1883";
	private final static String mqttClientID = "MQTTCollector";
	
	private static final String FLAME_DETECTOR_REGISTRATION_TOPIC = "flame-detector-registration";
	private static final String FLAME_DETECTOR_UPDATES_TOPIC = "flame-detector-%s/flame-detected-updates"; // Topic to subscribe to receive updates from flame detectors
	private static final String FLAME_DETECTOR_ALARM_STOP_TOPIC = "flame-detector-%s/alarm-stop";
	
	private static final int INITIAL_VALUE_FLAME_DETECTED_COUNTER = 5;
	private static int flameDetectedCounter = INITIAL_VALUE_FLAME_DETECTED_COUNTER; // Decreased each time a flame detected message is received, when 0 start the alarm. 
	
	
	public static MqttClient getMqttClient() {
		return mqttClient;
	}

	public MQTTCollector(SmartParkManager smartParkManager) {
		
		this.smartParkManager = smartParkManager;
		
		mqttClient = null;
		try {
			
			mqttClient = new MqttClient(brokerURI, mqttClientID);
			
			mqttClient.setCallback(this);
			
			mqttClient.connect();
			
		} catch (MqttException e) {
			System.out.println("[ERR  - MQTT Collector] : Error creating the MqttClient");
			e.printStackTrace();
		}
		
		subscribeToTopic(FLAME_DETECTOR_REGISTRATION_TOPIC);
		
	}

	@Override
	public void connectionLost(Throwable cause) {
		// TODO Auto-generated method stub

	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		
		String payloadString = new String(message.getPayload());
		System.out.println(String.format("[DBG  - MQTT Collector] : Message arrived: [%s] %s, id: %d", topic, payloadString, message.getId()));
		
		String flameDetectorID = null;
		
		if(topic.equals(FLAME_DETECTOR_REGISTRATION_TOPIC)) {
			flameDetectorID = handleFlameDetectorRegistration(payloadString);
			
			if(flameDetectorID != null) {
				
				subscribeToTopic(String.format(FLAME_DETECTOR_UPDATES_TOPIC, flameDetectorID));
				subscribeToTopic(String.format(FLAME_DETECTOR_ALARM_STOP_TOPIC, flameDetectorID));
				
			}
		} else {
			
			flameDetectorID = topic.split("/")[0].split("-")[2];
			System.out.println("[DBG  - MQTT Collector] : Flame Detector ID: " + flameDetectorID);
			
			if(SmartParkManager.getRegisteredFlameDetectors().keySet().contains(flameDetectorID)) {
				
				if(topic.equals(String.format(FLAME_DETECTOR_UPDATES_TOPIC, flameDetectorID))){
					
						System.out.println("[INFO - MQTT Collector] : Received update!");
						System.out.println();
						
						//If this is an update from a flame detector, handle it only if no fire is still detected.
						if(!SmartParkManager.getFireDetected())
							handleFlameDetectedUpdate(payloadString);
						else
							System.out.println("[INFO - MQTT Collector]: Fire already detected.");
						
				} else if(topic.equals(String.format(FLAME_DETECTOR_ALARM_STOP_TOPIC, flameDetectorID))) {
					// If this is an ALARM STOP command
					
					System.out.println("[INFO - MQTT Collector]: Received ALARM STOP command!");
					System.out.println();
					
					handleAlarmStopCommand(payloadString);
					
					//SmartParkManager.getRegisteredFlameDetectors().put(flameDetectorID, false);
					
				}
			} else {
				
				System.out.println("[INFO - MQTT Collector]: Received update from client not subscribed! Do nothing");
				System.out.println();
			}
			
		}
	}

	private void handleAlarmStopCommand(String alarmStopCommand) {
		
		System.out.println("[INFO - MQTT Collector] : Received flame detector ALARM STOP command: " + alarmStopCommand);
		
		Map<String, Object> alarmStopCommandJsonObject = Utils.jsonParser(alarmStopCommand);
		
        if(alarmStopCommandJsonObject == null){
        	
			System.out.println("[ERR  - MQTT Collector]: Malformed ALARM STOP command!");
			System.out.println();
			return;
        }
        
        if(alarmStopCommandJsonObject.containsKey("alarm")) {
        	
        	Boolean stopAlarm = !((Boolean) alarmStopCommandJsonObject.get("alarm"));
        	if(stopAlarm) {
        		System.out.println("[DBG  - MQTT Collector] : STOP ALARM!");
        		SmartParkManager.stopAlarmSystem();
        	}
        	
        }
		
	}

	private void handleFlameDetectedUpdate(String updateString) {
		
		System.out.println("[INFO - MQTT Collector] : Received flame detector update: " + updateString);
		
		Map<String, Object> updateJsonObject = Utils.jsonParser(updateString);
		
        if(updateJsonObject == null){
        	
			System.out.println("[ERR  - MQTT Collector]: Malformed update!");
			System.out.println();
			return;
        }
        
        if(updateJsonObject.containsKey("flameDetected")) {
        	
        	Boolean flameDetected = (Boolean) updateJsonObject.get("flameDetected");
        	if(flameDetected) {
        		System.out.println("[DBG  - MQTT Collector] : FLAME DETECTED!");
        		flameDetectedCounter--;
        		if(flameDetectedCounter == 0) {
        			// If the counter reached 0 --> fire detected --> start the alarm
        			
        			SmartParkManager.setFireDetected(true);
        			
        			SmartParkManager.startAlarmSystem();
        			
        		}
        		
        	} else {
        		flameDetectedCounter = INITIAL_VALUE_FLAME_DETECTED_COUNTER;
        	}
        }
        
        System.out.println();
		
	}

	private void subscribeToTopic(String topic) {
		
		try {
			
			System.out.println("[INFO - MQTT Collector] : Subscribing to topic " + topic);
			mqttClient.subscribe(topic);
			System.out.println("[INFO - MQTT Collector] : Subscribed to topic " + topic);
			
			System.out.println("");
			
		} catch (MqttException e) {
			System.out.println("[ERR  - MQTT Collector]: Error subscribing to topic:" + topic);
			e.printStackTrace();
		}
		
	}

	private String handleFlameDetectorRegistration(String registrationString) {
		
		System.out.println("[INFO - MQTT Collector] : Received request to subscribe: " + registrationString);
		
		Map<String, Object> registrationJsonObject = Utils.jsonParser(registrationString);
		
        if(registrationJsonObject == null){
        	
			System.out.println("[ERR  - MQTT Collector]: Registration failed, BAD REQUEST!");
			return null;
        }
        
        if(registrationJsonObject.containsKey("flameDetectorID")) {
        	// Insert the flame detector among the registered flame detectors
        	
        	String flameDetectorID = (String) registrationJsonObject.get("flameDetectorID");
        	SmartParkManager.getRegisteredFlameDetectors().put(flameDetectorID, false);
        	
        	System.out.println("[INFO - MQTT Collector] : Added flameDetectorID" + ": " + flameDetectorID + " to the list of registered Flame Detectors");
        	System.out.println();
        	
        	return flameDetectorID;
        }
        
        return null;
		
	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub

	}

}
