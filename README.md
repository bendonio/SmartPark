# SmartPark
Project for the Internet of Things course of the MSc's degree in Computer Engineering at University of Pisa.

The project is about a smart park, exploiting presence sensors to count the number of occupied/free park slots, smart traffic lights to visually understand if the park is full, and flame detectors to react as fast as possible to a possible fire detected in the park. 

## Installation, configuration and execution
In order to execute the tests described in the report, the following steps are needed:
- Download the entire folder and put it in the '''contiki-ng/examples''' folder.
- Inside the '''smartPark-collector''' folder compile the collector with the following command:
  '''bash
  mvn clean install
  '''
