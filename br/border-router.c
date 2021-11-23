#include "contiki.h"
#include "net/routing/routing.h"
#include "net/routing/rpl-lite/rpl-dag-root.h"
#include "net/netstack.h"
#include "sys/etimer.h"

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "RPL BR"
#define LOG_LEVEL LOG_LEVEL_DBG
#define START_INTERVAL (5 * CLOCK_SECOND)

static struct etimer e_timer; 


/* Declare and auto-start this file's process */
PROCESS(contiki_ng_br, "Contiki-NG Border Router");
AUTOSTART_PROCESSES(&contiki_ng_br);

/*---------------------------------------------------------------------------*/
PROCESS_THREAD(contiki_ng_br, ev, data)
{
  PROCESS_BEGIN();

#if BORDER_ROUTER_CONF_WEBSERVER
  PROCESS_NAME(webserver_nogui_process);
  process_start(&webserver_nogui_process, NULL);
#endif /* BORDER_ROUTER_CONF_WEBSERVER */

  LOG_INFO("Contiki-NG Border Router started\n");

  etimer_set(&e_timer, START_INTERVAL);

  while(1){
    PROCESS_WAIT_EVENT();

    if(ev == PROCESS_EVENT_TIMER && data == &e_timer){
        
        //printf("rpl_dag_root_is_root %d, num of nodes: %d\n", rpl_dag_root_is_root(), uip_sr_num_nodes());
    }

	  etimer_set(&e_timer, START_INTERVAL);
  }

  PROCESS_END();
}
