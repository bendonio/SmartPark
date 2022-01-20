#include "contiki.h"
#include "sys/etimer.h"
#include "dev/leds.h"
#include "./coap-traffic-light-alarm.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "CoAP Traffic Light Alarm"
#define LOG_LEVEL LOG_LEVEL_APP

PROCESS(traffic_light_alarm_process, "Traffic light alarm process");

PROCESS_THREAD(traffic_light_alarm_process, ev, data){

    static struct etimer alarm_led_blink_timer;
    static bool led_is_on;

    PROCESS_BEGIN();

    etimer_set(&alarm_led_blink_timer, ALARM_LED_BLINK_INTERVAL);

    while(1){

        PROCESS_YIELD();
        if(ev == PROCESS_EVENT_TIMER){
            if(etimer_expired(&alarm_led_blink_timer)){
                if(led_is_on) {
                    leds_off(LEDS_NUM_TO_MASK(LEDS_RED));
                    led_is_on = false;
                } else {
                    leds_on(LEDS_NUM_TO_MASK(LEDS_RED));
                    led_is_on = true;  
                }
                etimer_restart(&alarm_led_blink_timer);
            }
        } 

    }

    PROCESS_END();
}