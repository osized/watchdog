FROM java:8
WORKDIR /
ADD target/Watchdog-1.0-SNAPSHOT-jar-with-dependencies.jar watchdog.jar
EXPOSE 8080
CMD java -Xmx256m -jar watchdog.jar