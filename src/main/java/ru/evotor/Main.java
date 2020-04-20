package ru.evotor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        Watchdog watchdog = new Watchdog();

        while (true) {
            try {
                watchdog.check();
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
                System.out.println("Failed, will retry in an hour");
            }
            Thread.sleep(TimeUnit.HOURS.toMillis(1));
        }
    }
}
