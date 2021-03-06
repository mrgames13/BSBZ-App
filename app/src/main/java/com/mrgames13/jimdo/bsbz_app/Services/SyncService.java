package com.mrgames13.jimdo.bsbz_app.Services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.mrgames13.jimdo.bsbz_app.App.LogInActivity;
import com.mrgames13.jimdo.bsbz_app.App.MainActivity;
import com.mrgames13.jimdo.bsbz_app.CommonObjects.Account;
import com.mrgames13.jimdo.bsbz_app.R;
import com.mrgames13.jimdo.bsbz_app.Utils.AccountUtils;
import com.mrgames13.jimdo.bsbz_app.Utils.NotificationUtils;
import com.mrgames13.jimdo.bsbz_app.Utils.ServerMessagingUtils;
import com.mrgames13.jimdo.bsbz_app.Utils.StorageUtils;

import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class SyncService extends Service {
	
	//Konstanten

	//Variablen als Objekte
	private ConnectivityManager cm;
    private ServerMessagingUtils serverMessagingUtils;
    private Context context;
    private Handler handler;
    private NotificationManager nm;
    private Resources res;
    private NotificationUtils nu;
    private StorageUtils su;
    private AccountUtils au;
    private Account current_account;

    //Variablen
	private String klasse = "";
	private String username = "";
	private boolean update = false;
	private boolean sync;
	private String result;
	boolean show_notifications = true;

	//Interfaces
	public interface onSyncFinishedListener {
		void onSyncFinished();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

        //Kontext initialisieren
		context = getApplicationContext();

		//Resourcen initilisieren
		res = getResources();

        //StoratgeUtils initialisieren
        su = new StorageUtils(this, res);

        //AccountUtils initialisieren
        au = new AccountUtils(su);

        //Aktueller Account laden
        current_account = au.getLastUser();

        //NotificationUtils initialisieren
        nu = new NotificationUtils(this);

		//ServerMessagingUtils initialisieren
		cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
		serverMessagingUtils = new ServerMessagingUtils(cm, context);

		//NotificationManager initialisieren
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

		//Handler initialisieren
		handler = new Handler();

        try{
            final String data = intent.getStringExtra("Data");
            if(data != null) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if(serverMessagingUtils.isInternetAvailable()) {
                            //Snchronisieren
                            sync(data);
                            //SyncTime eintragen
                            EnterLastSyncTime();
                        }
                        //MainActivity refreshen
                        MainActivity.isRunning = false;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                try{ MainActivity.syncFinishedListener.onSyncFinished(); } catch(Exception e) {}
                            }
                        });
                        stopSelf();
                    }
                });
                t.start();
            } else {
                show_notifications = su.getBoolean("send_notifications", true);
                klasse = current_account.getForm();
                username = current_account.getUsername();
                update = su.getBoolean("UpdateAvailable", false);
                sync = su.getBoolean("Sync", true);

                if(!sync) Toast.makeText(context, getResources().getString(R.string.account_sync_blocked), Toast.LENGTH_LONG).show();

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        if(!update && sync && serverMessagingUtils.isInternetAvailable()) {
                            //Snchronisieren
                            result = "";
                            try{
                                result = serverMessagingUtils.sendRequest(null, "name="+ URLEncoder.encode(username, "UTF-8")+"&command=sync&class="+URLEncoder.encode(klasse, "UTF-8"));
                            } catch(Exception e) {}
                            sync(result);
                            //SyncTime eintragen
                            EnterLastSyncTime();
                        }
                        //MainActivity refreshen
                        MainActivity.isRunning = false;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                try{ MainActivity.syncFinishedListener.onSyncFinished(); } catch(Exception e) {}
                            }
                        });
                        stopSelf();
                    }
                });
                t.start();
            }
        } catch(Exception e) {}
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public IBinder onBind(Intent i) {
		return null;
	}

	public void sync(String result) {
		try{
			if(!result.equals("") && !result.contains("Error") && !result.contains("Warning")) {
                //Result auseinandernehmen
                final int index1 = result.indexOf("#", 0);
                final int index2 = result.indexOf("#", index1 +1);
                final int index3 = result.indexOf("#", index2 +1);
                final int index4 = result.indexOf("#", index3 +1);
                //Stundenplan
                    String timetable_str = result.substring(0, index1);
                    //Stundenplan vergleichen und ggf. eine Nachricht in der Statusleiste anzeigen
                    compareTimetables(timetable_str);
                    //in SharedPreferences eintragen
                    su.putString("Timetables", timetable_str);
                    //Daten auseinandernehmen
                    int i1 = timetable_str.indexOf(";", 0);
                    int i2 = timetable_str.indexOf(";", i1 +1);
                    int i3 = timetable_str.indexOf(";", i2 +1);
                    int i4 = timetable_str.indexOf(";", i3 +1);
                    String MO = timetable_str.substring(0, i1);
                    String DI = timetable_str.substring(i1 +1, i2);
                    String MI = timetable_str.substring(i2 +1, i3);
                    String DO = timetable_str.substring(i3 +1, i4);
                    String FR = timetable_str.substring(i4 +1);
                    //Stundenplan in SharedPreferences eintragen
                    su.addTimetable(klasse, MO, DI, MI, DO, FR);
                //Klassenarbeiten
                    String classtests_str = result.substring(index1 +1, index2);
                    //Classtests vergleichen und ggf. eine Nachricht in die Statusleiste senden
                    compareClasstests(classtests_str);
                    //Alte Klasstests in den SharedPreferences durch neue ersetzen
                    su.putString("Classtests", classtests_str);
                    //Daten auseinandernehmen
                    ArrayList<String> arraylist = new ArrayList<String>();
                    //In einzelne Klassenarbeiten unterteilen
                    while(classtests_str.contains("|")) {
                        int index = classtests_str.indexOf("|");
                        arraylist.add(classtests_str.substring(0, index));
                        classtests_str = classtests_str.substring(index +1);
                    }
                    //Klassenarbeiten in die SharedPreferences eintragen
                    int i = 0;
                    su.deleteAllClasstests();
                    for(String c_classtest : arraylist) {
                        //Indexe finden
                        int index1_1 = c_classtest.indexOf("~");
                        int index2_2 = c_classtest.indexOf("~", index1_1 +1);
                        int index3_3 = c_classtest.indexOf("~", index2_2 +1);
                        int index4_4 = c_classtest.indexOf("~", index3_3 +1);
                        //String zerteilen
                        int c_classtest_id = i +1;
                        String c_classtest_subject = c_classtest.substring(0, index1_1);
                        String c_classtest_description = c_classtest.substring(index1_1 +1, index2_2);
                        String c_classtest_writer = c_classtest.substring(index2_2 +1, index3_3);
                        String c_classtest_date = c_classtest.substring(index3_3 +1, index4_4);
                        String c_classtest_receiver = c_classtest.substring(index4_4 +1);
                        //Classtest-Objekt einspeichern
                        su.addClasstest(c_classtest_id, c_classtest_subject, c_classtest_description, c_classtest_receiver, c_classtest_writer, c_classtest_date);
                        i++;
                    }
                    su.setClasstestCount(i);
                //Hausaufgaben
                    String homeworks_str = result.substring(index2 +1, index3);
                    //Homeworks vergleichen und ggf. eine Nachricht in die Statusleiste senden
                    compareHomeworks(homeworks_str);
                    //Alte Homeworks in den SharedPreferences durch neue ersetzen
                    su.putString("Homeworks", homeworks_str);
                    //Daten auseinandernehmen
                    arraylist = new ArrayList<String>();
                    //In einzelne Hausaufgaben unterteilen
                    while(homeworks_str.contains("|")) {
                        int index = homeworks_str.indexOf("|");
                        arraylist.add(homeworks_str.substring(0, index));
                        homeworks_str = homeworks_str.substring(index +1);
                    }
                    //Hausaufgaben in die SharedPreferences eintragen
                    i = 0;
                    su.deleteAllHomeworks();
                    for(String c_homework : arraylist) {
                        //Indexe finden
                        int index1_1 = c_homework.indexOf("~");
                        int index2_2 = c_homework.indexOf("~", index1_1 +1);
                        int index3_3 = c_homework.indexOf("~", index2_2 +1);
                        int index4_4 = c_homework.indexOf("~", index3_3 +1);
                        //String zerteilen
                        int c_homework_id = i +1;
                        String c_homework_subject = c_homework.substring(0, index1_1);
                        String c_homework_description = c_homework.substring(index1_1 +1, index2_2);
                        String c_homework_writer = c_homework.substring(index2_2 +1, index3_3);
                        String c_homework_date = c_homework.substring(index3_3 +1, index4_4);
                        String c_homework_receiver = c_homework.substring(index4_4 +1);
                        //Homework-Objekt einspeichern
                        su.addHomework(c_homework_id, c_homework_subject, c_homework_description, c_homework_receiver, c_homework_writer, c_homework_date);
                        i++;
                    }
                    su.setHomeworkCount(i);
                //Termine
                    String termine_str = result.substring(index3 +1, index4);
                    //Events vergleichen und ggf. eine Nachricht in die Statusleiste senden
                    compareEvents(termine_str);
                    //Alte Events in den SharedPreferences durch neue ersetzen
                    su.putString("Events", termine_str);
                    //Daten auseinandernehmen
                    arraylist = new ArrayList<String>();
                    //In einzelne Events unterteilen
                    while(termine_str.contains("|")) {
                        int index = termine_str.indexOf("|");
                        arraylist.add(termine_str.substring(0, index));
                        termine_str = termine_str.substring(index +1);
                    }
                    //Events in die SharedPreferences eintragen
                    i = 0;
                    su.deleteAllEvents();
                    for(String c_event : arraylist) {
                        //Indexe finden
                        int index1_1 = c_event.indexOf("~");
                        int index2_2 = c_event.indexOf("~", index1_1 +1);
                        int index3_3 = c_event.indexOf("~", index2_2 +1);
                        int index4_4 = c_event.indexOf("~", index3_3 +1);
                        //String zerteilen
                        int c_homework_id = i +1;
                        String c_event_subject = c_event.substring(0, index1_1);
                        String c_event_description = c_event.substring(index1_1 +1, index2_2);
                        String c_event_writer = c_event.substring(index2_2 +1, index3_3);
                        String c_event_date = c_event.substring(index3_3 +1, index4_4);
                        String c_event_receiver = c_event.substring(index4_4 +1);
                        //Event-Objekt einspeichern
                        su.addEvent(c_homework_id, c_event_subject, c_event_description, c_event_receiver, c_event_writer, c_event_date);
                        i++;
                    }
                    su.setEventCount(i);
                //News
                    String news_str = result.substring(index4 +1).trim();
                    //News vergleichen und ggf. eine Nachricht in die Statusleiste senden
                    compareNews(news_str);
                    //Alte News in den SharedPreferences durch neue ersetzen
                    su.putString("News", news_str);
                    //Daten auseinandernehmen
                    arraylist = new ArrayList<String>();
                    //In einzelne News unterteilen
                    while(news_str.contains("|")) {
                        int index = news_str.indexOf("|");
                        arraylist.add(news_str.substring(0, index));
                        news_str = news_str.substring(index +1);
                    }
                    //News in die SharedPreferences eintragen
                    i = 1;
                    su.deleteAllNews();
                    for(String c_new : arraylist) {
                        //Indexe finden
						int index1_1 = c_new.indexOf("~");
                        int index2_2 = c_new.indexOf("~", index1_1 +1);
                        int index3_3 = c_new.indexOf("~", index2_2 +1);
                        int index4_4 = c_new.indexOf("~", index3_3 +1);
                        int index5_5 = c_new.indexOf("~", index4_4 +1);
                        int index6_6 = c_new.indexOf("~", index5_5 +1);
                        //String zerteilen
                        int c_new_id = i;
                        String c_new_subject = c_new.substring(0, index1_1);
                        String c_new_description = c_new.substring(index1_1 +1, index2_2);
                        String c_new_writer = c_new.substring(index2_2 +1, index3_3);
                        int c_new_state = Integer.parseInt(c_new.substring(index3_3 +1, index4_4));
                        String c_new_activation_date = c_new.substring(index4_4 +1, index5_5);
                        String c_new_expiration_date = c_new.substring(index5_5 +1, index6_6);
                        String c_new_receiver = c_new.substring(index6_6 +1);
                        //News-Objekt einspeichern
                        su.addNew(c_new_id, c_new_state, c_new_subject, c_new_description, c_new_receiver, c_new_writer, c_new_activation_date, c_new_expiration_date);
					    i++;
                    }
                    su.setNewsCount(i);
			}
		} catch(Exception e) {
            Log.e("BSBZ-App", "Error occured", e);
		}
	}

	private void compareTimetables(String timetable) {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String timetable_old = prefs.getString("Timetables", timetable);

		timetable_old = timetable_old.trim();
		timetable = timetable.trim();
		
		if(!timetable_old.equals(timetable) && show_notifications) {
			//Notification senden
			Intent i = new Intent(this, LogInActivity.class);
			i.putExtra("Confirm", "Timetable");

            nu.displayNotification(res.getString(R.string.app_name), res.getString(R.string.timetable_changings), nu.ID_COMP_TIMETABLES, i, nu.PRIORITY_LOW, 0, new long[0]);
		}
	}

	private void compareClasstests(String classtests) {
		String classtests_old = su.getString("Classtests", classtests);

		classtests_old = classtests_old.trim();
		classtests = classtests.trim();
		
		if(!classtests_old.equals(classtests) && show_notifications) {
			//Notification senden
			Intent i = new Intent(this, LogInActivity.class);
			i.putExtra("Confirm", "Classtests");
			PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), i, 0);

            nu.displayNotification(res.getString(R.string.app_name), res.getString(R.string.classtest_changings), nu.ID_COMP_CLASSTESTS, i, nu.PRIORITY_LOW, 0, new long[0]);
		}
	}

	private void compareHomeworks(String homeworks) {
		String homeworks_old = su.getString("Homeworks", homeworks);

		homeworks_old = homeworks_old.trim();
		homeworks = homeworks.trim();
		
		if(!homeworks_old.equals(homeworks) && show_notifications) {
			//Notification senden
			Intent i = new Intent(this, LogInActivity.class);
			i.putExtra("Confirm", "Homework");
			PendingIntent pi = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), i, 0);

            nu.displayNotification(res.getString(R.string.app_name), res.getString(R.string.homework_changings), nu.ID_COMP_HOMEWORKS, i, nu.PRIORITY_LOW, 0, new long[0]);
		}
	}
	
	private void compareEvents(String events) {
		String events_old = su.getString("Events", events);

		events_old = events_old.trim();
		events = events.trim();
		
		if(!events_old.equals(events) && show_notifications) {
			//Notification senden
			Intent i = new Intent(this, LogInActivity.class);
			i.putExtra("Confirm", "Events");

            nu.displayNotification(res.getString(R.string.app_name), res.getString(R.string.event_changings), nu.ID_COMP_EVENTS, i, nu.PRIORITY_LOW, 0, new long[0]);
		}
	}

	private void compareNews(String news) {
		String news_old = su.getString("News", news);

		news_old = news_old.trim();
		news = news.trim();

		if(!news_old.equals(news) && show_notifications) {
			//Notification senden
			Intent i = new Intent(this, LogInActivity.class);
			i.putExtra("Confirm", "News");

            nu.displayNotification(res.getString(R.string.app_name), res.getString(R.string.news_changings), nu.ID_COMP_NEWS, i, nu.PRIORITY_LOW, 0, new long[0]);
		}
	}
	
	private void EnterLastSyncTime() {
		//Letzte Syncronisationszeit eintragen
		//Time in Timestamp umwandeln
		Date date = new Date(System.currentTimeMillis());
		String sync_time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.LONG, Locale.GERMANY).format(date).substring(0, 16);
		//In Shared Preferences eintragen
        su.putString("SyncTime", sync_time);
	}
}