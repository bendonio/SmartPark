
#ifndef COAP_TRAFFIC_LIGHT_ALARM_H
#define COAP_TRAFFIC_LIGHT_ALARM_H

#include "contiki.h"

PROCESS_NAME(traffic_light_alarm_process);

#define ALARM_LED_BLINK_INTERVAL                        1*CLOCK_SECOND

#endif /* COAP_TRAFFIC_LIGHT_ALARM_H */