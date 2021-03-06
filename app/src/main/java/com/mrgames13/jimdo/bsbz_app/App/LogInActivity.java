package com.mrgames13.jimdo.bsbz_app.App;

import android.Manifest;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;
import com.mrgames13.jimdo.bsbz_app.CommonObjects.Account;
import com.mrgames13.jimdo.bsbz_app.FirebaseMessaging.FCM_Instance_ID_Service;
import com.mrgames13.jimdo.bsbz_app.R;
import com.mrgames13.jimdo.bsbz_app.Services.SyncService;
import com.mrgames13.jimdo.bsbz_app.Utils.AccountUtils;
import com.mrgames13.jimdo.bsbz_app.Utils.ServerMessagingUtils;
import com.mrgames13.jimdo.bsbz_app.Utils.StorageUtils;

import java.net.URLEncoder;
import java.util.ArrayList;

public class LogInActivity extends AppCompatActivity {

    //Konstanten festlegen
    private static final String androidversion = Build.VERSION.RELEASE;
    private final int REQUEST_CODE_PERMISSION_READ_PHONE_STATE = 488;

    //Variablen als Objekte
    private Toolbar toolbar;
    private Resources res;
    private ConnectivityManager cm;
    private ProgressBar pb;
    private EditText et_username;
    private EditText et_password;
    private CheckBox cb_keep_logged_in;

    //Variablen
    private boolean pressedOnce;
    private String result;
    public static String current_version = "";
    public static String autologin = "";
    private Account current_account;

    //Utils-Pakete
    private ServerMessagingUtils serverMessagingUtils;
    private AccountUtils au;
    private StorageUtils su;

    @Override
    public void onStart() {
        super.onStart();

        //Daten von den SharedPreferences abrufen
        String layout = su.getString("Layout", res.getString(R.string.bsbz_layout_orange));
        String color = "#ea690c";
        if(layout.equals("0")) {
            color = "#ea690c";
        } else if(layout.equals("1")) {
            color = "#000000";
        } else if(layout.equals("2")) {
            color = "#3ded25";
        } else if(layout.equals("3")) {
            color = "#ff0000";
        } else if(layout.equals("4")) {
            color = "#0000ff";
        } else if(layout.equals("5")) {
            color = "#00007f";
        }
        toolbar.setBackgroundDrawable(new ColorDrawable(Color.parseColor(color)));
        getSupportActionBar().setTitle(res.getString(R.string.title_activity_log_in));

        if(Build.VERSION.SDK_INT >= 21) {
            Window window = getWindow();
            window.setStatusBarColor(MainActivity.darkenColor(Color.parseColor(color)));
        }

        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancel(1);
        nm.cancel(2);
        nm.cancel(3);

        //Aktionen ermitteln und ausführen
        try {
            String action = getIntent().getExtras().getString("Action");
            if(action.equals("deleted account")) {
                Toast.makeText(LogInActivity.this, res.getString(R.string.account_deleted_successfully), Toast.LENGTH_LONG).show();
                getIntent().removeExtra("Action");
            } else if(action.equals("not deleted account")) {
                Toast.makeText(LogInActivity.this, res.getString(R.string.account_deletion_failed), Toast.LENGTH_LONG).show();
                getIntent().removeExtra("Action");
            } else if(action.equals("changed password")) {
                Toast.makeText(LogInActivity.this, res.getString(R.string.password_changed_successfully), Toast.LENGTH_LONG).show();
                getIntent().removeExtra("Action");
            } else if(action.equals("not changed password")) {
                Toast.makeText(LogInActivity.this, res.getString(R.string.password_changing_failed), Toast.LENGTH_LONG).show();
                getIntent().removeExtra("Action");
            }
        } catch(Exception e) {}
    }

    @Override
    protected void onPause() {
        super.onPause();
        autologin = "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!autologin.equals("")) autoLogin();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Resourcen initialisieren
        res = getResources();

        //StorageUtils initialisieren
        su = new StorageUtils(this, res);

        //Theme aus den Shared Preferences auslesen
        String theme = su.getString("AppTheme", "0");
        if(theme.equals("0")) {
            MainActivity.AppTheme = 0;
            setTheme(R.style.FirstTheme);
        } else if(theme.equals("1")) {
            MainActivity.AppTheme = 1;
            setTheme(R.style.SecondTheme);
        }

        setContentView(R.layout.activity_log_in);

        //Toolbar aufsetzen
        toolbar = findViewById(R.id.toolbar_login);
        setSupportActionBar(toolbar);

        //AccountUtils initialisieren
        au = new AccountUtils(su);

        //Account laden
        current_account = au.getLastUser();

        try { current_version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (NameNotFoundException e1) {}

        //Ids herausfinden
        //Edit Texte
        et_username = findViewById(R.id.LogIn_User_Name);
        et_password = findViewById(R.id.LogIn_Password);
        cb_keep_logged_in = findViewById(R.id.angemeldet_bleiben);
        //Buttons
        final Button login = findViewById(R.id.LogIn_LogIn);
        //Button-Ausgrau-Mechanismus aufsetzen
        et_username.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                login.setEnabled(s.length() > 0 && et_password.getText().length() > 0);
                cb_keep_logged_in.setEnabled(s.length() > 0 && et_password.getText().length() > 0);
            }
        });
        et_password.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void afterTextChanged(Editable s) {
                login.setEnabled(s.length() > 0 && et_username.getText().length() > 0);
                cb_keep_logged_in.setEnabled(s.length() > 0 && et_username.getText().length() > 0);
            }
        });
        et_password.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                //Strings auslesen und Leerzeichen durch '+' ersetzen
                final String username = et_username.getText().toString();
                final String password_string = et_password.getText().toString();
                final boolean ab = cb_keep_logged_in.isChecked();
                //Einloggen
                if(!username.equals("") && !password_string.equals("")) {
                    LogIn(username, password_string, androidversion, current_version, ab);
                }
                return true;
            }
        });
        login.setEnabled(false);
        cb_keep_logged_in.setEnabled(false);

        login.requestFocus();

        //LogIn Button
        login.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //Strings auslesen und Leerzeichen durch '+' ersetzen
                final String username = et_username.getText().toString();
                final String password_string = et_password.getText().toString();
                final boolean ab = cb_keep_logged_in.isChecked();
                //Einloggen
                if(!username.equals("") && !password_string.equals("")) {
                    LogIn(username, password_string, androidversion, current_version, ab);
                }
            }
        });
        //Registrieren
        Button register = findViewById(R.id.LogIn_Registrieren);
        register.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LogInActivity.this, RegistrationActivity.class));
            }
        });
        //Passwort vergessen
        final TextView forgot_password = findViewById(R.id.forgot_password);
        forgot_password.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(LogInActivity.this, res.getString(R.string.reset_password_m), Toast.LENGTH_LONG).show();
                Intent i = new Intent(LogInActivity.this, WebActivity.class);
                i.putExtra("Title", res.getString(R.string.support));
                i.putExtra("Website", su.getString("SupportUrl", res.getString(R.string.link_password_reset)));
                startActivity(i);
            }
        });

        //ServerMessagingUtils initialisieren
        cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        serverMessagingUtils = new ServerMessagingUtils(cm, LogInActivity.this);
        serverMessagingUtils.checkConnection(findViewById(R.id.container));

        //ProgressBar finden
        pb = findViewById(R.id.login_in_progress);

        if(!current_account.getUsername().equals(res.getString(R.string.guest))) {
            et_username.setText(current_account.getUsername());
            et_password.requestFocus();
        }

        //Auf Updates prüfen
        if(serverMessagingUtils.isInternetAvailable()) checkVersionAndServerState(current_account.getUsername(), false);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK) {
            if(!pressedOnce) {
                Toast.makeText(LogInActivity.this, res.getString(R.string.press_again_to_exit_app), Toast.LENGTH_SHORT).show();
                pressedOnce = true;
                //Handler zur Wiederherstellung erstellen
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        pressedOnce = false;
                    }
                }, 2000);
            } else {
                pressedOnce = false;
                onBackPressed();
            }
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_in, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if(id == R.id.action_gastmodus) {
            //startActivity(new Intent(LogInActivity.this,MainActivity.class));
            startActivity(new Intent(LogInActivity.this, MainActivity.class));
            //Animierter Activitywechsel starten
            overridePendingTransition(R.anim.in_login, R.anim.out_login);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void LogIn(final String username, final String password, final String androidversion, final String appversion, final boolean ab) {
        //ProgressBar sichtbar machen
        pb.setVisibility(View.VISIBLE);
        //Inhalt der Variablen in die Komponenten übertragen
        EditText et_username = findViewById(R.id.LogIn_User_Name);
        EditText et_password = findViewById(R.id.LogIn_Password);
        et_username.setText(username);
        et_password.setText(password);
        //Komponenten unveränderbar machen
        enableComponents(false);

        if(serverMessagingUtils.isInternetAvailable()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        result = serverMessagingUtils.sendRequest(findViewById(R.id.container), "name="+URLEncoder.encode(username.trim(), "UTF-8")+"&command=findaccount&password="+URLEncoder.encode(password.trim(), "UTF-8")+"&androidversion="+URLEncoder.encode(androidversion, "UTF-8")+"&appversion="+URLEncoder.encode(appversion, "UTF-8")+"&fcm_token="+URLEncoder.encode(su.getString(FCM_Instance_ID_Service.token_preference_key, "no_token"), "UTF-8"));
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(result.equals("account is not existing")) {
                                    Toast.makeText(LogInActivity.this, res.getString(R.string.account_do_not_exist), Toast.LENGTH_SHORT).show();
                                    pb.setVisibility(View.GONE);
                                    //Komponenten sichtbar machen
                                    enableComponents(true);
                                } else if(result.equals("password wrong")) {
                                    Toast.makeText(LogInActivity.this, res.getString(R.string.wrong_password), Toast.LENGTH_SHORT).show();
                                    pb.setVisibility(View.GONE);
                                    //Komponenten sichtbar machen
                                    enableComponents(true);
                                } else {
                                    try{
                                        int index1 = result.indexOf(",");
                                        int index2 = result.indexOf(",", index1 +1);
                                        String klasse = result.substring(0, index1);
                                        String rights = result.substring(index1 +1, index2);
                                        String account_state = result.substring(index2 +1);
                                        if(account_state.equals("1")) {
                                            //Synchronisation aktivieren
                                            su.putBoolean("Sync", true);
                                            //Angemeldet bleiben aktivieren
                                            su.putBoolean("Angemeldet bleiben", ab);
                                            //Topic deabonniren
                                            try{ FirebaseMessaging.getInstance().unsubscribeFromTopic(su.getString("Klasse", "")); } catch(Exception e1) {}
                                            //Accountdaten in die SharedPreferences eintragen
                                            if(rights.equals("wants to be a teacher") || rights.equals("wants_to_be_a_teacher")) rights = "student";
                                            if(rights.equals("wants to be a classspeaker") || rights.equals("wants_to_be_a_classspeaker")) rights = "student";
                                            au.LogIn(username, password, klasse, rights);
                                            //Topic abonnieren
                                            try{ FirebaseMessaging.getInstance().subscribeToTopic(klasse); } catch(Exception e2) {}
                                            //Toast ausgeben
                                            Toast.makeText(LogInActivity.this, "LogIn erfolgreich", Toast.LENGTH_SHORT).show();
                                            //Synchronisation initiieren
                                            startService(new Intent(LogInActivity.this, SyncService.class));
                                            //Activities starten
                                            try {
                                                String extra = getIntent().getStringExtra("Confirm");
                                                if(extra.equals("Timetable")) {
                                                    startActivity(new Intent(LogInActivity.this, TimeTableActivity.class));
                                                } else if(extra.equals("Classtests")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Jahresplan");
                                                    startActivity(i);
                                                } else if(extra.equals("Homework")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Jahresplan");
                                                    startActivity(i);
                                                } else if(extra.equals("Events")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Jahresplan");
                                                    startActivity(i);
                                                } else if(extra.equals("Today")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Today");
                                                    startActivity(i);
                                                } else {
                                                    startActivity(new Intent(LogInActivity.this, MainActivity.class));
                                                }
                                            } catch(NullPointerException e2) {}
                                            startActivity(new Intent(LogInActivity.this, MainActivity.class));
                                            //Animierter Activitywechsel starten
                                            overridePendingTransition(R.anim.in_login, R.anim.out_login);
                                            finish();
                                        } else if(account_state.equals("2")) {
                                            pb.setVisibility(View.GONE);
                                            //Synchronisation aktivieren
                                            su.putBoolean("Sync", true);
                                            //Nachricht an den Nutzer ausgeben, dass sein Account gesperrt wurde
                                            Toast.makeText(LogInActivity.this, res.getString(R.string.account_locked), Toast.LENGTH_LONG).show();
                                            //Komponenten sichtbar machen
                                            enableComponents(true);
                                        } else if(account_state.equals("3")) {
                                            pb.setVisibility(View.GONE);
                                            //Nachricht an den Nutzer ausgeben, dass seine Synchronisation blockiert wurde
                                            Toast.makeText(LogInActivity.this, res.getString(R.string.account_sync_blocked), Toast.LENGTH_LONG).show();
                                            //Synchronisation deaktivieren
                                            su.putBoolean("Sync", false);
                                            //Angemeldet bleiben (de)aktivieren
                                            su.putBoolean("Angemeldet bleiben", ab);
                                            //Accountdaten in die SharedPreferences eintragen
                                            if(rights.equals("wants to be a teacher") || rights.equals("wants_to_be_a_teacher")) rights = "student";
                                            if(rights.equals("wants to be a classspeaker") || rights.equals("wants_to_be_a_classspeaker")) rights = "student";
                                            au.LogIn(username, password, klasse, rights);
                                            //Activities starten
                                            try {
                                                String extra = getIntent().getStringExtra("Confirm");
                                                if(extra.equals("Timetable")) {
                                                    startActivity(new Intent(LogInActivity.this, TimeTableActivity.class));
                                                } else if(extra.equals("Classtests")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Jahresplan");
                                                    startActivity(i);
                                                } else if(extra.equals("Homework")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Jahresplan");
                                                    startActivity(i);
                                                } else if(extra.equals("Events")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Jahresplan");
                                                    startActivity(i);
                                                } else if(extra.equals("Today")) {
                                                    Intent i = new Intent(LogInActivity.this, MainActivity.class);
                                                    i.putExtra("Open", "Today");
                                                    startActivity(i);
                                                } else {
                                                    startActivity(new Intent(LogInActivity.this, MainActivity.class));
                                                }
                                            } catch(NullPointerException e2) {}
                                            startActivity(new Intent(LogInActivity.this, MainActivity.class));
                                            //Animierter Activitywechsel starten
                                            overridePendingTransition(R.anim.in_login, R.anim.out_login);
                                            finish();
                                        } else {
                                            pb.setVisibility(View.GONE);
                                            Toast.makeText(LogInActivity.this, res.getString(R.string.error_occured_try_again), Toast.LENGTH_SHORT).show();
                                            Log.e("BSBZ-App", "Error: Accountstate not recognized");
                                        }
                                    } catch(Exception e) {
                                        pb.setVisibility(View.GONE);
                                    }
                                }
                            }
                        });
                    } catch(Exception e) {}
                }
            }).start();
        } else {
            ArrayList<Account> allUsers = au.getAllUsers();
            for(Account user : allUsers) {
                if(user.getUsername().equals(username) && user.getPassword().equals(password)) {
                    au.LogIn(username, password, user.getForm(), user.getRightsString());
                    startActivity(new Intent(LogInActivity.this, MainActivity.class));
                    return;
                }
            }
            Account current_account = au.getLastUser();
            if(current_account.getUsername().equals(username) && current_account.getPassword().equals(password)) {
                au.LogIn(username, password, current_account.getForm(), current_account.getRightsString());
                startActivity(new Intent(LogInActivity.this, MainActivity.class));
                return;
            }
            Toast.makeText(this, res.getString(R.string.no_offline_account_found), Toast.LENGTH_SHORT).show();
            //Komponenten sichtbar machen
            enableComponents(true);
            pb.setVisibility(View.GONE);
        }
    }

    private void checkFirstStart() {
        if(!su.getBoolean("appAlreadyStarted", false)) {
            //Aufgaben beim ersten Start

            //Erfolgreiches Ausführen aller Aufgaben beim ersten Start in die SharedPreferences eintragen
            su.putBoolean("appAlreadyStarted", true);
        } else {
            if(su.getBoolean("Angemeldet bleiben", false) && serverMessagingUtils.isInternetAvailable()) autoLogin();
        }
    }

    private void checkMarshmellowPermissions() {
        //Permission 'READ_PHONE_STATE' abfragen
        if(!(ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(LogInActivity.this, new String[] {Manifest.permission.READ_PHONE_STATE}, REQUEST_CODE_PERMISSION_READ_PHONE_STATE);
        }
    }

    public void checkVersionAndServerState(final String username, final boolean showUpdateDialog) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String name = username;
                if(username.equals("")) name = res.getString(R.string.guest);
                try {
                    result = serverMessagingUtils.sendRequest(findViewById(R.id.container), "name="+URLEncoder.encode(name, "UTF-8")+"&command=getserverinfo");
                    //Result auseinandernehmen
                    int index1 = result.indexOf(",");
                    int index2 = result.indexOf(",", index1 +1);
                    int index3 = result.indexOf(",", index2 +1);
                    int index4 = result.indexOf(",", index3 +1);
                    int index5 = result.indexOf(",", index4 +1);
                    String client_name = result.substring(0, index1);
                    String server_state = result.substring(index1 +1, index2);
                    final String app_version = result.substring(index2 +1, index3);
                    String adminconsole_version = result.substring(index3 +1, index4);
                    String supporturl = result.substring(index4 +1, index5);
                    String owners = result.substring(index5 +1);
                    //Serverstatus prüfen
                    if(server_state.equals("1")) {
                        Log.i("BSBZ-App", "The server is online!");
                        //AppVersion prüfen
                        if(!app_version.equals(current_version)) {
                            MainActivity.isUpdateAvailable = true;
                            if(current_account.getRights() == Account.RIGHTS_TEAM) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                        dialog.setMessage(res.getString(R.string.update_team));
                                        dialog.setPositiveButton(res.getString(R.string.open), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                checkFirstStart();
                                                checkMarshmellowPermissions();
                                                //Dialog schließen
                                                dialog.dismiss();
                                            }
                                        });
                                        dialog.setNegativeButton(res.getString(R.string.close), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                //App beenden und Dialog schließen
                                                finish();
                                                dialog.dismiss();
                                            }
                                        });
                                        dialog.setCancelable(false);
                                        dialog.show();
                                    }
                                });
                                //In SharedPreferences eintragen
                                su.putBoolean("UpdateAvailable", false);
                                su.putString("SupportUrl", supporturl);
                            } else {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                        dialog.setMessage(res.getString(R.string.update_available_1) + app_version + res.getString(R.string.update_available_2));
                                        dialog.setPositiveButton(res.getString(R.string.yes), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                final String appPackageName = getPackageName();
                                                try {
                                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
                                                } catch (android.content.ActivityNotFoundException anfe) {
                                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
                                                }
                                                finish();
                                            }
                                        });
                                        dialog.setNegativeButton(res.getString(R.string.no), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                finish();
                                            }
                                        });
                                        dialog.setCancelable(false);
                                        dialog.show();
                                    }
                                });
                                //In SharedPreferences eintragen
                                su.putBoolean("UpdateAvailable", true);
                                su.putString("SupportUrl", supporturl);
                            }
                        } else {
                            MainActivity.isUpdateAvailable = false;
                            if (showUpdateDialog) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                        dialog.setMessage(res.getString(R.string.your_software_is_uptodate));
                                        dialog.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                            }
                                        });
                                        dialog.show();
                                    }
                                });
                            }
                            //In SharedPreferences eintragen
                            su.putBoolean("UpdateAvailable", false);
                            su.putString("SupportUrl", supporturl);
                            //UpdateNews-Dialog anzeigen
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    checkFirstStart();
                                    checkMarshmellowPermissions();
                                }
                            });
                        }
                    } else if(server_state.equals("2")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(current_account.getRights() == Account.RIGHTS_TEAM) {
                                    android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                    dialog.setMessage(R.string.server_is_offline_team);
                                    dialog.setPositiveButton(res.getString(R.string.open), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            checkFirstStart();
                                            checkMarshmellowPermissions();
                                            //Dialog schließen
                                            dialog.dismiss();
                                        }
                                    });
                                    dialog.setNegativeButton(res.getString(R.string.close), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            //App beenden und Dialog schließen
                                            finish();
                                            dialog.dismiss();
                                        }
                                    });
                                    dialog.setCancelable(false);
                                    dialog.show();
                                } else {
                                    android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                    dialog.setMessage(res.getString(R.string.server_is_offline));
                                    dialog.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            //App beenden und Dialog schließen
                                            finish();
                                            dialog.dismiss();
                                        }
                                    });
                                    dialog.setCancelable(false);
                                    dialog.show();
                                }
                            }
                        });
                    } else if(server_state.equals("3")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if(current_account.getRights() == Account.RIGHTS_TEAM) {
                                    android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                    dialog.setMessage(R.string.server_is_waiting_team);
                                    dialog.setPositiveButton(res.getString(R.string.open), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            checkFirstStart();
                                            checkMarshmellowPermissions();
                                            //Dialog schließen
                                            dialog.dismiss();
                                        }
                                    });
                                    dialog.setNegativeButton(res.getString(R.string.close), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            //App beenden und Dialog schließen
                                            finish();
                                            dialog.dismiss();
                                        }
                                    });
                                    dialog.setCancelable(false);
                                    dialog.show();
                                } else {
                                    android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                    dialog.setMessage(R.string.server_is_waiting);
                                    dialog.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            //App beenden und Dialog schließen
                                            finish();
                                            dialog.cancel();
                                        }
                                    });
                                    dialog.setCancelable(false);
                                    dialog.show();
                                }
                            }
                        });
                    } else if(server_state.equals("4")) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                android.support.v7.app.AlertDialog.Builder dialog = new android.support.v7.app.AlertDialog.Builder(LogInActivity.this);
                                dialog.setMessage(res.getString(R.string.app_is_no_longer_supported));
                                dialog.setPositiveButton(res.getString(R.string.ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        finish();
                                        dialog.cancel();
                                    }
                                });
                                dialog.setCancelable(false);
                                dialog.show();
                            }
                        });
                    }
                } catch(Exception e) {}
            }
        }).start();
    }

    public void autoLogin() {
        //Angemeldet bleiben abfragen
        String username = current_account.getUsername();
        String password_string = current_account.getPassword();

        //AutoLogin vom Registrieren beachten
        if(!autologin.equals("")) {
            int index = autologin.indexOf(",");
            username = autologin.substring(0, index);
            password_string = autologin.substring(index +1);
            autologin = "";
        }

        if((!username.equals(res.getString(R.string.guest)) && MainActivity.isUpdateAvailable == false) || !autologin.equals("")) {
            LogIn(username, password_string, androidversion, current_version, su.getBoolean("Angemeldet bleiben", false));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if(requestCode == REQUEST_CODE_PERMISSION_READ_PHONE_STATE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startService(new Intent(LogInActivity.this, FCM_Instance_ID_Service.class));
        }
    }

    private void enableComponents(boolean e) {
        findViewById(R.id.LogIn_User_Name).setEnabled(e);
        findViewById(R.id.LogIn_Password).setEnabled(e);
        findViewById(R.id.LogIn_LogIn).setEnabled(e);
        findViewById(R.id.LogIn_Registrieren).setEnabled(e);
        findViewById(R.id.angemeldet_bleiben).setEnabled(e);
        findViewById(R.id.forgot_password).setEnabled(e);
    }
}