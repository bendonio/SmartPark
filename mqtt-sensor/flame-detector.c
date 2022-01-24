#include "contiki.h"
#include "net/routing/routing.h"
#include "mqtt.h"
#include "net/ipv6/uip.h"
#include "net/ipv6/uip-icmp6.h"
#include "net/ipv6/sicslowpan.h"
#include "sys/etimer.h"
#include "sys/ctimer.h"
#include "lib/sensors.h"
#include "dev/button-hal.h"
#include "dev/leds.h"
#include "os/sys/log.h"
#include "flame-detector.h"
#include "./utils/utils.h"

#include <sys/time.h>
#include <string.h>
#include <strings.h>
#include <stdbool.h>
#include <stdio.h>
/*---------------------------------------------------------------------------*/
#define LOG_MODULE "MQTT Flame Detector"
#define LOG_LEVEL LOG_LEVEL_DBG

/*---------------------------------------------------------------------------*/
static char *broker_ip = MQTT_CLIENT_BROKER_IP_ADDR;

static int no_flame_detected_publish_counter = NO_FLAME_DETECTED_PUBLISH_INTERVAL -1;
static int flame_detected_publish_counter = FLAME_DETECTED_PUBLISH_INTERVAL -1;
static bool flame_detected = false;
static bool restarted = false;
static int seconds_to_alarm = 45; // counter after which trigger the alarm
// We assume that the broker does not require authentication

/*---------------------------------------------------------------------------*/
PROCESS_NAME(mqtt_flame_detector_process);
AUTOSTART_PROCESSES(&mqtt_flame_detector_process);


static char flame_detector_id[MAX_LEN_IPV6_ADDR];
static char IPv6addr[MAX_LEN_IPV6_ADDR];

/*---------------------------------------------------------------------------*/
/* Buffers for topics */

static char flame_detection_pub_topic[BUFFER_SIZE];
static char registration_pub_topic[BUFFER_SIZE];
static char alarm_stop_pub_topic[BUFFER_SIZE];
static char alarm_start_sub_topic[BUFFER_SIZE];
/*---------------------------------------------------------------------------*/

static struct etimer flame_detector_state_check_timer;
static button_hal_button_t *reset_btn; //Button for resetting the devide after fire is over.

/*---------------------------------------------------------------------------*/
/* The main MQTT buffers */

static char registration_buffer[APP_BUFFER_SIZE];
static char updates_buffer[APP_BUFFER_SIZE];
static char alarm_stop_buffer[APP_BUFFER_SIZE];
/*---------------------------------------------------------------------------*/
static struct mqtt_message *msg_ptr = 0;

static struct mqtt_connection conn;

/*---------------------------------------------------------------------------*/
PROCESS(mqtt_flame_detector_process, "MQTT Client");

/*---------------------------------------------------------------------------*/
static void
pub_handler(const char *topic, uint16_t topic_len, const uint8_t *chunk,
            uint16_t chunk_len){
    printf("Pub Handler: topic='%s' (len=%u), chunk_len=%u\n", topic,
          topic_len, chunk_len);

    char correct_topic[BUFFER_SIZE];
    sprintf(correct_topic, ALARM_START_SUB_TOPIC, IPv6addr);

    if(strcmp(topic, correct_topic) == 0) {
        LOG_INFO("Received ALARM message\n");

        if(strcmp(FLAME_DETECTOR_ALARM_START, (char*)chunk) == 0){
        LOG_INFO("Starting alarm\n");

        state = STATE_ALARM_STARTED;
        }

        return;
    }
}
/*---------------------------------------------------------------------------*/
static void
mqtt_event(struct mqtt_connection *m, mqtt_event_t event, void *data){
  switch(event) {
    case MQTT_EVENT_CONNECTED: {
        LOG_INFO("Application has a MQTT connection\n");

        state = STATE_CONNECTED;
        break;
    }
    case MQTT_EVENT_DISCONNECTED: {
        LOG_ERR("MQTT Disconnect. Reason %u\n", *((mqtt_event_t *)data));

        if(state != STATE_ALARM_STARTED){
        state = STATE_DISCONNECTED;
        process_poll(&mqtt_flame_detector_process);
        }
        break;
    }
    case MQTT_EVENT_PUBLISH: {
        msg_ptr = data;

        pub_handler(msg_ptr->topic, strlen(msg_ptr->topic),
                    msg_ptr->payload_chunk, msg_ptr->payload_length);
        break;
    }
    case MQTT_EVENT_SUBACK: {
    #if MQTT_311
        mqtt_suback_event_t *suback_event = (mqtt_suback_event_t *)data;

        if(suback_event->success) {
        printf("Application is subscribed to topic successfully\n");
        state = STATE_SUBSCRIBED;
        } else {
        printf("Application failed to subscribe to topic (ret code %x)\n", suback_event->return_code);
        }
    #else
        printf("Application is subscribed to topic successfully\n");
        state = STATE_SUBSCRIBED;
    #endif
        break;
    }
    case MQTT_EVENT_UNSUBACK: {
        printf("Application is unsubscribed to topic successfully\n");
        break;
    }
    case MQTT_EVENT_PUBACK: {
        printf("Publishing complete.\n");
        break;
    }
    default:
        printf("Application got a unhandled MQTT event: %i\n", event);
        break;
    }
}

static bool have_connectivity(void){
    if(uip_ds6_get_global(ADDR_PREFERRED) == NULL ||
        uip_ds6_defrt_choose() == NULL) {
        return false;
    }
    
    return true;
}

// Commented for the purpose of the presentation
/*static int get_random_value(int min, int max){

  return (rand() % (max - min + 1)) + min;

}*/

void check_for_alarm(){

    if(seconds_to_alarm == 0 && !flame_detected){
        flame_detected = true;
        } else{
            if(seconds_to_alarm > 0)
                seconds_to_alarm--;
        }
}

mqtt_status_t publish(char* topic, char* message_buffer){

  return mqtt_publish(&conn, NULL, topic, (uint8_t *)message_buffer,
                      strlen(message_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
}


/*---------------------------------------------------------------------------*/
PROCESS_THREAD(mqtt_flame_detector_process, ev, data){

    PROCESS_BEGIN();

    reset_btn = button_hal_get_by_index(0);

    mqtt_status_t status;
    //char broker_address[CONFIG_IP_ADDR_STR_LEN];

    static bool led_is_on = false;

    LOG_INFO("MQTT Flame Detector Process\n");

    // Initialize the ClientID as MAC address
    snprintf(flame_detector_id, BUFFER_SIZE, "%02x%02x%02x%02x%02x%02x",
                     linkaddr_node_addr.u8[0], linkaddr_node_addr.u8[1],
                     linkaddr_node_addr.u8[2], linkaddr_node_addr.u8[5],
                     linkaddr_node_addr.u8[6], linkaddr_node_addr.u8[7]);

    // Broker registration                                         
    mqtt_register(&conn, &mqtt_flame_detector_process, flame_detector_id, mqtt_event,
                    MAX_TCP_SEGMENT_SIZE);

    state=STATE_INIT;

    // Initialize periodic timer to check the status 
    etimer_set(&flame_detector_state_check_timer, FLAME_DETECTOR_STATE_CHECK_INTERVAL);

    /* Main loop */
    while(1) {

        PROCESS_YIELD();

        if((ev == PROCESS_EVENT_TIMER && data == &flame_detector_state_check_timer) ||
            ev == PROCESS_EVENT_POLL){

            if(state == STATE_INIT){
                if(have_connectivity()==true)
                    state = STATE_NET_OK;
            }

            if(state == STATE_NET_OK){

                if(!mqtt_connected(&conn)){
                    uiplib_ipaddr_snprint(IPv6addr,
                                        MAX_LEN_IPV6_ADDR, // 46 is the max length of an IPv6 address (45+ 1 trailing null ) 
                                        &uip_ds6_get_global(ADDR_PREFERRED)->ipaddr);

                    // Connect to MQTT server
                    LOG_INFO("Connecting to the MQTT Broker!\n");

                    status = mqtt_connect(&conn, broker_ip, DEFAULT_BROKER_PORT,
                                        BROKER_KEEP_ALIVE,
                                        MQTT_CLEAN_SESSION_ON);

                    if(status == MQTT_STATUS_ERROR) {
                        LOG_ERR("Error while connecting to the MQTT broker, retrying! Status: %d\n", status);
                        state = STATE_NET_OK;
                    } else {
                        state = STATE_CONNECTING;
                    }
                } else {
                    state = STATE_CONNECTED;
                }
            }

            if(state == STATE_CONNECTED){

                char complete_topic_name[BUFFER_SIZE];
                sprintf(complete_topic_name, ALARM_START_SUB_TOPIC, IPv6addr);
                LOG_INFO("Subscribing to the %s topic!\n", complete_topic_name);

                sprintf(alarm_start_sub_topic, ALARM_START_SUB_TOPIC, IPv6addr);

                LOG_DBG("Topic to subscribe is: %s\n", alarm_start_sub_topic);

                status = mqtt_subscribe(&conn, NULL, alarm_start_sub_topic, MQTT_QOS_LEVEL_0);

                if(status == MQTT_STATUS_OUT_QUEUE_FULL){

                    LOG_ERR("Tried to subscribe to topic \"alarm\" but command queue was full!\n");
                    PROCESS_EXIT();
                }

                state = STATE_SUBSCRIBING;
            }

            if(state == STATE_SUBSCRIBED){

                if(restarted){
                //Device has been restarted after fire was detected. Send command Alarm Stop.
                LOG_INFO("Sending alarm stop message to the MQTT Collector!\n");

                sprintf(alarm_stop_pub_topic, ALARM_STOP_PUB_TOPIC , IPv6addr);
                LOG_DBG("Alarm Stop topic is: %s\n", alarm_stop_pub_topic);

                memset(alarm_stop_buffer, 0, APP_BUFFER_SIZE);
                sprintf(alarm_stop_buffer, "{\"alarm\": false}");

                    status = publish(alarm_stop_pub_topic, alarm_stop_buffer);

                    LOG_INFO("Status: %d\n", status);

                    if(status != MQTT_STATUS_OK){
                        LOG_ERR("Error during publishing registration message\n");
                        switch (status){
                            case  MQTT_STATUS_OUT_QUEUE_FULL:
                            LOG_ERR("Error: MQTT_STATUS_OUT_QUEUE_FULL\n");
                            break;
                            case  MQTT_STATUS_NOT_CONNECTED_ERROR:
                            LOG_ERR("Error: MQTT_STATUS_NOT_CONNECTED_ERROR\n");
                            break;
                            case   MQTT_STATUS_INVALID_ARGS_ERROR:
                            LOG_ERR("Error:  MQTT_STATUS_INVALID_ARGS_ERROR\n");
                            break;
                            case   MQTT_STATUS_DNS_ERROR:
                            LOG_ERR("Error:  MQTT_STATUS_DNS_ERROR\n");
                            break;
                            default:
                            LOG_ERR("Error:  Unknown\n"); // It should never enter default case. 
                            break;
                        }
                    }

                    restarted = false;
                    etimer_set(&flame_detector_state_check_timer, FLAME_DETECTOR_STATE_CHECK_INTERVAL);
                    continue;
                }

                LOG_INFO("Sending registration message to the MQTT Collector!\n");

                sprintf(registration_pub_topic, "%s", "flame-detector-registration");

                memset(registration_buffer, 0, APP_BUFFER_SIZE);
                sprintf(registration_buffer, "{\"flameDetectorID\": \"%s\"}", IPv6addr);

                status = publish(registration_pub_topic, registration_buffer);

                LOG_INFO("Status: %d\n", status);

                if(status != MQTT_STATUS_OK){

                    LOG_ERR("Error during publishing registration message\n");

                    switch (status){
                        case  MQTT_STATUS_OUT_QUEUE_FULL:
                        LOG_ERR("Error: MQTT_STATUS_OUT_QUEUE_FULL\n");
                        break;
                        case  MQTT_STATUS_NOT_CONNECTED_ERROR:
                        LOG_ERR("Error: MQTT_STATUS_NOT_CONNECTED_ERROR\n");
                        break;
                        case   MQTT_STATUS_INVALID_ARGS_ERROR:
                        LOG_ERR("Error:  MQTT_STATUS_INVALID_ARGS_ERROR\n");
                        break;
                        case   MQTT_STATUS_DNS_ERROR:
                        LOG_ERR("Error:  MQTT_STATUS_DNS_ERROR\n");
                        break;
                        default:
                        LOG_ERR("Error:  Unknown\n"); // It should never enter default case. 
                        break;
                    }
                } else {
                    LOG_INFO("Registered to the MQTT Collector.\n");
                    state = STATE_REGISTERED;
                }
            }
            if(state == STATE_REGISTERED){
                // Commented for the purpose of registration

                // Simulate sending a message each 10 seconds in normal condition
                // and one message each 2 seconds if a flame is detected.
                /*if(!flame_detected)
                //if no flame detected, small possibility for a flame to appear
                flame_detected = (get_random_value(0, 100) > 90) ? true : false;
                else
                //if a flame is detected, discrete possibility to be there for a lot of time --> fire
                flame_detected = (get_random_value(0, 20) < 20) ? true : false;
                */

                check_for_alarm();

                LOG_DBG("Flame detected is %s\n\n", ((int)flame_detected==1 ? "true": "false"));

                if(!flame_detected){

                    flame_detected_publish_counter = FLAME_DETECTED_PUBLISH_INTERVAL -1;

                    switch(no_flame_detected_publish_counter){

                        case NO_FLAME_DETECTED_PUBLISH_INTERVAL -1:

                        LOG_INFO("Sending update to the MQTT Collector: No Flame Detected!\n");

                        sprintf(flame_detection_pub_topic, "flame-detector-%s/flame-detected-updates", IPv6addr);

                        LOG_DBG("Topic is: %s\n", flame_detection_pub_topic);

                        memset(updates_buffer, 0, APP_BUFFER_SIZE);

                        sprintf(updates_buffer, "{\"flameDetected\": false}");
                        LOG_DBG("out_buf: %s\n", updates_buffer);

                        status = publish(flame_detection_pub_topic, updates_buffer);

                        LOG_DBG("Status: %d\n", status);
                        no_flame_detected_publish_counter--;

                        break;

                        case 0:
                        no_flame_detected_publish_counter = NO_FLAME_DETECTED_PUBLISH_INTERVAL -1;
                        break;

                        default:
                        no_flame_detected_publish_counter--;
                        break;

                    }
                } else {
                    no_flame_detected_publish_counter = NO_FLAME_DETECTED_PUBLISH_INTERVAL -1;

                    LOG_INFO("Flame detected!\n");

                    switch(flame_detected_publish_counter){
                        case (FLAME_DETECTED_PUBLISH_INTERVAL -1):
                        LOG_INFO("Sending update to the MQTT Collector: Flame Detected!\n");

                        sprintf(flame_detection_pub_topic, "flame-detector-%s/flame-detected-updates", IPv6addr);

                        memset(updates_buffer, 0, APP_BUFFER_SIZE);

                        sprintf(updates_buffer, "{\"flameDetected\": true}");

                        status = publish(flame_detection_pub_topic, updates_buffer);

                        LOG_DBG("Status: %d\n", status);
                        flame_detected_publish_counter--;
                        break;

                        case 0:
                        flame_detected_publish_counter = FLAME_DETECTED_PUBLISH_INTERVAL -1;
                        break;

                        default:
                        flame_detected_publish_counter--;
                    }
                }

            }

            if(state == STATE_ALARM_STARTED){
                // Make red led blinking

                if(led_is_on) {
                    leds_off(LEDS_NUM_TO_MASK(LEDS_RED));
                    led_is_on = false;
                }
                else{
                    leds_on(LEDS_NUM_TO_MASK(LEDS_RED));
                    led_is_on = true;
                }

            } else if ( state == STATE_DISCONNECTED ){
                state = STATE_INIT;
                printf("Disconnected from MQTT broker\n");
            }

            etimer_set(&flame_detector_state_check_timer, FLAME_DETECTOR_STATE_CHECK_INTERVAL);

        } else if(ev == button_hal_periodic_event && state == STATE_ALARM_STARTED) {
            // Enters here only if the ALARM is started

            reset_btn = (button_hal_button_t *) data;
            LOG_DBG("Periodic event, %u seconds \n", reset_btn->press_duration_seconds);

            if(reset_btn->press_duration_seconds > 5) {

                LOG_DBG("Button pressed for more than 5 secs: Device restarted\n");
                leds_off(LEDS_NUM_TO_MASK(LEDS_RED));

                state = STATE_INIT;

                flame_detected = false;
                seconds_to_alarm = 120;
                restarted = true;

            }
            // Initialize periodic timer to check the status 
            etimer_set(&flame_detector_state_check_timer, FLAME_DETECTOR_STATE_CHECK_INTERVAL);
        }

    }

  PROCESS_END();
}
/*---------------------------------------------------------------------------*/
