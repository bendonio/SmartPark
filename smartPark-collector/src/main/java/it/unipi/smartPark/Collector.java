package it.unipi.smartPark;

import it.unipi.smartPark.coap.CoAPCollector;
import it.unipi.smartPark.mqtt.MQTTCollector;

public class Collector {

	public static void main(String[] args) {
		
		int totalParkSlots = 2;
		
		if(args.length > 0 && Integer.parseInt(args[0]) > 0) {
			totalParkSlots = Integer.parseInt(args[0]);
			
			System.out.println("Passed argument is: " + totalParkSlots);
		}
		
		final SmartParkManager smartParkManager = new SmartParkManager(totalParkSlots);
		MQTTCollector mqttCollector = new MQTTCollector(smartParkManager);
		CoAPCollector coapCollector = new CoAPCollector(smartParkManager);
		coapCollector.start();

	}

}
