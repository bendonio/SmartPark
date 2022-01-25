#include "contiki.h"
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "os/net/ipv6/uip-ds6.h"
#include "os/net/ipv6/uiplib.h"
#include "net/ipv6/uip-debug.h"
#include "coap-engine.h"
#include "coap-blocking-api.h"
#include "sys/etimer.h"
#include "dev/button-hal.h"
#include "dev/leds.h"
#include "./resources/res-vehicle-detection-sensor.h"
#include "./utils/utils.h"
#include "./coap-park-lot-server.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <execinfo.h>
#include <unistd.h>

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "CoAP Park Server"
#define LOG_LEVEL LOG_LEVEL_APP

process_event_t PARK_SERVER_FIRE_ALARM_START_EVENT;

/* #define PARKING_TIME_LOWER_BOUND                    3
#define PARKING_TIME_UPPER_BOUND                    30 */

// interval with which to check if the server is connected to the BR
#define CHECK_NETWORK_TIMER_INTERVAL                1*CLOCK_SECOND

static struct etimer next_car_event_timer;
static struct etimer check_network_timer;
static button_hal_button_t *reset_btn;
static bool registered = false;
static bool registration_sent = false;
static coap_endpoint_t collector_ep;
static coap_message_t registering_request;
extern coap_resource_t res_presence_sensor;
extern coap_resource_t res_alarm_system;
static int next_car_leaving_arriving;

#define BT_BUF_SIZE 100

/**
 * Callback function called when receiving the response 
 * from the collector after the server registration
 *
 * @param response response from the collector
 * 
 */
void registering_to_collector_response_handler(coap_message_t *response){

  if(response == NULL) {

    LOG_INFO("Request for registration to collector timed out\n");
    return;
  }

  if(response->code == FORBIDDEN_4_03){
    // Enters here if park server tries to register while alarm system is on
    LOG_INFO("Forbidden operation!\n");
    LOG_INFO("%s\n", (char*) response->payload);
    
    return;

  } 

  if(response->code != CREATED_2_01){ // CREATED_2_01 = 65 

    LOG_INFO("Error during registration to collector\n");

    return;
  } 

  LOG_INFO("Registration to collector was successful\n");

  registered = true;

  if(isOccupied()){
    leds_on(LEDS_NUM_TO_MASK(LEDS_RED));
  } else{
    leds_on(LEDS_NUM_TO_MASK(LEDS_GREEN));
  }

  next_car_leaving_arriving = CLOCK_SECOND*get_random_value(PARKING_TIME_LOWER_BOUND, PARKING_TIME_UPPER_BOUND);

  etimer_set(&next_car_event_timer, next_car_leaving_arriving);

  return;

}

/**
 * \brief                  Tells if the node is reachable from outside the WSN
 */
bool have_connectivity(){
  
  if(NETSTACK_ROUTING.node_is_reachable() &&  uip_ds6_get_global(ADDR_PREFERRED)){
    
    return true;
  }
  else{
      
    return false;
  }
  
}

int get_random_value(int min, int max){

  return (rand() % (max - min + 1)) + min;

}

/**
 * Function that activates the presence sensor resource
 */

void activate_resources(void){

  LOG_INFO("Activating the presence sensor resource\n");

  coap_activate_resource(&res_presence_sensor, "lotState");

  LOG_INFO("Activating the alarm system resource\n");

  coap_activate_resource(&res_alarm_system, "alarmSystem");

}

/* Declare and auto-start this file's process */
PROCESS(coap_park_server, "CoAP Park Server");
AUTOSTART_PROCESSES(&coap_park_server);

/*---------------------------------------------------------------------------*/
PROCESS_THREAD(coap_park_server, ev, data)
{
  PROCESS_BEGIN();

  static char payload[512];
  static char ipv6_addr[46]; // 46 is the max length of an IPv6 address (45+ 1 trailing null )

  reset_btn = button_hal_get_by_index(0);

  PROCESS_PAUSE();

  LOG_INFO("Contiki-NG Server started\n");

  activate_resources();
  /*
  // It will be in seconds only to make it faster
  int next_car_leaving_arriving = CLOCK_SECOND*get_random_value(PARKING_TIME_LOWER_BOUND, PARKING_TIME_UPPER_BOUND);

  etimer_set(&next_car_event_timer, next_car_leaving_arriving);*/

  // Check if the node is connected to the BR and if it has an IPv6 global address
  if(!have_connectivity()){

    LOG_INFO("Not reachable yet!\n");
    etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);

  }

  while(1){
 
    PROCESS_WAIT_EVENT();

    if(ev == PROCESS_EVENT_TIMER){

      if(etimer_expired(&check_network_timer)){
        
        // Periodically check if connected to the BR and if it has an IPv6 global address
        if(!have_connectivity()){
          //If not yet connected to BR and not yet have a global address

          LOG_INFO("Not reachable yet!\n");
          etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);

        } else{

          if(!registered || !registration_sent){
            // If connected to BR but not yet registered to the collector
            memset(ipv6_addr, 0, 46);
            uiplib_ipaddr_snprint(ipv6_addr, 46, &uip_ds6_get_global(ADDR_PREFERRED)->ipaddr);

            memset(payload, 0, 512);
            if(isOccupied())
              sprintf(payload, "{\"parkLotID\": \"%s\", \"occupied\": true}", ipv6_addr);
            else
              sprintf(payload, "{\"parkLotID\": \"%s\", \"occupied\": false}", ipv6_addr);
            
            if(!registration_sent)
              setup_registration_to_collector(&registering_request, 
                                            &collector_ep, 
                                            COLLECTOR_REGISTERED_PARK_SENSORS_RESOURCE, 
                                            payload
                                            );

            LOG_INFO("Sending registration request: %s!\n", payload);
            // Issue the request in a blocking manner
            // The client will wait for the server to reply (or the transmission to timeout)
            COAP_BLOCKING_REQUEST(&collector_ep, &registering_request, registering_to_collector_response_handler);

            registration_sent = true;
            
          }
        }

      }

      if(etimer_expired(&next_car_event_timer)){
        // next_car_event_timer expired

        if(registered)
          car_arrived_left();

        next_car_leaving_arriving = CLOCK_SECOND*get_random_value(PARKING_TIME_LOWER_BOUND, PARKING_TIME_UPPER_BOUND);
        
        etimer_set(&next_car_event_timer, next_car_leaving_arriving);
      }
		  
			
	  } else if (ev == PARK_SERVER_FIRE_ALARM_START_EVENT){

      LOG_DBG("Alarm start event arrived!\n");

      // Stop making cars arriving leaving and turn off the leds.  
      etimer_stop(&next_car_event_timer);
      leds_off(LEDS_NUM_TO_MASK(LEDS_GREEN) | LEDS_NUM_TO_MASK(LEDS_RED));

      registered = false;
      registration_sent = false;

    } else if(ev == button_hal_periodic_event) {

        reset_btn = (button_hal_button_t *) data;
        LOG_DBG("Periodic event, %u seconds \n", reset_btn->press_duration_seconds);

        if(reset_btn->press_duration_seconds > 5) {

          LOG_DBG("Button pressed for more than 5 secs: Device restarted\n");
          
          next_car_leaving_arriving = CLOCK_SECOND*get_random_value(PARKING_TIME_LOWER_BOUND, PARKING_TIME_UPPER_BOUND);
        
          etimer_set(&next_car_event_timer, next_car_leaving_arriving);
          etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);

        }   
    }
  }

  PROCESS_END();
}
