#!/bin/bash

cd /root/cordaSnam/kotlin-source/build/nodes
cd Notary
java -jar corda.jar &
sleep 60
cd ..


cd Sman
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep 60
cd ..


cd EMI
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep 60
cd ..


cd Songenia
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep 60
cd ..


cd Edifon
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep infinity
