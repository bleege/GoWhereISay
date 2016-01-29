package com.bradleege.gowhereisay;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.speech.RecognizerIntent;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.mapbox.directions.DirectionsCriteria;
import com.mapbox.directions.MapboxDirections;
import com.mapbox.directions.service.models.DirectionsResponse;
import com.mapbox.directions.service.models.DirectionsRoute;
import com.mapbox.directions.service.models.Waypoint;
import com.mapbox.geocoder.MapboxGeocoder;
import com.mapbox.geocoder.service.models.GeocoderFeature;
import com.mapbox.geocoder.service.models.GeocoderResponse;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.views.MapView;
import java.util.ArrayList;
import java.util.List;
import retrofit.Callback;
import retrofit.Response;
import retrofit.Retrofit;

public class MainActivity extends AppCompatActivity {

    private static String TAG = "MainActivity";

    protected static final int RESULT_SPEECH = 1;

    private MapView mapView;
    private static final LatLng pikePlaceLatLng = new LatLng(47.60865, -122.34052);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.setStyle(Style.MAPBOX_STREETS);
        resetMap();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "FAB onClick()");

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

        mapView.onCreate(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
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
    protected void onStop() {
        super.onStop();
        mapView.onStop();
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
                    Log.i(TAG, "results text = " + text);
                    if (text.size() > 0) {
                        searchForEndpointCoordinates(text.get(0));
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

    private void searchForEndpointCoordinates(final String endpoint) {

        // Get Coordinates For Endpoint
        MapboxGeocoder client = new MapboxGeocoder.Builder()
                .setAccessToken(getString(R.string.mapbox_access_token))
                .setLocation(endpoint)
                .setProximity(pikePlaceLatLng.getLongitude(), pikePlaceLatLng.getLatitude())
                .build();

        client.enqueue(new Callback<GeocoderResponse>() {
            @Override
            public void onResponse(Response<GeocoderResponse> response, Retrofit retrofit) {

                List<GeocoderFeature> results = response.body().getFeatures();
                if (results.size() > 0) {
                    Log.i(TAG, "Coordinates = " + results.get(0).getLatitude() + ", " + results.get(0).getLongitude());
                    searchForDirections(results.get(0));
                } else {
                    Toast.makeText(getApplicationContext(),
                            "Couldn't find a coordinate for '" + endpoint + "'",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Throwable t) {
                Toast.makeText(getApplicationContext(),
                        "Failure occurred while trying to find a coordinate for '" + endpoint + "'",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchForDirections(final GeocoderFeature endpointFeature) {

        Waypoint origin = new Waypoint(pikePlaceLatLng.getLongitude(), pikePlaceLatLng.getLatitude());
        Waypoint destination = new Waypoint(endpointFeature.getLongitude(), endpointFeature.getLatitude());

        MapboxDirections md = new MapboxDirections.Builder()
                .setAccessToken(getString(R.string.mapbox_access_token))
                .setOrigin(origin)
                .setDestination(destination)
                .setProfile(DirectionsCriteria.PROFILE_WALKING)
                .build();

        md.enqueue(new Callback<DirectionsResponse>() {
            @Override
            public void onResponse(Response<DirectionsResponse> response, Retrofit retrofit) {

                // Print some info about the route
                DirectionsRoute firstRoute = response.body().getRoutes().get(0);
                Log.i(TAG, "Distance: " + firstRoute.getDistance());
                Log.i(TAG, (String.format("Route is %d meters long.", firstRoute.getDistance())));

                // Draw the route on the map
                drawRoute(firstRoute);
            }

            @Override
            public void onFailure(Throwable t) {
                Toast.makeText(getApplicationContext(),
                        "Failure occurred while trying to find directions for '" + endpointFeature.getPlaceName() + "'",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void drawRoute(DirectionsRoute route) {

        resetMap();

        // Convert List<Waypoint> into LatLng[]
        List<Waypoint> waypoints = route.getGeometry().getWaypoints();
        LatLng[] point = new LatLng[waypoints.size()];
        for (int i = 0; i < waypoints.size(); i++) {
            point[i] = new LatLng(
                    waypoints.get(i).getLatitude(),
                    waypoints.get(i).getLongitude());

            // Display End Marker
            if (i == waypoints.size() - 1) {
                mapView.addMarker(new MarkerOptions().position(point[i]).title("Endpoint"));
            }
        }

        // Draw Points on MapView
        mapView.addPolyline(new PolylineOptions()
                .add(point)
                .color(Color.parseColor("#3887be"))
                .width(5));
    }

    private void resetMap() {
        mapView.removeAllAnnotations();
        mapView.moveCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(pikePlaceLatLng).zoom(12).build()));

        mapView.addMarker(new MarkerOptions().position(pikePlaceLatLng).title("Pike Place Market"));
    }
}
