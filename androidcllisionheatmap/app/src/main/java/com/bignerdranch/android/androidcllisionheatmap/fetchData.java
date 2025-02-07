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
    private double startLat, startLng, destLat, destLng;
    private boolean isGoogleRoute;

    // Constructor for path request with coordinates
    public fetchData(DataCallback callback, double startLat, double startLng, double destLat, double destLng, boolean isGoogleRoute) {
        this.callback = callback;
        this.startLat = startLat;
        this.startLng = startLng;
        this.destLat = destLat;
        this.destLng = destLng;
        this.isGoogleRoute = isGoogleRoute;
    }

    @Override
    protected List<LineStringWithGridcode> doInBackground(Void... voids) {
        List<LineStringWithGridcode> lineStringsWithGridcode = new ArrayList<>();
        Log.d("FETCH_DATA", "Starting data fetch...");

        String apiUrl;

        if (isGoogleRoute) {
            // ✅ Construct Google Maps API request
            apiUrl = "https://maps.googleapis.com/maps/api/directions/json?origin=" + startLat + "," + startLng +
                    "&destination=" + destLat + "," + destLng + "&key=AIzaSyCaKlsJUqN-Vc0y8AhdJiwkD_CqFNHFz-o";
        } else {
            // ✅ Construct Safe Route API request
            apiUrl = "https://backend-collision.onrender.com/api/NYCSafeRouteWithPoints?latitude1=" + startLat +
                    "&longitude1=" + startLng + "&latitude2=" + destLat + "&longitude2=" + destLng + "&gridcode=" + 9;
        }

        Log.d("FETCH_DATA", "API URL: " + apiUrl);

        try {
            // ✅ Make API request
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

                Log.d("FETCH_DATA", "API Response: " + response.toString());

                JSONObject jsonResponse = new JSONObject(response.toString());

                if (isGoogleRoute) {
                    // ✅ Only process Google Maps route response
                    JSONArray routes = jsonResponse.getJSONArray("routes");

                    if (routes.length() > 0) {
                        JSONObject route = routes.getJSONObject(0);
                        JSONObject legs = route.getJSONArray("legs").getJSONObject(0);

                        // ✅ Extract Distance & Duration
                        String totalDistance = legs.getJSONObject("distance").getString("text");
                        String totalDuration = legs.getJSONObject("duration").getString("text");

                        Log.d("GOOGLE_ROUTE", "Total Distance: " + totalDistance);
                        Log.d("GOOGLE_ROUTE", "Total Duration: " + totalDuration);

                        // ✅ Send distance & duration to UI
                        if (callback != null) {
                            callback.onRouteInfoFetched(totalDistance, totalDuration);
                        }

                        // ✅ Extract & decode polyline
                        JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                        String encodedPolyline = overviewPolyline.getString("points");
                        List<LatLng> decodedPolyline = decodePolyline(encodedPolyline);
                        lineStringsWithGridcode.add(new LineStringWithGridcode(decodedPolyline, 0));
                    } else {
                        Log.e("GOOGLE_ROUTE", "No routes found");
                    }
                } else {
                    // ✅ Only process Safe Route API response
                    if (!jsonResponse.has("dataset")) {
                        Log.e("FETCH_DATA_ERROR", "API response does not contain dataset key");
                        return lineStringsWithGridcode;
                    }

                    JSONArray dataset = jsonResponse.getJSONArray("dataset");

                    if (dataset.length() == 0) {
                        Log.e("FETCH_DATA_ERROR", "Dataset is empty, no lines to display");
                        return lineStringsWithGridcode;
                    }

                    for (int i = 0; i < dataset.length(); i++) {
                        JSONObject feature = dataset.getJSONObject(i);
                        int gridcode = feature.optInt("gridcode", 0);

                        if (!feature.has("geom")) {
                            Log.e("FETCH_DATA_ERROR", "Skipping entry with missing geom");
                            continue;
                        }

                        JSONObject geometry = feature.getJSONObject("geom");
                        if (!geometry.has("coordinates")) {
                            Log.e("FETCH_DATA_ERROR", "Skipping entry with missing coordinates");
                            continue;
                        }

                        if (geometry.getString("type").equals("LineString")) {
                            JSONArray coordinates = geometry.getJSONArray("coordinates");
                            List<LatLng> latLngList = new ArrayList<>();

                            for (int j = 0; j < coordinates.length(); j++) {
                                JSONArray coordPair = coordinates.getJSONArray(j);
                                double lon = coordPair.getDouble(0);
                                double lat = coordPair.getDouble(1);
                                latLngList.add(new LatLng(lat, lon));
                            }

                            Log.d("FETCH_DATA", "Parsed LineString with " + latLngList.size() + " points, gridcode: " + gridcode);
                            lineStringsWithGridcode.add(new LineStringWithGridcode(latLngList, gridcode));
                        }
                    }

                    if (lineStringsWithGridcode.isEmpty()) {
                        Log.e("FETCH_DATA_ERROR", "No LineStrings were successfully parsed from API response");
                    }
                }
            } else {
                Log.e("FETCH_DATA_ERROR", "API request failed with response code: " + responseCode);
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
        void onRouteInfoFetched(String distance, String duration); // ✅ New method for distance & duration
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

    // Method to decode polyline strings into LatLng points
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((lat / 1E5), (lng / 1E5));
            poly.add(p);
        }
        return poly;
    }
}
