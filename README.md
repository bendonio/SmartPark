# SmartPark
Project for the Internet of Things course of the MSc's degree in Computer Engineering at University of Pisa.

The project is about a smart park, exploiting presence sensors to count the number of occupied/free park slots, smart traffic lights to visually understand if the park is full, and flame detectors to react as fast as possible to a possible fire detected in the park. 

## Installation and execution
In order to execute the test 1 described in the report, the following steps are needed:
- Download the entire folder and put it in the ```contiki-ng/examples``` folder.
- Inside the ```smartPark-collector``` folder compile the collector with the following command:
  ```bash
  mvn clean install
  ```
- Run the MQTT Broker
- Import the  MySQL database contained in ```smartParkDB.sql```
- Open cooja and import the simulation ```simulations/test1_simulated```
- In Cooja, configure the border router to use tunslip6:
  ```bash
  Tools > Serial Socket (SERVER) > Contiki 1
  Start
  ```
  Inside the ```br``` folder execute:
  ```bash
  make TARGET=cooja connect-router-cooja
  ```
- Inside the ```smartPark-collector``` folder, start the collector with the following command: 
  ```bash
  java -jar target/smartPark-collector-0.0.1-SNAPSHOT.jar
- Start the simulation on Cooja
