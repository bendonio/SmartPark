#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "os/net/ipv6/uip-ds6.h"
#include "os/net/ipv6/uiplib.h"
#include "sys/log.h"
#include "../utils/utils.h"

#include <time.h>
#include <stdbool.h>
#include <string.h>

#define LOG_MODULE "CoAP Park Slot Resource"
#define LOG_LEVEL LOG_LEVEL_APP

static void res_get_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

static void res_event_handler(void);

EVENT_RESOURCE(res_presence_sensor,
         "title=\"Presence sensor\";obs",
         res_get_handler,
         NULL,
         NULL,
         NULL,
         res_event_handler);

static bool park_slot_occupied = false;

static void create_json_observable_message(char *out_buf, size_t size_out_buf){

  /* char park_slot_id[46]; // 46 is the max length of an IPv6 address (45+ 1 trailing null )
  uiplib_ipaddr_snprint(park_slot_id, 46, &uip_ds6_get_global(ADDR_PREFERRED)->ipaddr); */

  memset(out_buf, 0, size_out_buf);

  if(park_slot_occupied)
    sprintf(out_buf,  "{\"timestamp\":%lu, \"occupied\": true}", (unsigned long)time(NULL) );
  else
    sprintf(out_buf,  "{\"timestamp\": %lu, \"occupied\": false}", (unsigned long)time(NULL));
}

static void res_event_handler(void){

  coap_notify_observers(&res_presence_sensor);

}

static void res_get_handler(coap_message_t *request, coap_message_t *response, 
                            uint8_t *buffer, uint16_t preferred_size, int32_t *offset){

  char json_observable_message[512]; 
  int len;
  create_json_observable_message(json_observable_message, 512);

  len = sizeof(json_observable_message) -1;
  
  memset(buffer, 0, len);

  memcpy(buffer, json_observable_message, strlen(json_observable_message));

  coap_set_header_content_format(response, APPLICATION_JSON);
  coap_set_header_etag(response, (uint8_t *)&len, 1);
  //coap_set_header_block2(response, 0, 0, 512);
  coap_set_payload(response, buffer, strlen(json_observable_message));//snprintf((char *)buffer, preferred_size, json_observable_message));
                                      //snprintf returns the number of characters that would have been written 
                                      //if preferred_size had been sufficiently large, not counting the terminating null character.
  
}

bool isOccupied(){
  return park_slot_occupied;
}

void car_arrived_left(){

  park_slot_occupied = (park_slot_occupied == true) ? false : true;
  leds_on(LEDS_NUM_TO_MASK((park_slot_occupied == true) ? LEDS_RED : LEDS_GREEN));  // I switch red/green the sensor led to better visualize 
  leds_off(LEDS_NUM_TO_MASK((park_slot_occupied == true) ? LEDS_GREEN : LEDS_RED)); // when the park slots are occupied/free

  LOG_INFO("Park slot %s.\n", (park_slot_occupied == true) ? "occupied" : "free");

  res_presence_sensor.trigger();

}
