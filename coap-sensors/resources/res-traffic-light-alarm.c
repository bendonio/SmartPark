#include "contiki.h"
#include "coap-engine.h"
#include "dev/leds.h"
#include "../utils/utils.h"
#include "../coap-traffic-light-server.h"

#include <string.h>

// #if PLATFORM_HAS_LEDS || LEDS_COUNT

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "CoAP Traffic Light Alarm Resource"
#define LOG_LEVEL LOG_LEVEL_APP

static void res_put_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);

/* A simple actuator example, depending on the color query parameter and post variable mode, corresponding led is activated or deactivated */
RESOURCE(res_traffic_light_alarm_system,
         "title=\"Traffic Light Alarm System\";rt=\"alarmSystem\"",
         NULL,
         NULL,
         res_put_handler,
         NULL);

static void
res_put_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset){

    size_t len = 0;
    char json_response[512];

    LOG_DBG("Received message: %s\n",  (char*)request->payload);

    if(strcmp(COMMAND_START_ALARM, (char*)request->payload) == 0){
        
        LOG_DBG("Sending TRAFFIC_LIGHT_FIRE_ALARM_START_EVENT to coap_traffic_light_server process!\n");
        process_post(&coap_traffic_light_server, TRAFFIC_LIGHT_FIRE_ALARM_START_EVENT, NULL);

        memset(json_response, 0, 512);
        
        leds_off(LEDS_NUM_TO_MASK(LEDS_GREEN) | LEDS_NUM_TO_MASK(LEDS_RED));
        sprintf(json_response,  "{\"message\": \"Alarm started!\"}");

        len = sizeof(json_response) -1;
        
        memset(buffer, 0, len);

        memcpy(buffer, json_response, strlen(json_response));

        coap_set_status_code(response, CHANGED_2_04);
        coap_set_header_content_format(response, APPLICATION_JSON);
        coap_set_header_etag(response, (uint8_t *)&len, 1);
        coap_set_payload(response, buffer, strlen(json_response));
    }

}