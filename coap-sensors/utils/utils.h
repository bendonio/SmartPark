#include "sys/etimer.h"
#include <stdbool.h>

/* Constants */
#define COLLECTOR_EP                                    "coap://[fd00::1]:5683"
#define COLLECTOR_REGISTERED_PARK_SENSORS_RESOURCE      "/registeredParkSensors"
#define COLLECTOR_REGISTERED_TRAFFIC_LIGHT_RESOURCE     "/registeredTrafficLights"

#define TRAFFIC_LIGHT_COMMAND_RED	                    "{\"color\":\"r\"}"
#define TRAFFIC_LIGHT_COMMAND_GREEN	                    "{\"color\":\"g\"}"
#define TRAFFIC_LIGHT_COMMAND_TURN_ON_GREEN	            "{\"mode\":\"on\", \"color\":\"g\"}"
#define TRAFFIC_LIGHT_COMMAND_TURN_ON_RED               "{\"mode\":\"on\", \"color\":\"r\"}"

#define COMMAND_START_ALARM	                            "{\"alarm\": true}"


/**
 * \brief                  Initializes required data structures to register to collector
 */
void setup_registration_to_collector(coap_message_t*, coap_endpoint_t*, char*, char*);

int get_random_value(int, int);

void registering_to_collector_response_handler(coap_message_t *);


