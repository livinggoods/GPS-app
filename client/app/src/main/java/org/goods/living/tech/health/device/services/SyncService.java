package org.goods.living.tech.health.device.services;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.loopj.android.http.TextHttpResponseHandler;

import org.goods.living.tech.health.device.AppController;
import org.goods.living.tech.health.device.BuildConfig;
import org.goods.living.tech.health.device.R;
import org.goods.living.tech.health.device.UI.UpgradeActivity;
import org.goods.living.tech.health.device.models.DataBalance;
import org.goods.living.tech.health.device.models.Stats;
import org.goods.living.tech.health.device.models.User;
import org.goods.living.tech.health.device.utils.Constants;
import org.goods.living.tech.health.device.utils.PermissionsUtils;
import org.goods.living.tech.health.device.utils.ServerRestClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.inject.Inject;
import javax.inject.Singleton;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.entity.StringEntity;

@Singleton
public class SyncService extends BaseService {


    @Inject
    StatsService statsService;

    @Inject
    UserService userService;

    @Inject
    DataBalanceService dataBalanceService;

    Context c;


    ServerRestClient serverRestClient = new ServerRestClient(AppController.getInstance().getString(R

            .string.server_url));

    AtomicBoolean syncRunning = new AtomicBoolean(false);

    long SYNC_SIZE = 200;

    static long CLEANUP_LIMIT = 100;

    boolean syncSuccessful = false;

    Date deviceSyncTime;

    @Inject

    public SyncService() {
        //super(boxStore);

    }

    public void sync(Context context) {

        try {
            //synchronized {}

            c = context;
            if (syncRunning.get()) {
                Crashlytics.log(Log.DEBUG, TAG, "sync running exiting...");
                return;
            }
            Crashlytics.log("Starting sync");
            syncRunning.set(true);
            deviceSyncTime = new Date();


            User user = syncUser();


            //check server version

            if (user.forceUpdate || BuildConfig.VERSION_CODE < user.serverApi) {
                //  show update dialog ?

                Intent intent = new Intent(context, UpgradeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                intent.putExtra(UpgradeActivity.FORCE_UPDATE, user.forceUpdate);
                context.startActivity(intent);


            }
            if (user.forceUpdate) {
                Crashlytics.log("failed sync.forcing update");

                syncRunning.set(false);
                return;
            }

            //only sync if user synced
            if (user.masterId == null) {
                Crashlytics.log("cancel sync stats. masterid does not exist");
                Answers.getInstance().logCustom(new CustomEvent("Sync failed")
                        .putCustomAttribute("Reason", "masterid missing"));
                syncRunning.set(false);
                return;
            }


            syncStats();

            syncDataBalance();


            syncRunning.set(false);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
            syncRunning.set(false);
            Crashlytics.logException(e);
        }

    }

    User syncUser() throws JSONException {

        final User user = userService.getRegisteredUser();
        user.deviceTime = deviceSyncTime;
        //only sync after username is registered on device, else just collect data
        if (user.username == null || user.phone == null) {
            Crashlytics.log("cancel sync. username/phone not registered on device yet");
            Answers.getInstance().logCustom(new CustomEvent("Sync failed")
                    .putCustomAttribute("Reason", "username/phone missing"));
            return user;
        }
        //  String  deviceSyncTimeStr = Utils.getStringTimeStampWithTimezoneFromDate(deviceSyncTime, TimeZone.getTimeZone(Utils.TIMEZONE_UTC));


        StringEntity entity = new StringEntity(user.toJSONObject().toString(), "UTF-8");
        //RequestParams params = new RequestParams(user.toJSONObject());
        //params.setUseJsonStreamer(true);

        serverRestClient.postSync(Constants.URL.USER_CREATE, entity, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Crashlytics.log(Log.DEBUG, TAG, "onFailure " + responseString);

                user.masterId = null;
                user.lastSync = new Date();
                userService.insertUser(user);

                Crashlytics.logException(throwable);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Crashlytics.log(Log.DEBUG, TAG, "onSuccess " + responseString);
                try {
                    JSONObject response = new JSONObject(responseString);
                    String data = response.toString();
                    Crashlytics.log(Log.DEBUG, TAG, "Data : " + data);

                    // If the response is JSONObject instead of JSONArray
                    boolean success = response.has(Constants.STATUS) && response.getBoolean(Constants.STATUS);
                    String msg = response.has(Constants.MESSAGE) ? response.getString(Constants.MESSAGE) : response.getString(Constants.MESSAGE);
                    boolean changeLocationUpdateInterval = false;
                    if (success && response.has(Constants.DATA)) {
                        User updatedUser = new User(response.getJSONObject(Constants.DATA));

                        //         Answers.getInstance().logCustom(new CustomEvent("User Update")
                        //                .putCustomAttribute("Reason", "androidId: " + updatedUser.androidId + " username: " + updatedUser.username));

                        user.masterId = updatedUser.masterId;
                        changeLocationUpdateInterval = user.updateInterval != updatedUser.updateInterval;
                        user.updateInterval = updatedUser.updateInterval;
                        user.serverApi = updatedUser.serverApi;
                        user.forceUpdate = updatedUser.forceUpdate;
                        user.phone = updatedUser.phone;
                        user.balanceCode = updatedUser.balanceCode;

                        user.lastSync = new Date();
                        userService.insertUser(user);


                    } else {
                        Crashlytics.log(Log.ERROR, TAG, "problem syncing user");
                    }

                    if (changeLocationUpdateInterval) {
                        PermissionsUtils.requestLocationUpdates(c, user.updateInterval);
                    }

                } catch (JSONException e) {
                    Log.e(TAG, "", e);
                    Crashlytics.logException(e);
                }
            }
        });


        return user;
    }

    void syncStats() throws JSONException {

        final User user = userService.getRegisteredUser();

        final List<Stats> list = statsService.getUnSyncedRecords(SYNC_SIZE);

        syncSuccessful = false;

        // JSONObject JSONObject = new JSONObject();
        JSONArray JSONArray = new JSONArray();

        for (Stats s : list) {
            s.userMasterId = user.masterId;
            s.deviceTime = deviceSyncTime;
            JSONArray.put(s.toJSONObject());
        }

        StringEntity entity = new StringEntity(JSONArray.toString(), "UTF-8");
        //RequestParams params = new RequestParams(user.toJSONObject());
        //params.setUseJsonStreamer(true);


        serverRestClient.postSync(Constants.URL.STATS_CREATE, entity, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Crashlytics.log(Log.DEBUG, TAG, "Failed");
                Crashlytics.log(Log.DEBUG, TAG, "body " + responseString);
                Crashlytics.log("problem syncing stats: " + responseString);
                Crashlytics.logException(throwable);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Crashlytics.log(Log.DEBUG, TAG, "data " + responseString);
                try {
                    JSONObject response = new JSONObject(responseString);
                    String data = response.toString();
                    Crashlytics.log(Log.DEBUG, TAG, "Data : " + data);

                    // If the response is JSONObject instead of JSONArray
                    boolean success = response.has(Constants.STATUS) && response.getBoolean(Constants.STATUS);
                    String msg = response.has(Constants.MESSAGE) ? response.getString(Constants.MESSAGE) : response.getString(Constants.MESSAGE);

                    if (success) {
                        Crashlytics.log("Successfully synced stats");
                        syncSuccessful = true;
                        for (Stats s : list) {
                            s.synced = true;
                        }
                        statsService.insertRecords(list);
                        //      Answers.getInstance().logCustom(new CustomEvent("Sync stats")
                        //              .putCustomAttribute("Reason", "androidId: " + user.androidId + " username: " + user.username));


                        //cleanup
                        statsService.deleteSyncedRecords(list);//deleteSyncedRecordsOlder(CLEANUP_LIMIT);

                    } else {
                        Crashlytics.log(Log.DEBUG, TAG, "problem syncing stats");
                        Crashlytics.log("problem syncing stats");
                        Crashlytics.logException(new Exception("problem syncing stats "));
                    }


                } catch (JSONException e) {
                    //  e.printStackTrace();
                    Log.e(TAG, "", e);
                    Crashlytics.logException(e);
                }
            }
        });


        List<Stats> listMore = statsService.getUnSyncedRecords(SYNC_SIZE);
        if (listMore.size() > 0 && syncSuccessful) {
            syncStats();
        }

    }

    void syncDataBalance() throws JSONException {

        final User user = userService.getRegisteredUser();

        final List<DataBalance> list = dataBalanceService.getUnSyncedRecords(SYNC_SIZE);

        syncSuccessful = false;

        // JSONObject JSONObject = new JSONObject();
        JSONArray JSONArray = new JSONArray();

        for (DataBalance d : list) {
            d.userMasterId = user.masterId;
            JSONArray.put(d.toJSONObject());
        }

        StringEntity entity = new StringEntity(JSONArray.toString(), "UTF-8");
        //RequestParams params = new RequestParams(user.toJSONObject());
        //params.setUseJsonStreamer(true);


        serverRestClient.postSync(Constants.URL.STATS_CREATE, entity, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                Crashlytics.log(Log.DEBUG, TAG, "Failed");
                Crashlytics.log(Log.DEBUG, TAG, "body " + responseString);
                Crashlytics.log("problem syncing databalance: " + responseString);
                Crashlytics.logException(throwable);
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                Crashlytics.log(Log.DEBUG, TAG, "data " + responseString);
                try {
                    JSONObject response = new JSONObject(responseString);
                    String data = response.toString();
                    Crashlytics.log(Log.DEBUG, TAG, "Data : " + data);

                    // If the response is JSONObject instead of JSONArray
                    boolean success = response.has(Constants.STATUS) && response.getBoolean(Constants.STATUS);
                    String msg = response.has(Constants.MESSAGE) ? response.getString(Constants.MESSAGE) : response.getString(Constants.MESSAGE);

                    if (success) {
                        Crashlytics.log("Successfully synced databalance");
                        syncSuccessful = true;

                        for (DataBalance d : list) {
                            d.synced = true;
                        }
                        dataBalanceService.insertRecords(list);
                        //      Answers.getInstance().logCustom(new CustomEvent("Sync stats")
                        //              .putCustomAttribute("Reason", "androidId: " + user.androidId + " username: " + user.username));


                        //cleanup
                        dataBalanceService.deleteSyncedRecords(list);//deleteSyncedRecordsOlder(CLEANUP_LIMIT);

                    } else {
                        Crashlytics.log(Log.DEBUG, TAG, "problem syncing stats");
                        Crashlytics.log("problem syncing stats");
                        Crashlytics.logException(new Exception("problem syncing stats "));
                    }


                } catch (JSONException e) {
                    //  e.printStackTrace();
                    Log.e(TAG, "", e);
                    Crashlytics.logException(e);
                }
            }
        });


        List<DataBalance> listMore = dataBalanceService.getUnSyncedRecords(SYNC_SIZE);
        if (listMore.size() > 0 && syncSuccessful) {
            syncDataBalance();
        }

    }
}
