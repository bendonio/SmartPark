/* MQTT broker address. */
#define MQTT_CLIENT_BROKER_IP_ADDR "fd00::1"

/* Default config values */
#define DEFAULT_BROKER_PORT                 1883
#define BROKER_KEEP_ALIVE                   30
#define NO_FLAME_DETECTED_PUBLISH_INTERVAL  10
#define FLAME_DETECTED_PUBLISH_INTERVAL     2

/*---------------------------------------------------------------------------*/
/* Various states */
static uint8_t state;

#define STATE_INIT    		  0
#define STATE_NET_OK    	  1
#define STATE_CONNECTING      2
#define STATE_CONNECTED       3
#define STATE_SUBSCRIBED      4
#define STATE_SUBSCRIBING     5
#define STATE_REGISTERED      6
#define STATE_ALARM_STARTED   7
#define STATE_DISCONNECTED    8

/*---------------------------------------------------------------------------*/
/* Maximum TCP segment size for outgoing segments of our socket */
#define MAX_TCP_SEGMENT_SIZE      32
#define CONFIG_IP_ADDR_STR_LEN    64

/*---------------------------------------------------------------------------*/
#define MAX_LEN_IPV6_ADDR         46

/*---------------------------------------------------------------------------*/
/* Buffer size for Client ID and Topics */
#define BUFFER_SIZE 64

/*---------------------------------------------------------------------------*/
/* Buffer size for the main MQTT buffers*/ 
#define APP_BUFFER_SIZE 512

/*---------------------------------------------------------------------------*/

#define ALARM_START_SUB_TOPIC "flame-detector-%s/alarm-start"
#define ALARM_STOP_PUB_TOPIC "flame-detector-%s/alarm-stop"

// Periodic timer to check the state of the MQTT client
#define FLAME_DETECTOR_STATE_CHECK_INTERVAL     (CLOCK_SECOND * 1)
/*---------------------------------------------------------------------------*/
/* JSON messages */ 

#define FLAME_DETECTOR_ALARM_START	"{\"alarm\": true}"
#define FLAME_DETECTOR_ALARM_STOP	"{\"alarm\": false}"