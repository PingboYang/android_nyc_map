package com.bignerdranch.android.androidcllisionheatmap;

import androidx.fragment.app.FragmentActivity;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.bignerdranch.android.androidcllisionheatmap.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, fetchData.DataCallback {

    private GoogleMap mMap;
    private static final String API_KEY = "AIzaSyCaKlsJUqN-Vc0y8AhdJiwkD_CqFNHFz-o";
    private ActivityMapsBinding binding;
    private Button googleRouteButton, safeRouteButton;
    private EditText startLocationInput, destinationLocationInput;
    private TextView routeInfoText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        googleRouteButton = findViewById(R.id.google_route_button);
        safeRouteButton = findViewById(R.id.safe_route_button);
        startLocationInput = findViewById(R.id.start_location);
        destinationLocationInput = findViewById(R.id.destination_location);
        routeInfoText = findViewById(R.id.route_info_text);

        googleRouteButton.setOnClickListener(v -> drawGoogleRoute());
        safeRouteButton.setOnClickListener(v -> drawSafeRoute());
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);

        LatLng nycCenter = new LatLng(40.730610, -73.935242);
        mMap.addMarker(new MarkerOptions().position(nycCenter).title("Marker in NYC"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nycCenter, 10));
    }

    public void onRouteInfoFetched(String distance, String duration) {
        runOnUiThread(() -> {
            routeInfoText.setText("Estimated Time: " + duration + " | Distance: " + distance);
            Log.d("GOOGLE_ROUTE", "Displaying Info - Time: " + duration + ", Distance: " + distance);

            TextView routeInfoText = findViewById(R.id.route_info_text);
            routeInfoText.setText("Distance: " + distance + "\nDuration: " + duration);
            routeInfoText.setVisibility(View.VISIBLE); // ✅ Ensure it's visible
        });
    }

    @Override
    public void onDataFetched(List<fetchData.LineStringWithGridcode> linesWithGridcode) {
        if (mMap == null) {
            Log.e("MAP_ERROR", "Google Map instance is null");
            return;
        }
        if (linesWithGridcode == null || linesWithGridcode.isEmpty()) {
            Log.e("MAP_ERROR", "No lines received from API");
            return;
        }

        Log.d("MAP_SUCCESS", "Received " + linesWithGridcode.size() + " lines from API");

        for (fetchData.LineStringWithGridcode line : linesWithGridcode) {
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(line.lineString)
                    .width(5)
                    .color(Color.BLUE);
            mMap.addPolyline(polylineOptions);
        }
    }



    private void drawGoogleRoute() {
        String startAddress = startLocationInput.getText().toString().trim();
        String destAddress = destinationLocationInput.getText().toString().trim();

        if (startAddress.isEmpty() || destAddress.isEmpty()) {
            Log.e("MAP_ERROR", "Start or destination input is empty.");
            Toast.makeText(this, "Please enter both start and destination addresses", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch and process start and destination location coordinates
        getCoordinatesFromAddress(startAddress, startLatLng -> {
            if (startLatLng != null) {
                getCoordinatesFromAddress(destAddress, destLatLng -> {
                    if (destLatLng != null) {
                        Log.d("GOOGLE_ROUTE", "Fetching Google route...");
                        new fetchData(MapsActivity.this, startLatLng.latitude, startLatLng.longitude, destLatLng.latitude, destLatLng.longitude, true).execute();
                    } else {
                        Toast.makeText(this, "Invalid destination location.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, "Invalid start location.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void drawSafeRoute() {
        String startAddress = startLocationInput.getText().toString().trim();
        String destAddress = destinationLocationInput.getText().toString().trim();

        if (startAddress.isEmpty() || destAddress.isEmpty()) {
            Log.e("MAP_ERROR", "Start or destination input is empty.");
            Toast.makeText(this, "Please enter both start and destination addresses", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch and process start and destination location coordinates
        getCoordinatesFromAddress(startAddress, startLatLng -> {
            if (startLatLng != null) {
                getCoordinatesFromAddress(destAddress, destLatLng -> {
                    if (destLatLng != null) {
                        Log.d("SAFE_ROUTE", "Fetching Safe Route...");
                        new fetchData((fetchData.DataCallback) this, startLatLng.latitude, startLatLng.longitude, destLatLng.latitude, destLatLng.longitude, false).execute();
                    } else {
                        Toast.makeText(this, "Invalid destination location.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                Toast.makeText(this, "Invalid start location.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void getCoordinatesFromAddress(String address, CoordinateCallback callback) {
        new GeocodingTask(callback).execute(address);
    }

    // Interface to handle coordinates
    public interface CoordinateCallback {
        void onCoordinatesFetched(LatLng latLng);
    }

    // AsyncTask to get coordinates for an address
    private class GeocodingTask extends AsyncTask<String, Void, LatLng> {
        private final CoordinateCallback callback;

        public GeocodingTask(CoordinateCallback callback) {
            this.callback = callback;
        }

        @Override
        protected LatLng doInBackground(String... params) {
            String address = params[0];
            String apiUrl = "https://maps.googleapis.com/maps/api/geocode/json?address=" + address + "&key=" + API_KEY;
            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONObject jsonObject = new JSONObject(response.toString());
                JSONArray results = jsonObject.getJSONArray("results");
                if (results.length() > 0) {
                    JSONObject location = results.getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                    double lat = location.getDouble("lat");
                    double lng = location.getDouble("lng");
                    return new LatLng(lat, lng);
                }
            } catch (Exception e) {
                Log.e("GEOCODING_ERROR", "Error fetching coordinates: " + e.getMessage());
            }
            return null;
        }

        @Override
        protected void onPostExecute(LatLng latLng) {
            callback.onCoordinatesFetched(latLng);
        }
    }
}
