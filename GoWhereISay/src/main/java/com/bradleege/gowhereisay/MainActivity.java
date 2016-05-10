package com.bradleege.gowhereisay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.Constants;
import com.mapbox.services.commons.ServicesException;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;
import com.mapbox.services.directions.v5.DirectionsCriteria;
import com.mapbox.services.directions.v5.MapboxDirections;
import com.mapbox.services.directions.v5.models.DirectionsResponse;
import com.mapbox.services.directions.v5.models.DirectionsRoute;
import com.mapbox.services.geocoding.v5.MapboxGeocoding;
import com.mapbox.services.geocoding.v5.models.GeocodingFeature;
import com.mapbox.services.geocoding.v5.models.GeocodingResponse;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    protected static final int RESULT_SPEECH = 1;

    private MapView mapView;
    private MapboxMap mapboxMap;
    private Marker destinationMarker;
    private static final LatLng pikePlaceLatLng = new LatLng(47.60865, -122.34052);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap map) {
                mapboxMap = map;
                resetMap();
            }
        });

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");

                try {
                    startActivityForResult(intent, RESULT_SPEECH);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getApplicationContext(),
                            "This device doesn't support Speech to Text",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause()  {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    /**
     * Dispatch onLowMemory() to all fragments.
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == R.id.action_reset) {
            if (mapboxMap != null) {
                resetMap();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Dispatch incoming result to the correct fragment.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SPEECH: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> text = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (text.size() > 0) {
                        resetMap();
                        try {
                            searchForEndpointCoordinates(text.get(0));
                        } catch (ServicesException e) {
                            Log.e(TAG, "Exception While Searching:" + e.getMessage());
                            Toast.makeText(MainActivity.this, "Error While Searching For Endpoint", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "We had a failure to communicate.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }

        }

    }

    private void searchForEndpointCoordinates(final String endpoint) throws ServicesException {

        // Get Coordinates For Endpoint
        MapboxGeocoding client = new MapboxGeocoding.Builder()
                    .setAccessToken(getString(R.string.mapbox_access_token))
                    .setLocation(endpoint)
                    .setProximity(Position.fromCoordinates(pikePlaceLatLng.getLongitude(), pikePlaceLatLng.getLatitude()))
                    .build();

        client.enqueueCall(new Callback<GeocodingResponse>() {
            @Override
            public void onResponse(Call<GeocodingResponse> call, Response<GeocodingResponse> response) {

                if (response != null && response.body() != null && response.body().getFeatures() != null) {
                    List<GeocodingFeature> features = response.body().getFeatures();
                    if (!features.isEmpty()) {
                        Log.i(TAG, "Coordinates (Lat / Lon) = " + features.get(0).asPosition().getLatitude() + ", " + features.get(0).asPosition().getLongitude());
                        try {
                            searchForDirections(features.get(0));
                        } catch (ServicesException e) {
                            Log.e(TAG, "Exception While Searching for Directions: "+ e);
                            Toast.makeText(MainActivity.this, "Error While Searching For Directions", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(getApplicationContext(),
                                "Couldn't find a coordinate for '" + endpoint + "'",
                                Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<GeocodingResponse> call, Throwable t) {
                Toast.makeText(getApplicationContext(),
                        "Failure occurred while trying to find a coordinate for '" + endpoint + "'",
                        Toast.LENGTH_SHORT).show();
            }
        });

    }

    private void searchForDirections(final GeocodingFeature endpointFeature) throws ServicesException {

        final ArrayList<Position> points = new ArrayList<>();
        points.add(Position.fromCoordinates(pikePlaceLatLng.getLongitude(), pikePlaceLatLng.getLatitude()));
        points.add(endpointFeature.asPosition());

        MapboxDirections client = new com.mapbox.services.directions.v5.MapboxDirections.Builder()
                .setAccessToken(getString(R.string.mapbox_access_token))
                .setCoordinates(points)
                .setProfile(DirectionsCriteria.PROFILE_DRIVING)
                .build();

        client.enqueueCall(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                if (response != null && response.body() != null) {
                    DirectionsRoute route = response.body().getRoutes().get(0);

                    resetMap();

                    destinationMarker = mapboxMap.addMarker(new MarkerOptions().position(new LatLng(endpointFeature.asPosition().getLatitude(), endpointFeature.asPosition().getLongitude())).title(endpointFeature.getPlaceName()).snippet(endpointFeature.getText()));

                    // Center Route On Map
                    LatLng center = new LatLng(
                            (pikePlaceLatLng.getLatitude() + endpointFeature.asPosition().getLatitude()) / 2,
                            (pikePlaceLatLng.getLongitude() + endpointFeature.asPosition().getLongitude()) / 2);
                    mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(center).build()));

                    // Draw the route on the map
                    drawRoute(route);
                } else {
                    Toast.makeText(getApplicationContext(), "No routes could be found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                Toast.makeText(getApplicationContext(),
                        "Failure occurred while trying to find directions for '" + endpointFeature.getPlaceName() + "'",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawRoute(DirectionsRoute route) {

        // show route
        List<PolylineOptions> polylineOptions = new ArrayList<>();
        PolylineOptions builder = new PolylineOptions();
        builder.color(ContextCompat.getColor(this, R.color.colorAccent));
        builder.alpha(0.5f);

        LineString lineString = LineString.fromPolyline(route.getGeometry(), Constants.OSRM_PRECISION_V5);
        List<Position> coordinates = lineString.getCoordinates();
        for (int lc = 0; lc < coordinates.size(); lc++) {
            builder.add(new LatLng(coordinates.get(lc).getLatitude(), coordinates.get(lc).getLongitude()));
        }

        builder.width(getResources().getDimension(R.dimen.line_width_route));
        polylineOptions.add(builder);
        mapboxMap.addPolylines(polylineOptions);
    }

    private void resetMap() {
        if (destinationMarker != null) {
            mapboxMap.removeMarker(destinationMarker);
        }
        mapboxMap.removeAnnotations();
        mapboxMap.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(pikePlaceLatLng).zoom(12).build()));
        mapboxMap.addMarker(new MarkerOptions().position(pikePlaceLatLng).title("Pike Place Market"));
    }
}
