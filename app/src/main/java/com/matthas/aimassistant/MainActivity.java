package com.matthas.AimAssistant;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.hardware.ConsumerIrManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.Activity;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

public class MainActivity extends Activity implements SensorEventListener {

    private static final String TAG = "Thesis";
    private SensorManager mSensorManager;
    private Sensor mRotationSensor;
    private float pitch;
    private double finalpitch;
    private double angle=0;
    TextView mDistanceIn;
    EditText mhandleangle;
    TextView mTargetAngle;
    TextView mIRdatashow;
    TextView mMaxrangetext;
    TextView mCurrenthandleangle;
    TextView mCurrenthandleangletext;
    ConsumerIrManager mCIR;
    private SensorManager mgr;

    private int viewmode = 0;    //zmienna odpowiedzialna za tryp ciemny[0]/jasny[1]
    public int freq = 889;       //czas trwania polbitu IR, 899us dla standardu RC5
    private int tdetonator;      //czas opoznienia zapalnika [s]
    private int distance;        //odleglosc do celu         [m]
    private int targettype;      //rodzaj celu               [0-9]
    private double averagemissilespeed; //srednia predkosc pocisku  [m/s]
    private double calibratedangle=33; //zmienna potrzebna do kalibracji katu pochylenia
    private int curvetype=0;    //typ strzalu ukosny/prosty
    private double gravity = 9.81f;  //wartosc przyspieszenia ziemskiego
    double maxrange =0;


    private static final int SENSOR_DELAY = 100 * 1000; // 100ms
    private static final int FROM_RADS_TO_DEGS = -57;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sensory pomiaru pochylenia
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // IR blaster
        mCIR = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        //===================================================
        mgr = (SensorManager) this.getSystemService(SENSOR_SERVICE);

        //wywolanie elementow layoutow
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout mLayoutMainmenu = findViewById(R.id.layout_main_menu);
        LinearLayout mLayoutTargetSettings = findViewById(R.id.layout_target_settings);
        LinearLayout mLayoutFinalScreen = findViewById(R.id.layout_final_screen);
        LinearLayout mLayoutCalibrationScreen = findViewById(R.id.layout_calibration_screen);
        findViewById(R.id.calibrationbackbutton).setOnClickListener(mBackbuttoncalibration);
        mhandleangle = (EditText) findViewById(R.id.handleangle);
        mCurrenthandleangle = (TextView) findViewById(R.id.currenthandleangle);
        findViewById(R.id.calibrationsetbutton).setOnClickListener(mCalibrationsetbutton);
        findViewById(R.id.missle1).setOnClickListener(mMissile1);
        findViewById(R.id.missle2).setOnClickListener(mMissile2);
        findViewById(R.id.missle3).setOnClickListener(mMissile3);
        findViewById(R.id.missle4).setOnClickListener(mMissile4);
        findViewById(R.id.calibration).setOnClickListener(mCalibration);
        findViewById(R.id.backbuttonsettingsmenu).setOnClickListener(mBackbuttonsettingsmenu);
        findViewById(R.id.highshot).setOnClickListener(mHishshot);
        findViewById(R.id.lowshot).setOnClickListener(mLowshow);
        mTargetAngle = (TextView) findViewById(R.id.targetangle);
        findViewById(R.id.targettype1).setOnClickListener(mTargettype1);
        findViewById(R.id.targettype2).setOnClickListener(mTargettype2);
        findViewById(R.id.targettype3).setOnClickListener(mTargettype3);
        findViewById(R.id.targettype4).setOnClickListener(mTargettype4);
        findViewById(R.id.next2).setOnClickListener(mNext2);
        findViewById(R.id.nextshot).setOnClickListener(mNextshot);
        findViewById(R.id.nexttarget).setOnClickListener(mNexttarget);
        mIRdatashow = (TextView) findViewById(R.id.IRdatashow);
        mDistanceIn = (EditText) findViewById(R.id.distanceIn);
        mMaxrangetext = (TextView) findViewById(R.id.maxrangetext);
        findViewById(R.id.buttonviewchange).setOnClickListener(mModechange);

        String handleanglestring = Double.toString(calibratedangle);
        mhandleangle.setText(handleanglestring);
        mCurrenthandleangle.setText(handleanglestring);

        //wywolanie funkcji sprawdzajacej wprowadzenie wszystkich danych przy kazdej zmiane wartosci
        //odleglosci
        mDistanceIn.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkifallsettingsok();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
        //to samo tylko dla wpisywania calibracji kata pochylenia uchwytu
        mhandleangle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                checkifanglecorrect();
            }
            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        //wlaczenie glownego menu, wylaczenie ekranu ustawien i ekranu finalowego
        mLayoutMainmenu.setVisibility(View.VISIBLE);
        mLayoutTargetSettings.setVisibility(View.GONE);
        mLayoutFinalScreen.setVisibility(View.GONE);

        //sprawdzenie czy telefon posiada odpowiedni Hardware
        try {
            mSensorManager = (SensorManager) getSystemService(Activity.SENSOR_SERVICE);
            mRotationSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            mSensorManager.registerListener(this, mRotationSensor, SENSOR_DELAY);
        } catch (Exception e) {
            Log.d(TAG, "Brak IR blaster\n");
            Toast.makeText(this, "Hardware compatibility issue", Toast.LENGTH_LONG).show();
        }
        viewmodeswitch();
    }

    View.OnClickListener mModechange = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (viewmode == 1 ){
                viewmode = 0;
            } else {
                viewmode = 1;
            }
            viewmodeswitch();
        }
    };


    //==============funkcja odpowiedzialna za tryp Ciemny/jasny===================
    void viewmodeswitch() {
        if (viewmode == 1) {   //lightmode
            //main menu
            ((Button) findViewById(R.id.buttonviewchange)).setText(getResources().getText(R.string.modedark));
            ((LinearLayout) findViewById(R.id.layout_main_menu)).setBackgroundColor(getResources().getColor(R.color.white)); //tlo
            ((TextView) findViewById(R.id.CurrentAngleMain)).setTextColor(getResources().getColor(R.color.black));            //Aktualny kat
            //main menu

            //targetsettings
            ((LinearLayout) findViewById(R.id.layout_target_settings)).setBackgroundColor(getResources().getColor(R.color.white)); //tlo
            ((TextView) findViewById(R.id.CurrentAngleSettings)).setTextColor(getResources().getColor(R.color.black));           //Aktualny kat
            ((Button) findViewById(R.id.backbuttonsettingsmenu)).setTextColor(getResources().getColor(R.color.black));           //przycisk wstecz Text
            ((Button) findViewById(R.id.backbuttonsettingsmenu)).setBackgroundColor(getResources().getColor(R.color.grey));      //przycisk wstecz Tlo
            ((TextView) findViewById(R.id.typedistancetotarged)).setTextColor(getResources().getColor(R.color.black));           //wpisz odleglosc do celu TEXT color
            ((EditText) findViewById(R.id.distanceIn)).setBackgroundColor(getResources().getColor(R.color.grey));                //pole wpisywania odleglosci TLO
            ((EditText) findViewById(R.id.distanceIn)).setTextColor(getResources().getColor(R.color.black));                     //pole wpisywania odleglosci Kolor tekstu
            ((TextView) findViewById(R.id.distanceInUnit)).setTextColor(getResources().getColor(R.color.black));                 //pole z jednostka "m"
            ((TextView) findViewById(R.id.highshotText)).setTextColor(getResources().getColor(R.color.black));                   //strzal za zaslony Text color
            ((ImageButton) findViewById(R.id.highshot)).setImageResource(R.drawable.highshot);                                   //strzal za zaslony Icona
            ((TextView) findViewById(R.id.lowshowText)).setTextColor(getResources().getColor(R.color.black));                    //strzal prosty Text color
            ((ImageButton) findViewById(R.id.lowshot)).setImageResource(R.drawable.lowshot);                               //strzal prosty Icona
            ((Button) findViewById(R.id.targettype1)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.piechota,0,0,0);  //target 1 obrazek
            ((Button) findViewById(R.id.targettype1)).setBackgroundColor(getResources().getColor(R.color.white));                                   //target 1 tlo
            ((Button) findViewById(R.id.targettype1)).setTextColor(getResources().getColor(R.color.black));                                         //target 1 text
            ((Button) findViewById(R.id.targettype2)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.base,0,0,0);      //target 2 obrazek
            ((Button) findViewById(R.id.targettype2)).setBackgroundColor(getResources().getColor(R.color.white));                                   //target 2 tlo
            ((Button) findViewById(R.id.targettype2)).setTextColor(getResources().getColor(R.color.black));                                         //target 2 text
            ((Button) findViewById(R.id.targettype3)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.infantryvehicle,0,0,0);  //target 3 obrazek
            ((Button) findViewById(R.id.targettype3)).setBackgroundColor(getResources().getColor(R.color.white));                                   //target 3 tlo
            ((Button) findViewById(R.id.targettype3)).setTextColor(getResources().getColor(R.color.black));                                         //target 3 text
            ((Button) findViewById(R.id.targettype4)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.armoredvehicle,0,0,0);  //target 4 obrazek
            ((Button) findViewById(R.id.targettype4)).setBackgroundColor(getResources().getColor(R.color.white));                                   //target 4 tlo
            ((Button) findViewById(R.id.targettype4)).setTextColor(getResources().getColor(R.color.black));                                         //target 4 text
            ((TextView) findViewById(R.id.maxrangetext)).setTextColor((getResources().getColor(R.color.black)));
            ((TextView) findViewById(R.id.maxrangestatictext)).setTextColor((getResources().getColor(R.color.black)));
            ((TextView) findViewById(R.id.currentangleangletext)).setTextColor((getResources().getColor(R.color.black)));
            ((TextView) findViewById(R.id.currenthandleangle)).setTextColor((getResources().getColor(R.color.black)));
            //targetsettings

            //final screen
            ((LinearLayout) findViewById(R.id.layout_final_screen)).setBackgroundColor(getResources().getColor(R.color.white));  //tlo
            ((TextView) findViewById(R.id.targetangletext)).setTextColor(getResources().getColor(R.color.black));               //wymagany kat text
            ((TextView) findViewById(R.id.targetangle)).setTextColor(getResources().getColor(R.color.black));                   //wymagany kat
            ((TextView) findViewById(R.id.currentangletext)).setTextColor(getResources().getColor(R.color.black));                   //aktualny kat
            //aktualny kat kat nie wymagane
            ((TextView) findViewById(R.id.IRdatashow)).setTextColor(getResources().getColor(R.color.black));                    //ramka IR


            //final screen
        } else {  //darkmode
            //main menu
            ((Button) findViewById(R.id.buttonviewchange)).setText(getResources().getText(R.string.modelight));
            ((LinearLayout) findViewById(R.id.layout_main_menu)).setBackgroundColor(getResources().getColor(R.color.black)); //tlo
            ((TextView) findViewById(R.id.CurrentAngleMain)).setTextColor(getResources().getColor(R.color.white));           //Aktualny kat
            //main menu

            //targetsettings
            ((LinearLayout) findViewById(R.id.layout_target_settings)).setBackgroundColor(getResources().getColor(R.color.black)); //tlo
            ((TextView) findViewById(R.id.CurrentAngleSettings)).setTextColor(getResources().getColor(R.color.white));           //Aktualny kat
            ((Button) findViewById(R.id.backbuttonsettingsmenu)).setTextColor(getResources().getColor(R.color.white));           //przycisk wstecz Text
            ((Button) findViewById(R.id.backbuttonsettingsmenu)).setBackgroundColor(getResources().getColor(R.color.grey));      //przycisk wstecz Tlo
            ((TextView) findViewById(R.id.typedistancetotarged)).setTextColor(getResources().getColor(R.color.white));           //wpisz odleglosc do celu TEXT color
            ((EditText) findViewById(R.id.distanceIn)).setBackgroundColor(getResources().getColor(R.color.grey));                //pole wpisywania odleglosci TLO
            ((EditText) findViewById(R.id.distanceIn)).setTextColor(getResources().getColor(R.color.white));                     //pole wpisywania odleglosci Kolor tekstu
            ((TextView) findViewById(R.id.distanceInUnit)).setTextColor(getResources().getColor(R.color.white));                 //pole z jednostka "m"
            ((TextView) findViewById(R.id.highshotText)).setTextColor(getResources().getColor(R.color.white));                   //strzal za zaslony Text color
            ((ImageButton) findViewById(R.id.highshot)).setImageResource(R.drawable.hightshot_black);                            //strzal za zaslony Icona
            ((TextView) findViewById(R.id.lowshowText)).setTextColor(getResources().getColor(R.color.white));                    //strzal prosty Text color
            ((ImageButton) findViewById(R.id.lowshot)).setImageResource(R.drawable.lowshot_black);                               //strzal prosty Icona
            ((Button) findViewById(R.id.targettype1)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.piechota_black,0,0,0);  //target 1 obrazek
            ((Button) findViewById(R.id.targettype1)).setBackgroundColor(getResources().getColor(R.color.black));                                   //target 1 tlo
            ((Button) findViewById(R.id.targettype1)).setTextColor(getResources().getColor(R.color.white));                                         //target 1 text
            ((Button) findViewById(R.id.targettype2)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.base_black,0,0,0);      //target 2 obrazek
            ((Button) findViewById(R.id.targettype2)).setBackgroundColor(getResources().getColor(R.color.black));                                   //target 2 tlo
            ((Button) findViewById(R.id.targettype2)).setTextColor(getResources().getColor(R.color.white));                                         //target 2 text
            ((Button) findViewById(R.id.targettype3)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.infantryvehicle_black,0,0,0);  //target 3 obrazek
            ((Button) findViewById(R.id.targettype3)).setBackgroundColor(getResources().getColor(R.color.black));                                   //target 3 tlo
            ((Button) findViewById(R.id.targettype3)).setTextColor(getResources().getColor(R.color.white));                                         //target 3 text
            ((Button) findViewById(R.id.targettype4)).setCompoundDrawablesWithIntrinsicBounds(R.drawable.armoredvehicle_black,0,0,0);  //target 4 obrazek
            ((Button) findViewById(R.id.targettype4)).setBackgroundColor(getResources().getColor(R.color.black));                                   //target 4 tlo
            ((Button) findViewById(R.id.targettype4)).setTextColor(getResources().getColor(R.color.white));                                         //target 4 text
            ((TextView) findViewById(R.id.maxrangetext)).setTextColor((getResources().getColor(R.color.white)));
            ((TextView) findViewById(R.id.maxrangestatictext)).setTextColor((getResources().getColor(R.color.white)));
            ((TextView) findViewById(R.id.currentangleangletext)).setTextColor((getResources().getColor(R.color.white)));
            ((TextView) findViewById(R.id.currenthandleangle)).setTextColor((getResources().getColor(R.color.white)));
            //targetsettings

            //final screen
            ((LinearLayout) findViewById(R.id.layout_final_screen)).setBackgroundColor(getResources().getColor(R.color.black));  //tlo
            ((TextView) findViewById(R.id.targetangletext)).setTextColor(getResources().getColor(R.color.white));               //wymagany kat text
            ((TextView) findViewById(R.id.targetangle)).setTextColor(getResources().getColor(R.color.white));                   //wymagany kat
            ((TextView) findViewById(R.id.currentangletext)).setTextColor(getResources().getColor(R.color.white));                   //aktualny kat
            //aktualny kat kat nie wymagane
            ((TextView) findViewById(R.id.IRdatashow)).setTextColor(getResources().getColor(R.color.white));                    //ramka IR

            //final screen

        }
    }


    //==============funkcja odpowiedzialna za tryp Ciemny/jasny===================




    //==========przyciski menu glownego===============
    //==========przyciski menu glownego===============
    //==========przyciski menu glownego===============
    //==========przyciski menu glownego===============
    //======funkcja wlaczajaca menu ustawien strzalu============
    void targetsettinglayout(){
        Log.d(TAG, "Wlaczenie ekranu ustawien strzalu\n");
        LinearLayout mLayoutMainmenu = findViewById(R.id.layout_main_menu);
        LinearLayout mLayoutTargetSettings = findViewById(R.id.layout_target_settings);
        mLayoutMainmenu.setVisibility(View.GONE);
        mLayoutTargetSettings.setVisibility(View.VISIBLE);
    };



    //======funkcja wlaczajaca menu ustawien strzalu============

    //funkcja obliczenia maksymalnego zasiegu
    void calculatemaxrange() {

        maxrange = averagemissilespeed*averagemissilespeed/gravity;
        NumberFormat nf = new DecimalFormat("####");
        mMaxrangetext.setText("" +nf.format(maxrange));

    }
    //funkcja obliczenia maksymalnego zasiegu
    //======przycisk wyboru pocisku nr 111111111111=============
    View.OnClickListener mMissile1 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Pocisk nr 1\n");
            averagemissilespeed = 147;
            targetsettinglayout();
            calculatemaxrange();


        }
    };
    //======przycisk wyboru pocisku nr 22222222222=============
    View.OnClickListener mMissile2 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Pocisk nr 2\n");
            averagemissilespeed = 144;
            targetsettinglayout();
            calculatemaxrange();

        }
    };

    //======przycisk wyboru pocisku nr 33333333333=============
    View.OnClickListener mMissile3 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Pocisk nr 3\n");
            averagemissilespeed = 140;
            targetsettinglayout();
            calculatemaxrange();

        }
    };
    //======przycisk wyboru pocisku nr 44444444444=============
    View.OnClickListener mMissile4 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Pocisk nr 4\n");
            averagemissilespeed = 137;
            targetsettinglayout();
            calculatemaxrange();

        }
    };
    //============przycisk kalibracji pochylenia===============
    View.OnClickListener mCalibration = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Calibracja\n");
            LinearLayout mLayoutCalibrationScreen = findViewById(R.id.layout_calibration_screen);
            LinearLayout mLayoutMainMenu = findViewById(R.id.layout_main_menu);
            mLayoutMainMenu.setVisibility(View.GONE);
            mLayoutCalibrationScreen.setVisibility(View.VISIBLE);

        }
    };



    //==========przyciski menu glownego===============
    //==========przyciski menu glownego===============
    //==========przyciski menu glownego===============
    //==========przyciski menu glownego===============

    //=========przycisku ustawien strzalu=============
    //=========przycisku ustawien strzalu=============
    //=========przycisku ustawien strzalu=============
    //=========przycisku ustawien strzalu=============

    //========funkcja przycisku DALEJ ==============================================================
    //przycisk zostanie pokazany potym jak wszystkie dane zostana uzupelnione
    View.OnClickListener mNext2 = new View.OnClickListener() {
        public void onClick(View v) {
            //przejscie na finalowy layout
            Log.d(TAG,"Przejscie na finalowy Layout");
            LinearLayout mLayoutTargetSettings = findViewById(R.id.layout_target_settings);
            LinearLayout mLayoutFinalScreen = findViewById(R.id.layout_final_screen);
            mLayoutTargetSettings.setVisibility(View.GONE);
            mLayoutFinalScreen.setVisibility(View.VISIBLE);

            getAngle();   //wywolanie funkcji obliczajacej potrzebny kat
            //zamiana sygnalu Dec na binarny
            Log.d(TAG, "Utworzenie ramki binarnie");
            int[] prepattern = {
                    1, //start bit #1                              //0
                    1,  //start bit #2                             //1
                    0,  //toggle bit                               //2
                    (tdetonator / 64) % 2,   //detonator bit #6    //3
                    (tdetonator / 32) % 2,   //detonator bit #5    //4
                    (tdetonator / 16) % 2,   //detonator bit #4    //5
                    (tdetonator / 8) % 2,    //detonator bit #3    //6
                    (tdetonator / 4) % 2,    //detonator bit #2    //7
                    (tdetonator / 2) % 2,    //detonator bit #1    //8
                    (tdetonator / 1) % 2,    //detonator bit #0    //9
                    (distance / 4096) % 2,   //distance bit #12    //10
                    (distance / 2048) % 2,   //distance bit #11    //11
                    (distance / 1024) % 2,   //distance bit #10    //12
                    (distance / 512) % 2,    //distance bit #9     //13
                    (distance / 256) % 2,    //distance bit #8     //14
                    (distance / 128) % 2,    //distance bit #7     //15
                    (distance / 64) % 2,     //distance bit #6     //16
                    (distance / 32) % 2,     //distance bit #5     //17
                    (distance / 16) % 2,     //distance bit #4     //18
                    (distance / 8) % 2,      //distance bit #3     //19
                    (distance / 4) % 2,      //distance bit #2     //20
                    (distance / 2) % 2,      //distance bit #1     //21
                    (distance / 1) % 2};     //distance bit #0     //22

            //testowanie
            String a;
            a = Arrays.toString(prepattern);
            mIRdatashow.setText(a);
            //for testing

            //zamiana arraya prepattern na ciag mikrosekund trwania polpitow podczerwieni
            Log.d(TAG, "Utworzenie ramki IR\n");
            int[] pattern = {freq, freq, freq * 2, freq, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850, 850};
            int pattern_count = 4;
            Log.d(TAG, "Zamiana ramki na mikrosendy\n");
            for (int i = 3; i <= 20; i++) {  //kolejne bity
                if (prepattern[i] == 0) {   //aktualny bit = 0
                    //both are false
                    if (prepattern[i - 1] == 0) {   //jezeli stary bit = 0
                        pattern[pattern_count] = freq;
                        pattern_count += 1;
                        pattern[pattern_count] = freq;
                        pattern_count += 1;
                    } else {    //jezeli stary bit = 1
                        pattern[pattern_count - 1] = freq * 2;
                        pattern[pattern_count] = freq;
                        pattern_count += 1;
                    }
                } else {    //aktualny bit = 1
                    if (prepattern[i - 1] == 1) {   //stary bit = 1
                        pattern[pattern_count] = freq;
                        pattern_count += 1;
                        pattern[pattern_count] = freq;
                        pattern_count += 1;
                    } else { //stary bit = 0
                        pattern[pattern_count - 1] = freq * 2;
                        pattern[pattern_count] = freq;
                        pattern_count += 1;
                    }
                }
            }
              //ostatni polbit bardzo dlugi aby zapobiec bledom
            pattern[pattern_count] = 4000;
            Log.d(TAG, "Przeslanie sygnalu IR\n");
            //mCIR.transmit(36000, pattern);   //[test]wykomentowac dla urzadzenia virtualnego
        };
    };//==========wyslanie sygnalu IR==========

    //========funkcja przycisku DALEJ ==============================================================
    //========funkcja sprawdzajaca wprowadzenie wszystkich danych============
    public void checkifallsettingsok() {
        Log.d(TAG, "checkIfAllSettingsOK");
        if (TextUtils.isEmpty(mDistanceIn.getText().toString())) {
            //nie rob nic
        } else {
            distance = Integer.parseInt(mDistanceIn.getText().toString());
            if ( curvetype == 0 || targettype == 0 || (distance == 0 || distance >= maxrange)) {
                Button mNext2 = (Button) findViewById(R.id.next2) ;
                mNext2.setVisibility(View.GONE);
            } else {
                Button mNext2 = (Button) findViewById(R.id.next2) ;
                mNext2.setVisibility(View.VISIBLE);
            }
        }
    };
    //========funkcja sprawdzajaca wprowadzenie wszystkich danych============
    //=========przycisk wstecz=============
    View.OnClickListener mBackbuttonsettingsmenu = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Przycisk Wstecz");
            LinearLayout mLayoutMainmenu = findViewById(R.id.layout_main_menu);
            LinearLayout mLayoutTargetSettings = findViewById(R.id.layout_target_settings);
            mLayoutTargetSettings.setVisibility(View.GONE);
            mLayoutMainmenu.setVisibility(View.VISIBLE);
            averagemissilespeed = 0;
        }
    };
    //=========przycisk wstecz=============
    //=========wybor strzalu ukosnego============
    View.OnClickListener mHishshot = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Wybor strzalu ukosnego");

            curvetype = 1;
            checkifallsettingsok();
                ((ImageButton) findViewById(R.id.highshot)).setBackgroundResource(R.layout.imagebutton_border);
                ((ImageButton) findViewById(R.id.lowshot)).setBackgroundResource(R.color.grey);


        }
    };
    //=========wybor strzalu ukosnego============
    //=========wybor strzalu prostego============
    View.OnClickListener mLowshow = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Wybor strzalu prostego");

            curvetype = 2;
            checkifallsettingsok();
                ((ImageButton) findViewById(R.id.lowshot)).setBackgroundResource(R.layout.imagebutton_border);
                ((ImageButton) findViewById(R.id.highshot)).setBackgroundResource(R.color.grey);
        }
    };
    //======funkcja pomocnicza podswietlen przyciskow===========

    void unsellectbuttonsmissiletyp() {
        if (viewmode==1){
            ((Button) findViewById(R.id.targettype1)).setTextColor(getResources().getColor(R.color.black));
            ((Button) findViewById(R.id.targettype2)).setTextColor(getResources().getColor(R.color.black));
            ((Button) findViewById(R.id.targettype3)).setTextColor(getResources().getColor(R.color.black));
            ((Button) findViewById(R.id.targettype4)).setTextColor(getResources().getColor(R.color.black));

        } else {
            ((Button) findViewById(R.id.targettype1)).setTextColor(getResources().getColor(R.color.white));
            ((Button) findViewById(R.id.targettype2)).setTextColor(getResources().getColor(R.color.white));
            ((Button) findViewById(R.id.targettype3)).setTextColor(getResources().getColor(R.color.white));
            ((Button) findViewById(R.id.targettype4)).setTextColor(getResources().getColor(R.color.white));
        }
    }

    //======funkcja pomocnicza podswietlen przyciskow===========

    //=========wybor strzalu prostego============
    //=========wybor celu PIECHOTA============
    View.OnClickListener mTargettype1 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Cel Typ nr 1");

            targettype = 1;
            tdetonator = 10;
            checkifallsettingsok();
            //zaznacz przycisk na czerwono/odznacz pozostale przyciski
            unsellectbuttonsmissiletyp();
            ((Button) findViewById(R.id.targettype1)).setTextColor(getResources().getColor(R.color.red));
        }
    };
    //=========wybor celu PIECHOTA============
    //=========wybor celu OBOZ============
    View.OnClickListener mTargettype2 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Cel Typ nr 2");

            targettype = 2;
            tdetonator = 6;
            checkifallsettingsok();
            //zaznacz przycisk na czerwono/odznacz pozostale przyciski
            unsellectbuttonsmissiletyp();
            ((Button) findViewById(R.id.targettype2)).setTextColor(getResources().getColor(R.color.red));
        }
    };
    //=========wybor celu OBOZ============
    //=========wybor celu POJAZD============
    View.OnClickListener mTargettype3 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Cel Typ nr 3\n");

            targettype = 3;
            tdetonator = 1;
            checkifallsettingsok();
            //zaznacz przycisk na czerwono/odznacz pozostale przyciski
            unsellectbuttonsmissiletyp();
            ((Button) findViewById(R.id.targettype3)).setTextColor(getResources().getColor(R.color.red));
        }
    };
    //=========wybor celu POJAZD============
    //=========wybor celu POJAZD UZBROJONY============
    View.OnClickListener mTargettype4 = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Cel Typ nr 4\n");

            targettype = 4;
            tdetonator = 3;
            checkifallsettingsok();
            //zaznacz przycisk na czerwono/odznacz pozostale przyciski
            unsellectbuttonsmissiletyp();
            ((Button) findViewById(R.id.targettype4)).setTextColor(getResources().getColor(R.color.red));
        }
    };
    //=========wybor celu POJAZD UZBROJONY============

    //=========przycisku ustawien strzalu=============
    //=========przycisku ustawien strzalu=============
    //=========przycisku ustawien strzalu=============
    //=========przycisku ustawien strzalu=============

    //=========ustawienie ostatniego ekranu=============
    //=========ustawienie ostatniego ekranu=============
    //=========ustawienie ostatniego ekranu=============
    //=========ustawienie ostatniego ekranu=============

    //==========================przycisk nastepnego strzalu=======================
    View.OnClickListener mNexttarget = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Nastepny strzal\n");
            LinearLayout mLayoutMainmenu = findViewById(R.id.layout_main_menu);
            LinearLayout mLayoutFinalScreen = findViewById(R.id.layout_final_screen);
            mLayoutFinalScreen.setVisibility(View.GONE);
            mLayoutMainmenu.setVisibility(View.VISIBLE);
            // zerowanie zmiennych
            Log.d(TAG, "zerowanie zmiennych");
            distance = 0;
            targettype = 0;
            curvetype = 0;
            averagemissilespeed = 0;
            tdetonator = 0;
            mDistanceIn.setText("");
            ((ImageButton) findViewById(R.id.highshot)).setBackgroundResource(R.color.grey);
            ((ImageButton) findViewById(R.id.lowshot)).setBackgroundResource(R.color.grey);
            unsellectbuttonsmissiletyp();
            Button mNext2 = (Button) findViewById(R.id.next2) ;
            mNext2.setVisibility(View.GONE);
        }
    };
    //==========================przycisk nastepnego strzalu=======================

    //===funkcja obliczajaca kat dla strzalow====
    public void getAngle() {
        double angle1,angle2;
        double distancedouble = distance;



        angle1 = Math.toDegrees(Math.atan((Math.pow(averagemissilespeed, 2)
                + Math.sqrt (Math.pow (averagemissilespeed, 4) -
                (gravity * (gravity * Math.pow (distancedouble, 2))))) /
                (gravity * distancedouble)));
        angle2 = Math.toDegrees(Math.atan((Math.pow(averagemissilespeed, 2) -
                Math.sqrt (Math.pow (averagemissilespeed, 4) -
                        (gravity * (gravity * Math.pow (distancedouble, 2))))) / (
                                gravity * distancedouble)));

        if (curvetype == 1) {
            if (angle1 >= angle2) {
                angle = angle2;
            } else {
                angle = angle1;
            };
        } else if (curvetype ==2){
            if (angle1 <= angle2) {
                angle = angle2;
            } else {
                angle = angle1;
            };
        };
        NumberFormat nf = new DecimalFormat("##.#");
        mTargetAngle.setText("" +nf.format(angle));
    };
    //===funkcja obliczajaca kat dla strzalow====

    //=========ustawienie ostatniego ekranu=============
    //=========ustawienie ostatniego ekranu=============
    //=========ustawienie ostatniego ekranu=============
    //=========ustawienie ostatniego ekranu=============


    //====pobranie danych pochylenia telefonu============
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mRotationSensor) {
            if (event.values.length > 4) {
                float[] truncatedRotationVector = new float[4];
                System.arraycopy(event.values, 0, truncatedRotationVector, 0, 4);
                update(truncatedRotationVector);
            } else {
                update(event.values);
            }
        }
    }
    //====pobranie danych pochylenia telefonu============

    //===ponowny strzal do tego samego celu============
    void nextshot() {
        Log.d(TAG, "Ponowny strzal\n");
        LinearLayout mLayoutTargetSettings = findViewById(R.id.layout_target_settings);
        LinearLayout mLayoutFinalScreen = findViewById(R.id.layout_final_screen);
        mLayoutFinalScreen.setVisibility(View.GONE);
        mLayoutTargetSettings.setVisibility(View.VISIBLE);
    };

    View.OnClickListener mNextshot = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            nextshot();
        }
    };

    //===ponowny strzal do tego samego celu============

    //=====pokazanie aktualnego pochylenia telefonu==========
    private void update(float[] vectors) {
        float errormarging =2;  //zmienna bledu celowania w stopniach
        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, vectors);
        int worldAxisX = SensorManager.AXIS_X;
        int worldAxisZ = SensorManager.AXIS_Z;
        float[] adjustedRotationMatrix = new float[9];
        SensorManager.remapCoordinateSystem(rotationMatrix, worldAxisX, worldAxisZ, adjustedRotationMatrix);
        float[] orientation = new float[3];
        SensorManager.getOrientation(adjustedRotationMatrix, orientation);
        pitch = orientation[1] * FROM_RADS_TO_DEGS;
        NumberFormat nf = new DecimalFormat("##.#");
        finalpitch = pitch - calibratedangle;
        finalpitch *= -1;
        //Textview na ekranie startowym
        ((TextView)findViewById(R.id.CurrentAngleMain)).setText(" "+nf.format(finalpitch));
        //Textview na ekranie ustawien
        ((TextView)findViewById(R.id.CurrentAngleSettings)).setText(" "+nf.format(finalpitch));
        //Textview na ekranie koncowym
        ((TextView)findViewById(R.id.CurrentAngleFinal)).setText(" "+nf.format(finalpitch));

        //====ustawienie odpowiednich kolorow w zaleznosci od aktualnego kata
        if ((finalpitch<=(angle+errormarging)) & (finalpitch>=(angle-errormarging) ) ) {
            //jak aktualny kat w zakresie bledu celowania to kolor zielony
            ((TextView) findViewById(R.id.CurrentAngleFinal)).setTextColor(getResources().getColor(R.color.green));
        } else {  //jezeli nie to kolor czerwony
            ((TextView) findViewById(R.id.CurrentAngleFinal)).setTextColor(getResources().getColor(R.color.red));
        }
    }
    //=====pokazanie aktualnego pochylenia telefonu==========


    //=======ekran calibracji========
    //=======ekran calibracji========
    //=======ekran calibracji========
    //=======ekran calibracji========

    View.OnClickListener mBackbuttoncalibration = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "Powrot z calibracji\n");
            LinearLayout mLayoutMainmenu = findViewById(R.id.layout_main_menu);
            LinearLayout mLayoutCalibrationScreen = findViewById(R.id.layout_calibration_screen);
            mLayoutCalibrationScreen.setVisibility(View.GONE);
            mLayoutMainmenu.setVisibility(View.VISIBLE);
        }
    };

    void checkifanglecorrect() {
            Log.d(TAG, "check Calibration Angle");
            if (TextUtils.isEmpty(mhandleangle.getText().toString())) {
                //nie rob nic
            } else {
                double calibratedangleCHECK = Double.parseDouble(mhandleangle.getText().toString());
                if ( calibratedangleCHECK > 90 || calibratedangleCHECK < 0) {
                    Button mCalibrationbuttonset = (Button) findViewById(R.id.calibrationsetbutton) ;
                    mCalibrationbuttonset.setVisibility(View.GONE);
                    Toast.makeText(this, "Kat musi się zawierac w 90°", Toast.LENGTH_SHORT).show();
                    Toast toast = Toast.makeText(this,"Kat musi się zawierac w 90°", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.TOP, 0, 0);
                    toast.show();
                } else {
                    Button mCalibrationbuttonset = (Button) findViewById(R.id.calibrationsetbutton) ;
                    mCalibrationbuttonset.setVisibility(View.VISIBLE);
                }
            }
        }
        View.OnClickListener mCalibrationsetbutton = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                calibratedangle = Double.parseDouble(mhandleangle.getText().toString());
                LinearLayout mLayoutMainmenu = findViewById(R.id.layout_main_menu);
                LinearLayout mLayoutCalibrationScreen = findViewById(R.id.layout_calibration_screen);
                mLayoutCalibrationScreen.setVisibility(View.GONE);
                mLayoutMainmenu.setVisibility(View.VISIBLE);
                String handleanglestring = Double.toString(calibratedangle);
                mCurrenthandleangle.setText(handleanglestring);
            }
        };


    //=======ekran calibracji========
    //=======ekran calibracji========
    //=======ekran calibracji========
    //=======ekran calibracji========











}//koniec programu