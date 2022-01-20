#include "contiki.h"

PROCESS_NAME(coap_park_server);

extern process_event_t PARK_SERVER_FIRE_ALARM_START_EVENT;

#define PARKING_TIME_LOWER_BOUND                    3
#define PARKING_TIME_UPPER_BOUND                    30