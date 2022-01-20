package it.unipi.smartPark.coap;

import org.eclipse.californium.core.CoapServer;
import it.unipi.smartPark.SmartParkManager;

public class CoAPCollector extends CoapServer {
	
	private final SmartParkManager smartParkManager;

    public CoAPCollector(SmartParkManager smartParkManager) {
		super();
		
		this.smartParkManager = smartParkManager;
	}


	public void start() {

		add(new RegisteringParkLotsResource("registeredParkSensors", this));
		add(new RegisteringTrafficLightsResource("registeredTrafficLights", this));
		
		super.start();
	}



	public SmartParkManager getSmartParkManager() {
		
		return smartParkManager;
		
	}

    

}
