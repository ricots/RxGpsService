package com.miguelbcr.rx_gpsservice.app;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.miguelbcr.rx_gps_service.lib.RxGpsService;
import com.miguelbcr.rx_gps_service.lib.entities.LatLong;
import com.miguelbcr.rx_gps_service.lib.entities.PermissionDeniedException;
import com.miguelbcr.rx_gps_service.lib.entities.RouteStats;
import com.miguelbcr.rx_gpsservice.R;
import com.google.android.gms.location.LocationRequest;

import java.util.ArrayList;
import java.util.List;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class RouteFragment extends Fragment {
    private static final String TAG = "RouteFragment";
    private static final String PREF_STORE = "RxGpsServiceStore";
    private static final String PREF_IS_PLAYING = "isPlaying";
    private PlaceMapFragment placeMapFragment;
    private ImageView ib_play, ib_stop;
    private CompositeSubscription subscriptions;
    private LatLong lastLatLong;
    private BitmapHelper bitmapHelper;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Log.e("RouteFragment", "onCreateView()");
        return inflater.inflate(R.layout.route_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.e("RouteFragment", "onActivityCreated()");
        super.onActivityCreated(savedInstanceState);
        placeMapFragment = PlaceMapFragment.getInstance();
        replaceFragment(R.id.map_fragment, placeMapFragment);
        init();

//        Log.e("XXXXXXXXXXXXXXXXXX", "(10 horas, 1 waypoint/seg) obj LatLng    = " + new Gson().toJson(new LatLong(0.468464684684864, 1.654646846848, 212, true)).length() * 10 * 3600 / 1024 /1024 + " MB");
//        Log.e("XXXXXXXXXXXXXXXXXX", "(10 horas, 1 waypoint/seg) obj Location = " + new Gson().toJson(new Location("dadf")).length() * 10 * 3600 / 1024 /1024 + " MB");
    }

    @Override
    public void onDestroy() {
        Log.e("RouteFragment", "onDestroy()");
        super.onDestroy();
        unsubscribe();
    }

    private void init() {
        subscriptions = new CompositeSubscription();
        bitmapHelper = new BitmapHelper();

        ib_play = (ImageView) getView().findViewById(R.id.ib_play);
        ib_stop = (ImageView) getView().findViewById(R.id.ib_stop);

        boolean isPlaying = getPreferenceIsPlaying(getContext());
        if (isPlaying) {
            continueRoute();
        }
        ib_play.setImageResource(isPlaying ? R.drawable.ic_pause : R.drawable.ic_play_arrow);
        ib_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (RxGpsService.isServiceStarted()) {
                    boolean isPlaying = getPreferenceIsPlaying(getContext());

                    if (isPlaying) {
                        stopListenForLocationUpdates();
                    } else {
                        continueRoute();
                    }
                } else {
                    setPreferenceIsPlaying(getContext(), true);
                    startLocationService();
                }
            }
        });
        ib_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopLocationService();
                unsubscribe();
            }
        });
    }

    private void replaceFragment(int id, Fragment fragment) {
        FragmentManager fragmentManager = getChildFragmentManager();
        fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fragmentManager.beginTransaction()
                .replace(id, fragment, fragment.getClass().getSimpleName())
                .commit();
    }

    private void stopChrono() {
        setPreferenceIsPlaying(getContext(), false);
        ib_play.setImageResource(R.drawable.ic_play_arrow);
        if (RxGpsService.isServiceStarted())
            RxGpsService.instance().stopChrono();
    }

    private void playChrono() {
        setPreferenceIsPlaying(getContext(), true);
        ib_play.setImageResource(getPreferenceIsPlaying(getContext()) ? R.drawable.ic_pause : R.drawable.ic_play_arrow);

        if (RxGpsService.isServiceStarted())
            RxGpsService.instance().playChrono();
    }

    private void setPreferenceIsPlaying(Context context, boolean isPlaying) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_STORE, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_IS_PLAYING, isPlaying);
        editor.apply();
    }

    private boolean getPreferenceIsPlaying(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREF_STORE, Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean(PREF_IS_PLAYING, false);
    }

    private void continueRoute() {
        playChrono();
        startListenForLocationUpdates();
    }

    private void stopLocationService() {
        stopChrono();
        RxGpsService.stopService(getContext());
    }

    private void startLocationService() {
        if (!RxGpsService.isServiceStarted()) {
            RxGpsService.builder(getActivity())
                    .withDebugMode(true)
                    .withSpeedMinModeAuto(5 / 3.6f)
                    .withStageDistance(1000)
                    .withDiscardSpeedsAbove(150 / 3.6f)
                    .withMinDistanceTraveled(10)
                    .withPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                    .withInterval(10000)
                    .withFastestInterval(5000)
                    .withDetailedWaypoints(false)
                    .startService(getContext(), new RxGpsService.Listener() {
                        @Override
                        public Notification statusBarNotification(Context context) {
                            int requestID = (int) System.currentTimeMillis();
                            Intent notificationIntent = new Intent(context, MainActivity.class);
                            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            PendingIntent contentIntent = PendingIntent.getActivity(context, requestID, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                            return new NotificationCompat.Builder(getActivity())
                                    .setTicker(getString(R.string.app_name))
                                    .setContentTitle(getString(R.string.app_name))
                                    .setContentText(getString(R.string.route_started))
                                    .setSmallIcon(R.drawable.ic_place)
                                    .setLargeIcon(bitmapHelper.getBitmap(getContext(), R.drawable.ic_place))
                                    .setContentIntent(contentIntent)
                                    .build();
                        }

                        @Override
                        public void onServiceAlreadyStarted() {
                            prepareLocationService();
                        }

                        @Override
                        public void onNavigationModeChanged(boolean isAuto) {
                        }
                    });
        }
    }

    private void prepareLocationService() {
        resetRoutePath();
        playChrono();
        startListenForLocationUpdates();
    }

    private void resetRoutePath() {
        placeMapFragment.drawPathUser(new ArrayList<LatLong>());
        placeMapFragment.resetUserPosition(lastLatLong);
        lastLatLong = new LatLong(0, 0);
    }

    private void stopListenForLocationUpdates() {
        stopChrono();
        subscriptions.clear();
    }

    private void startListenForLocationUpdates() {
        Log.e("RouteFragment", "startListenForLocationUpdates");
        if (RxGpsService.isServiceStarted()) {
            subscriptions = new CompositeSubscription();
            subscriptions.add(RxGpsService.instance().updatesRouteStats()
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                        }
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Action1<RouteStats>() {
                        @Override
                        public void call(RouteStats routeStats) {
                            Log.d(TAG, "Received data: " + routeStats.toString());
                            drawUserPath(routeStats);
                        }
                    }, new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            throwable.printStackTrace();
                            stopLocationService();
                            subscriptions.clear();

                            if (throwable instanceof PermissionDeniedException) {
                                Toast.makeText(getContext(), R.string.permissions_required, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }));
        }
    }

    private void drawUserPath(RouteStats routeStats) {
        if (RxGpsService.isServiceStarted()) {
            List<LatLong> waypoints = routeStats.getLatLongs();
            placeMapFragment.drawPathUser(waypoints);
            if (waypoints != null && !waypoints.isEmpty()) {
                lastLatLong = routeStats.getLastLatLong();
                placeMapFragment.updateUserLocation(routeStats, true);
            }
        }
    }

    private void unsubscribe() {
        if (subscriptions != null)
            subscriptions.unsubscribe();
    }
}