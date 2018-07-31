package com.tsuaojap.navigationtutorial;

import android.support.annotation.NonNull;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener
{
    private Button              button;
    private DirectionsRoute     currentRoute;
    private LatLng              destinationCoord;
    private LatLng              originCoord;
    private Location            originLocation;
    private LocationEngine      locationEngine;
    private LocationLayerPlugin locationPlugin;
    private Marker              destinationMarker;
    private MapboxMap           map;
    private MapView             mapView;
    private NavigationMapRoute  navigationMapRoute;
    private PermissionsManager  permissionsManager;
    private Point               destinationPosition;
    private Point               originPosition;
    private static final String TAG = "DirectionsActivity";


    @SuppressWarnings({"MissingPermission}"})
    private   void enableLocationPlugin()
    {
        if(PermissionsManager.areLocationPermissionsGranted(this))
        {
            initializeLocationEngine();

            locationPlugin = new LocationLayerPlugin(mapView, map, locationEngine);
            locationPlugin.setRenderMode(RenderMode.COMPASS);
        }
        else
        {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    private   void getRoute(Point origin, Point destination)
    {
        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>()
                {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response)
                    {
                        Log.d(TAG, "Response code: " + response.code());

                        if     (response.body() == null)
                        {
                            Log.e(TAG, "No routes found, make sure you set the right user and access token.");
                            return;
                        }
                        else if(response.body().routes().size() < 1)
                        {
                            Log.e(TAG, "No routes found");
                            return;
                        }

                        currentRoute = response.body().routes().get(0);

                        if(navigationMapRoute != null)
                        {
                            navigationMapRoute.removeRoute();
                        }
                        else
                        {
                            navigationMapRoute = new NavigationMapRoute(null, mapView, map, R.style.NavigationMapRoute);
                        }

                        navigationMapRoute.addRoute(currentRoute);
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t)
                    {
                        Log.e(TAG, "Error: " + t.getMessage());
                    }
                });
    }

    @SuppressWarnings({"MissingPermission"})
    private   void initializeLocationEngine()
    {
        LocationEngineProvider locationEngineProvider = new LocationEngineProvider(this);

        locationEngine = locationEngineProvider.obtainBestLocationEngineAvailable();
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        locationEngine.activate();

        Location lastLocation = locationEngine.getLastLocation();

        if(lastLocation != null)
        {
            originLocation = lastLocation;
            setCameraPosition(lastLocation);
        }
        else
        {
            locationEngine.addLocationEngineListener(this);
        }
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public    void onConnected()
    {
        locationEngine.requestLocationUpdates();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(new OnMapReadyCallback()
        {
            @Override
            public void onMapReady(final MapboxMap mapboxMap)
            {
                map = mapboxMap;
                enableLocationPlugin();

                originCoord = new LatLng(originLocation.getLatitude(), originLocation.getLongitude());

                mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener()
                {
                    @Override
                    public void onMapClick(@NonNull LatLng point)
                    {
                        if(destinationMarker != null)
                        {
                            mapboxMap.removeMarker(destinationMarker);
                        }
                        destinationCoord = point;
                        destinationMarker = mapboxMap.addMarker(new MarkerOptions()
                            .position(destinationCoord));

                        destinationPosition = Point.fromLngLat(destinationCoord.getLongitude(), destinationCoord.getLatitude());
                             originPosition = Point.fromLngLat(     originCoord.getLongitude(),      originCoord.getLatitude());

                        getRoute(originPosition, destinationPosition);

                        button.setEnabled(true);
                        button.setBackgroundResource(R.color.mapboxBlue);
                    }
                });

                button = findViewById(R.id.startButton);
                button.setOnClickListener(new View.OnClickListener()
                {
                    public void onClick(View v)
                    {
                        boolean simulateRoute = true;

                        NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                            .directionsRoute(currentRoute)
                            .shouldSimulateRoute(simulateRoute)
                            .build();

                        NavigationLauncher.startNavigation(MainActivity.this, options);
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        mapView.onDestroy();

        if(locationEngine != null)
        {
            locationEngine.deactivate();
        }
    }

    @Override
    public    void onExplanationNeeded(List<String> permissionsToExplain)
    {

    }

    @Override
    public    void onLocationChanged(Location location)
    {
        if(location != null)
        {
            originLocation = location;
            setCameraPosition(location);
            locationEngine.removeLocationEngineListener(this);
        }
    }

    @Override
    public    void onLowMemory()
    {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public    void onPause()
    {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public    void onPermissionResult(boolean granted)
    {
        if(granted)
        {
            enableLocationPlugin();
        }
        else
        {
            finish();
        }
    }

    @Override
    public    void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public    void onResume()
    {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    @SuppressWarnings({"MissingPermission"})
    public    void onStart()
    {
        super.onStart();

        if(locationEngine != null)
        {
            locationEngine.requestLocationUpdates();
        }

        if(locationPlugin != null)
        {
            locationPlugin.onStart();
        }

        mapView.onStart();
    }

    @Override
    public    void onStop()
    {
        super.onStop();

        if(locationEngine != null)
        {
            locationEngine.removeLocationUpdates();
        }

        if(locationPlugin != null)
        {
            locationPlugin.onStop();
        }

        mapView.onStop();
    }

    private   void setCameraPosition(Location location)
    {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
            new LatLng(location.getLatitude(), location.getLongitude()), 13));
    }
}