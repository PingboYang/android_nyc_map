package com.bignerdranch.android.androidcllisionheatmap;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class fetchData extends AsyncTask<Void, Void, List<fetchData.LineStringWithGridcode>> {

    private DataCallback callback;
    private String zipCode;
    private double startLat, startLng, destLat, destLng;
    private boolean isPathRequest;

    // Constructor for zip code request
    public fetchData(DataCallback callback, String zipCode) {
        this.callback = callback;
        this.zipCode = zipCode;
        this.isPathRequest = false;  // Indicates this is a color street request
    }

    // Constructor for path request with coordinates
    public fetchData(DataCallback callback, double startLat, double startLng, double destLat, double destLng) {
        this.callback = callback;
        this.startLat = startLat;
        this.startLng = startLng;
        this.destLat = destLat;
        this.destLng = destLng;
        this.isPathRequest = true;  // Indicates this is a path request
    }

    @Override
    protected List<LineStringWithGridcode> doInBackground(Void... voids) {
        List<LineStringWithGridcode> lineStringsWithGridcode = new ArrayList<>();
        Log.d("FETCH_DATA", "Starting data fetch..."); // Log start of fetch
        String apiUrl;

        if (isPathRequest) {
            // Safe path API endpoint
            apiUrl = "https://backend-collision.onrender.com/api/SafePath?latitude1=" + startLat +
                    "&longitude1=" + startLng + "&latitude2=" + destLat + "&longitude2=" + destLng;

        } else {
            // Draw color street API endpoint by zip code
            apiUrl = "https://backend-collision.onrender.com/api/" + zipCode;
        }

        try {
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONArray jsonArray = new JSONArray(response.toString());

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject feature = jsonArray.getJSONObject(i);
                    int gridcode = feature.optInt("gridcode", 0);

                    JSONObject geometry = feature.getJSONObject("geom");
                    if (geometry.getString("type").equals("LineString")) {
                        JSONArray coordinates = geometry.getJSONArray("coordinates");
                        List<LatLng> latLngList = new ArrayList<>();
                        for (int j = 0; j < coordinates.length(); j++) {
                            JSONArray coordPair = coordinates.getJSONArray(j);
                            double lon = coordPair.getDouble(0);
                            double lat = coordPair.getDouble(1);
                            latLngList.add(new LatLng(lat, lon));
                        }
                        lineStringsWithGridcode.add(new LineStringWithGridcode(latLngList, gridcode));
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FETCH_DATA_ERROR", "Exception occurred: " + e.getMessage());
        }
        return lineStringsWithGridcode;
    }

    @Override
    protected void onPostExecute(List<LineStringWithGridcode> linesWithGridcode) {
        if (callback != null) {
            callback.onDataFetched(linesWithGridcode);
        }
    }

    public interface DataCallback {
        void onDataFetched(List<LineStringWithGridcode> linesWithGridcode);
    }

    // Helper class to store LineString and gridcode together
    public static class LineStringWithGridcode {
        List<LatLng> lineString;
        int gridcode;

        public LineStringWithGridcode(List<LatLng> lineString, int gridcode) {
            this.lineString = lineString;
            this.gridcode = gridcode;
        }
    }
}

/*package com.bignerdranch.android.androidcllisionheatmap;

import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class fetchData extends AsyncTask<Void, Void, List<fetchData.LineStringWithGridcode>> {

    private DataCallback callback;

    // Constructor to receive the callback from MapsActivity
    public fetchData(DataCallback callback) {
        this.callback = callback;
    }

    // Class to hold the line and gridcode together
    public static class LineStringWithGridcode {
        List<LatLng> lineString;
        int gridcode;

        public LineStringWithGridcode(List<LatLng> lineString, int gridcode) {
            this.lineString = lineString;
            this.gridcode = gridcode;
        }
    }

    @Override
    protected List<LineStringWithGridcode> doInBackground(Void... voids) {
        List<LineStringWithGridcode> lineStringsWithGridcode = new ArrayList<>();
        String apiUrl = "https://backend-collision.onrender.com/api/10007"; // Your API endpoint https://backend-collision.onrender.com/swagger-ui/index.html#/

        try {
            // Fetch data from the API
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            // Get the response
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                // Parse the response
                JSONArray jsonArray = new JSONArray(response.toString());

                // Extract LineStrings and gridcode
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject feature = jsonArray.getJSONObject(i);
                    int gridcode = feature.optInt("gridcode", 0);  // Default to 0 if not found
                    JSONObject geometry = feature.getJSONObject("geom");

                    // Make sure the geometry type is LineString
                    if (geometry.getString("type").equals("LineString")) {
                        JSONArray coordinates = geometry.getJSONArray("coordinates");
                        List<LatLng> latLngList = new ArrayList<>();
                        for (int j = 0; j < coordinates.length(); j++) {
                            JSONArray coordPair = coordinates.getJSONArray(j);
                            double lon = coordPair.getDouble(0);
                            double lat = coordPair.getDouble(1);
                            latLngList.add(new LatLng(lat, lon));  // Use lat, lon in correct order
                        }

                        // Add the lineString and gridcode to the list
                        lineStringsWithGridcode.add(new LineStringWithGridcode(latLngList, gridcode));

                        // Log the gridcode and size of LineString
                        Log.d("FETCH_DATA", "Gridcode: " + gridcode + ", LineString size: " + latLngList.size());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("FETCH_DATA_ERROR", "Exception occurred: " + e.getMessage());
        }

        return lineStringsWithGridcode;  // Return the lines and gridcode data
    }

    @Override
    protected void onPostExecute(List<LineStringWithGridcode> linesWithGridcode) {
        if (callback != null) {
            callback.onDataFetched(linesWithGridcode);  // Pass the data to the callback
        }
    }

    // Define an interface to act as a callback
    public interface DataCallback {
        void onDataFetched(List<LineStringWithGridcode> linesWithGridcode);
    }
}
// Updated fetchData class with two endpoint methods for street data and safe path data
*/


