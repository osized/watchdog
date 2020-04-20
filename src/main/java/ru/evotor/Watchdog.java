package ru.evotor;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

class Watchdog {

    private final static String APP_NAME = "HDFS Uploader: MoveToBuffer";
    private final static int TIME_LIMIT_MINUTES = 180;


    public void check() throws IOException, URISyntaxException {

        CloseableHttpClient httpClient = HttpClients.createDefault();

        URIBuilder builder = new URIBuilder("http://m1-ev-hdpm01.ev.local:8088/ws/v1/cluster/apps");
        builder.setParameter("startedTimeBegin",
                (System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)) + "")
                .setParameter("startedTimeEnd", System.currentTimeMillis() + "");


        HttpGet request = new HttpGet(builder.build());


        CloseableHttpResponse response = httpClient.execute(request);

        try {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                JSONObject jsonResponse = new JSONObject(EntityUtils.toString(entity));
                Optional<String> stuckAppId = getStuckAppId(jsonResponse);
                if (stuckAppId.isPresent()) {
                    kill(stuckAppId.get(), httpClient);
                }
            }
        } finally {
            response.close();
        }
    }

    private Optional<String> getStuckAppId (JSONObject info) throws IOException {
        JSONArray apps = info.getJSONObject("apps").getJSONArray("app");
        Iterator<Object> iterator = apps.iterator();
        while (iterator.hasNext()) {
            JSONObject appObject = (JSONObject) iterator.next();
            String name = appObject.getString("name");
            String finalStatus = appObject.getString("finalStatus");
            if (!APP_NAME.equals(name) || !finalStatus.equals("UNDEFINED")) {
                continue;
            }

            int elapsedRaw = appObject.getInt("elapsedTime");
            int elapsedMins = elapsedRaw / 1000 / 60;
            if (elapsedMins > TIME_LIMIT_MINUTES) {
                return Optional.of(appObject.getString("id"));
            }
        }
        return Optional.empty();
    }

    private void kill(String id, HttpClient client) throws IOException {
        System.out.println("Killing app with id " + id);
        String url = String.format("http://m1-ev-hdpm01.ev.local:8088/ws/v1/cluster/apps/%s/state", id);
        String inputJson = "{\"state\":\"KILLED\"}";
        HttpPut httpPut = new HttpPut(url);
        StringEntity jsonEntity = new StringEntity(inputJson);
        jsonEntity.setContentType("application/json");
        httpPut.setHeader("Accept", "application/json");
        httpPut.setHeader("Content-type", "application/json");
        httpPut.setEntity(jsonEntity);
        HttpResponse response = client.execute(httpPut);

        //Throw runtime exception if status code isn't 200
        if (response.getStatusLine().getStatusCode() != 200) {
            String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            System.out.println("Response body: " + responseBody);
            throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
        }

    }
}