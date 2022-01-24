CONTIKI_PROJECT = coap-server
all: $(CONTIKI_PROJECT)
CONTIKI = /home/user/contiki-ng

CFLAGS += -DPROJECT_CONF_H=\"project-conf.h\"

MODULES_REL += $(CONTIKI)/examples/project/coap-sensors/resources
MODULES_REL += $(CONTIKI)/examples/project/coap-sensors/utils
MODULES_REL += $(CONTIKI)/examples/project/coap-sensors/alarm-processes


# Include optional target-specific module
include $(CONTIKI)/Makefile.identify-target
MODULES_REL += $(TARGET)

include $(CONTIKI)/Makefile.dir-variables
MODULES += $(CONTIKI_NG_APP_LAYER_DIR)/coap

MODULES += $(CONTIKI)/examples/project/coap-sensors/utils

include $(CONTIKI)/Makefile.include
