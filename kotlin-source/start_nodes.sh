#!/bin/bash

cd /root/cordaWasteDisposal/kotlin-source/build/nodes
cd Notary
java -jar corda.jar &
sleep 60
cd ..


cd Syndial
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep 60
cd ..


cd Cliente
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep 60
cd ..


cd Fornitore
java -jar corda.jar &
sleep 2
java -jar corda-webserver.jar &
sleep 60
sleep infinity