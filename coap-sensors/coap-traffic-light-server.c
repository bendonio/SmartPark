#include "contiki.h"
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "coap-engine.h"
#include "coap-blocking-api.h"
#include "os/net/ipv6/uip-ds6.h"
#include "os/net/ipv6/uiplib.h"
#include "sys/etimer.h"
#include "./utils/utils.h"
#include "./alarm/coap-traffic-light-alarm.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "CoAP Traffic Light Server"
#define LOG_LEVEL LOG_LEVEL_APP

process_event_t TRAFFIC_LIGHT_FIRE_ALARM_START_EVENT;

#define CHECK_NETWORK_TIMER_INTERVAL                1*CLOCK_SECOND

static struct etimer check_network_timer;
static bool registered = false;
static bool registration_sent = false;
static coap_endpoint_t collector_ep;
static coap_message_t registering_request[1];
extern coap_resource_t res_traffic_light_leds;
extern coap_resource_t res_traffic_light_alarm_system;
/**
 * Callback function called when receiving the response 
 * from the collector after the server registration
 *
 * @param response response from the collector
 * 
 */
void registering_to_collector_response_handler(coap_message_t *response){

  if(response == NULL) {

    LOG_INFO("Request for registration timed out\n");
    etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);
    return;
  } 

  if(response->code != CREATED_2_01){ // CREATED_2_01 = 65 

    LOG_INFO("Error during registration\n");
    etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);
    return;
  } 

  LOG_INFO("Registration was successful\n");

  registered = true;

  return;

}

/**
 * Function that activates the traffic light actuator resource
 */

void activate_resources(void){

  LOG_INFO("Activating the traffic light actuator resource\n");

  coap_activate_resource(&res_traffic_light_leds, "trafficLight");

  LOG_INFO("Activating the alarm system resource\n");

  coap_activate_resource(&res_traffic_light_alarm_system, "alarmSystem");

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

/* Declare and auto-start this file's process */
PROCESS(coap_traffic_light_server, "Contiki-NG CoAP Server");
AUTOSTART_PROCESSES(&coap_traffic_light_server);


/*---------------------------------------------------------------------------*/
PROCESS_THREAD(coap_traffic_light_server, ev, data){
  PROCESS_BEGIN();

  static char payload[512];
  char traffic_light_id[46]; // 46 is the max length of an IPv6 address (45+ 1 trailing null )
  
  activate_resources();

  // Check if the node is connected to the BR and if it has an IPv6 global address
  if(!have_connectivity()){

    LOG_INFO("Not reachable yet!\n");
    etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);

  }

  while(1){
 
    PROCESS_WAIT_EVENT();

    if(ev == PROCESS_EVENT_TIMER){

      if(etimer_expired(&check_network_timer)){
        
        if(!have_connectivity()){
          //If not yet connected to BR and not yet have a global address

          LOG_INFO("Not reachable yet!\n");
          etimer_set(&check_network_timer, CHECK_NETWORK_TIMER_INTERVAL);
       
        } else {

            memset(traffic_light_id, 0, 46);
            uiplib_ipaddr_snprint(traffic_light_id, 46, &uip_ds6_get_global(ADDR_PREFERRED)->ipaddr);

            LOG_INFO("Resource activated at: coap://[%s]/trafficLight\n", traffic_light_id);

            if(!registered || !registration_sent){
              // If connected to BR but not yet registered to the collector
              memset(payload, 0, 512);
              sprintf(payload, "{\"trafficLightID\": \"%s\"}", traffic_light_id);
            
              setup_registration_to_collector(registering_request, 
                                              &collector_ep, 
                                              COLLECTOR_REGISTERED_TRAFFIC_LIGHT_RESOURCE, 
                                              payload);

              LOG_INFO("Sending registration request: %s!\n", payload);
              // Issue the request in a blocking manner
              // The client will wait for the server to reply (or the transmission to timeout)
              COAP_BLOCKING_REQUEST(&collector_ep, registering_request, registering_to_collector_response_handler);

              registration_sent = true;

          }
        }
        

      }
    } else if(ev == TRAFFIC_LIGHT_FIRE_ALARM_START_EVENT){

        LOG_DBG("Alarm start event arrived!\n");

        process_start(&traffic_light_alarm_process, NULL);
    }
  }
  
  PROCESS_END();
}
