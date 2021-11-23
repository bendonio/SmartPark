package it.unipi.smartPark.coap;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;

import it.unipi.smartPark.SmartParkManager;

public class CoAPCollector extends CoapServer {
	
	private final SmartParkManager smartParkManager;

    public CoAPCollector(SmartParkManager smartParkManager) {
		super();
		
		this.smartParkManager = smartParkManager;
	}


	public void start() {

		addEndpoint(new CoapEndpoint(new InetSocketAddress("fd00::1", 5683)));
		add(new RegisteringParkSlotsResource("registeredParkSensors", this));
		add(new RegisteringTrafficLightsResource("registeredTrafficLights", this));
		
		super.start();
	}



	public SmartParkManager getSmartParkManager() {
		
		return smartParkManager;
		
	}

    

}
