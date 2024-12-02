package com.bignerdranch.android.androidcllisionheatmap;

import androidx.fragment.app.FragmentActivity;

import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.bignerdranch.android.androidcllisionheatmap.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
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
    private Button drawPathButton, drawColorStreetButton;
    private EditText startLocationInput, destinationLocationInput, zipCodeInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        drawPathButton = findViewById(R.id.draw_path_button);
        drawColorStreetButton = findViewById(R.id.draw_color_street_button);
        startLocationInput = findViewById(R.id.start_location);
        destinationLocationInput = findViewById(R.id.destination_location);
        zipCodeInput = findViewById(R.id.zip_code_input);

        drawPathButton.setOnClickListener(v -> drawPath());
        drawColorStreetButton.setOnClickListener(v -> drawColorStreet());
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

    private void drawPath() {
        String startAddress = startLocationInput.getText().toString().trim();
        String destAddress = destinationLocationInput.getText().toString().trim();

        if (startAddress.isEmpty() || destAddress.isEmpty()) {
            Log.e("MAP_ERROR", "Start or destination input is empty.");
            Toast.makeText(this, "Please enter both start and destination addresses", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch and process start location coordinates
        getCoordinatesFromAddress(startAddress, new CoordinateCallback() {
            @Override
            public void onCoordinatesFetched(LatLng startLatLng) {
                if (startLatLng != null) {
                    Log.d("DEBUG", "Start coordinates fetched: " + startLatLng.latitude + ", " + startLatLng.longitude);

                    // Fetch and process destination location coordinates
                    getCoordinatesFromAddress(destAddress, new CoordinateCallback() {
                        @Override
                        public void onCoordinatesFetched(LatLng destLatLng) {
                            if (destLatLng != null) {
                                Log.d("DEBUG", "Destination coordinates fetched: " + destLatLng.latitude + ", " + destLatLng.longitude);
                                // Execute fetchData with coordinates for safe path
                                new fetchData(MapsActivity.this, startLatLng.latitude, startLatLng.longitude, destLatLng.latitude, destLatLng.longitude).execute();
                            } else {
                                Log.e("MAP_ERROR", "Unable to get coordinates for destination.");
                                Toast.makeText(MapsActivity.this, "Unable to find coordinates for destination location", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } else {
                    Log.e("MAP_ERROR", "Unable to get coordinates for start location.");
                    Toast.makeText(MapsActivity.this, "Unable to find coordinates for start location", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }



    private void drawColorStreet() {
        String zipCode = zipCodeInput.getText().toString().trim();
        if (!zipCode.isEmpty()) {
            new fetchData(this, zipCode).execute();
        } else {
            Log.e("MAP_ERROR", "Zip code is empty.");
        }
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

    @Override
    public void onDataFetched(List<fetchData.LineStringWithGridcode> linesWithGridcode) {
        if (mMap == null || linesWithGridcode == null || linesWithGridcode.isEmpty()) {
            Log.e("MAP_ERROR", "Map is not ready or no lines to draw");
            return;
        }

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasLines = false;

        for (fetchData.LineStringWithGridcode lineWithGridcode : linesWithGridcode) {
            List<LatLng> line = lineWithGridcode.lineString;
            int gridcode = lineWithGridcode.gridcode;

            if (line.isEmpty()) continue;

            int polylineColor = getColorForGridcode(gridcode);
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(line)
                    .width(5)
                    .color(polylineColor);

            mMap.addPolyline(polylineOptions);
            for (LatLng point : line) boundsBuilder.include(point);
            hasLines = true;
        }

        if (hasLines) {
            try {
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100;
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            } catch (IllegalStateException e) {
                Log.e("MAP_ERROR", "Error fitting map bounds: " + e.getMessage());
                LatLng nycCenter = new LatLng(40.730610, -73.935242);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nycCenter, 10));
            }
        }
    }

    private int getColorForGridcode(int gridcode) {
        switch (gridcode) {
            case 0: return Color.RED;
            case 1: return Color.BLUE;
            case 2: return Color.GREEN;
            case 3: return Color.YELLOW;
            case 4: return Color.MAGENTA;
            case 5: return Color.CYAN;
            case 6: return Color.DKGRAY;
            case 7: return Color.LTGRAY;
            case 8: return Color.BLACK;
            case 9: return Color.WHITE;
            default: return Color.GRAY;
        }
    }
}

/*package com.bignerdranch.android.androidcllisionheatmap;

import androidx.fragment.app.FragmentActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.bignerdranch.android.androidcllisionheatmap.databinding.ActivityMapsBinding;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, fetchData.DataCallback {

    private GoogleMap mMap;
    private ActivityMapsBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMapsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Enable zoom controls and gestures
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setZoomGesturesEnabled(true);

        // Set a default marker in NYC
        LatLng nycCenter = new LatLng(40.730610, -73.935242);  // Approximate center of NYC
        mMap.addMarker(new MarkerOptions().position(nycCenter).title("Marker in NYC"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nycCenter, 10));

        // Fetch data and draw lines when fetched
        new fetchData(this).execute();  // Pass the callback (this activity) to fetchData
    }

    // Callback method when data is fetched
    @Override
    public void onDataFetched(List<fetchData.LineStringWithGridcode> linesWithGridcode) {
        if (mMap == null || linesWithGridcode == null || linesWithGridcode.isEmpty()) {
            Log.e("MAP_ERROR", "Map is not ready or no lines to draw");
            return;
        }

        // Create a LatLngBounds.Builder to include all the points of the polylines
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasLines = false;

        // Draw polylines for each LineString and include them in the bounds
        for (fetchData.LineStringWithGridcode lineWithGridcode : linesWithGridcode) {
            List<LatLng> line = lineWithGridcode.lineString;
            int gridcode = lineWithGridcode.gridcode;

            if (line.isEmpty()) {
                Log.e("MAP_ERROR", "Line is empty, skipping.");
                continue;
            }

            // Log how many points are in this line and the gridcode
            Log.d("MAP_POLYLINE", "Adding polyline with points: " + line.size() + ", Gridcode: " + gridcode);

            // Get the color based on gridcode
            int polylineColor = getColorForGridcode(gridcode);

            // Create and add the polyline
            PolylineOptions polylineOptions = new PolylineOptions()
                    .addAll(line)
                    .width(5)
                    .color(polylineColor);  // Color the lines based on gridcode

            // Add the polyline to the map
            mMap.addPolyline(polylineOptions);

            // Add all points of the polyline to the bounds
            for (LatLng point : line) {
                boundsBuilder.include(point);
            }
            hasLines = true;  // We have at least one valid line
        }

        // Move the camera to fit the bounds of the drawn polylines
        if (hasLines) {
            try {
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100; // Padding around the polylines (in pixels)
                mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
            } catch (IllegalStateException e) {
                Log.e("MAP_ERROR", "Error fitting map bounds: " + e.getMessage());

                // Fallback to NYC center if bounds calculation fails
                LatLng nycCenter = new LatLng(40.730610, -73.935242);  // Approximate center of NYC
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(nycCenter, 10));
            }
        }
    }

    // Function to get color based on gridcode
    private int getColorForGridcode(int gridcode) {
        switch (gridcode) {
            case 0:
                return Color.RED;
            case 1:
                return Color.BLUE;
            case 2:
                return Color.GREEN;
            case 3:
                return Color.YELLOW;
            case 4:
                return Color.MAGENTA;
            case 5:
                return Color.CYAN;
            case 6:
                return Color.DKGRAY;
            case 7:
                return Color.LTGRAY;
            case 8:
                return Color.BLACK;
            case 9:
                return Color.WHITE;
            default:
                return Color.GRAY;  // Default color if gridcode is not 0-9
        }
    }
}*/

