#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "../utils/utils.h"
#include "../alarm-processes/coap-traffic-light-alarm.h"

#include <string.h>

//#if PLATFORM_HAS_LEDS || LEDS_COUNT

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "CoAP Traffic Light Leds Resource"
#define LOG_LEVEL LOG_LEVEL_APP

static void res_put_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

/* A simple actuator example, depending on the color query parameter and post variable mode, corresponding led is activated or deactivated */
RESOURCE(res_traffic_light_leds,
         "title=\"Traffic Light actuator\";rt=\"trafficLightLeds\"",
         NULL,
         NULL,
         res_put_handler,
         NULL);

uint8_t led = LEDS_GREEN;
static uint8_t traffic_light_is_on = false;
bool alarm_started = false;

static void
res_put_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset){

  size_t len = 0;
  int success = 1;
  uint8_t led_to_toggle = 0;
  char json_response[512];

  if(traffic_light_is_on) {

    if(strcmp(TRAFFIC_LIGHT_COMMAND_RED, (char*)request->payload) == 0 ||
        strcmp(TRAFFIC_LIGHT_COMMAND_TURN_ON_RED, (char*)request->payload) == 0){
    // If the traffic light has been already turned on and the request is about switching the color

      LOG_DBG("Arrived command to switch color to RED: %s\n", (char*)request->payload);

      led = LEDS_RED;
      led_to_toggle = LEDS_GREEN;

    } else if(strcmp(TRAFFIC_LIGHT_COMMAND_GREEN, (char*)request->payload) == 0||
              strcmp(TRAFFIC_LIGHT_COMMAND_TURN_ON_GREEN, (char*)request->payload) == 0){
    // If the traffic light has been already turned on and the request is about switching the color

      LOG_DBG("Arrived command to switch color to GREEN: %s\n", (char*)request->payload);

      led = LEDS_GREEN;
      led_to_toggle = LEDS_RED;

    }  else {
      // If the traffic light has been already turned on and the request is not about switching the color

      success = 0;
    }

    if(success){

      leds_off(LEDS_NUM_TO_MASK(led_to_toggle));
      leds_on(LEDS_NUM_TO_MASK(led));
      sprintf(json_response,  "{\"message\": \"Traffic light %s\"}", 
              (led==LEDS_RED)? "RED" : "GREEN");
      

      len = sizeof(json_response) -1;
      
      memset(buffer, 0, len);

      memcpy(buffer, json_response, strlen(json_response));

      coap_set_status_code(response, CHANGED_2_04);
      coap_set_header_content_format(response, APPLICATION_JSON);
      coap_set_header_etag(response, (uint8_t *)&len, 1);
      coap_set_payload(response, buffer, strlen(json_response));
    }

  } else {

    if(strcmp(TRAFFIC_LIGHT_COMMAND_RED, (char*)request->payload) == 0 ||
        strcmp(TRAFFIC_LIGHT_COMMAND_GREEN, (char*)request->payload) == 0){
          // If the request is about switching the color but the traffic light is off
          LOG_DBG("Arrived command to switch color, but traffic light is off: %s\n", (char*)request->payload);
          
          memset(json_response, 0, 512);

          sprintf(json_response,  "{\"message\": \"Traffic light is OFF\"}");

          len = sizeof(json_response) -1;
          
          memset(buffer, 0, len);

          memcpy(buffer, json_response, strlen(json_response));

          coap_set_status_code(response, FORBIDDEN_4_03);
          coap_set_header_content_format(response, APPLICATION_JSON);
          coap_set_header_etag(response, (uint8_t *)&len, 1);
          coap_set_payload(response, buffer, strlen(json_response));
          
    } else if(strcmp(TRAFFIC_LIGHT_COMMAND_TURN_ON_GREEN, (char*)request->payload) == 0
              || strcmp(TRAFFIC_LIGHT_COMMAND_TURN_ON_RED, (char*)request->payload) == 0){
        // If the request is about turning on the traffic light


        memset(json_response, 0, 512);
        
        if(strcmp(TRAFFIC_LIGHT_COMMAND_TURN_ON_GREEN, (char*)request->payload) == 0){
          led = LEDS_GREEN;
          sprintf(json_response,  "{\"message\": \"Traffic light is ON GREEN\"}");
          LOG_DBG("Arrived message to turn on traffic light leds, GREEN: %s\n",  (char*)request->payload);
        } else {
          led = LEDS_RED;
          sprintf(json_response,  "{\"message\": \"Traffic light is ON RED\"}");
          LOG_DBG("Arrived message to turn on traffic light leds, RED: %s\n",  (char*)request->payload);
        }
        
        traffic_light_is_on = true;
        leds_on(LEDS_NUM_TO_MASK(led));

        len = sizeof(json_response) -1;
        
        memset(buffer, 0, len);

        memcpy(buffer, json_response, strlen(json_response));

        coap_set_status_code(response, CHANGED_2_04);
        coap_set_header_content_format(response, APPLICATION_JSON);
        coap_set_header_etag(response, (uint8_t *)&len, 1);
        coap_set_payload(response, buffer, strlen(json_response));

    } else {

      success = 0;
    }

  }
  if(!success){
    coap_set_status_code(response, BAD_REQUEST_4_00);
  }
  
}

//#endif /* PLATFORM_HAS_LEDS */
