#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include "contiki.h"
#include "net/routing/routing.h"
#include "net/netstack.h"
#include "os/net/ipv6/uip-ds6.h"
#include "os/net/ipv6/uiplib.h"
#include "net/ipv6/uip-debug.h"
#include "coap-engine.h"
#include "coap-blocking-api.h"
#include "./utils.h"
#include "sys/log.h"

#define LOG_MODULE "App"
#define LOG_LEVEL LOG_LEVEL_APP

bool is_reachable(){
  
  if(NETSTACK_ROUTING.node_is_reachable() &&  uip_ds6_get_global(ADDR_PREFERRED)){
    return true;
  }
  else{
      
    return false;
  }
  
}

void setup_registration_to_collector(coap_message_t* registering_request, coap_endpoint_t* collector_ep, 
                                      char* resource_path, char* payload){

  // Populate the coap_endpoint_t data structure
  coap_endpoint_parse(COLLECTOR_EP, strlen(COLLECTOR_EP), collector_ep);

  // Prepare the message
  coap_init_message(registering_request, COAP_TYPE_CON, COAP_POST, 0);
  coap_set_header_uri_path(registering_request, resource_path);

  coap_set_payload(registering_request, (uint8_t*) payload, strlen(payload));

}