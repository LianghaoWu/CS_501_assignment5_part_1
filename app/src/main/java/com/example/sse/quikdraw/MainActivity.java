//TODO, make it so before we set to matrix mode, we scale the image so it doesn't bounce before stretching.


package com.example.sse.quikdraw;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.icu.number.Scale;
import android.support.v7.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.GestureDetector;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static android.widget.ImageView.ScaleType.CENTER_INSIDE;
import static android.widget.ImageView.ScaleType.FIT_XY;

public class MainActivity extends Activity implements GestureDetector.OnGestureListener,  GestureDetector.OnDoubleTapListener
//public class MainActivity extends AppCompatActivity implements GestureDetector.OnGestureListener
{
    private static final int SWIPE_MIN_DISTANCE = 120;          //swiping thresholds...
    private static final int SWIPE_MAX_OFF_PATH = 250;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;
    private static final String DRAWABLE_PREFIX = "useful_";  //used to prefix images we want to show in our app.

    private ArrayList<Drawable> drawables;  //keeping track of our drawables
    private int currDrawableIndex;  //keeping track of which drawable is currently displayed.
    private float SF = 1f;  //initial image scale factor.

    //Boiler Plate Stuff.
    private ImageView imgView;
    private Button btnLeft;
    private Button btnRight;
    // value used to determine whether user shook the device "significantly"
    private static int SIGNIFICANT_SHAKE = 100000;   //tweak this as necessary
    private float acceleration;
    private float currentAcceleration;
    private float lastAcceleration;

    GestureDetector.OnDoubleTapListener DTL;
    GestureDetector GD;         //consumes gesture events.
    ScaleGestureDetector SGD;   //another consumer of gesture events, but for scaling.
    Matrix matrix;              //information for storing graphical transformations.  https://developer.android.com/reference/android/graphics/Matrix
    Animation anim;

    @Override
    protected void onStart() {
        super.onStart();
        enableAccelerometerListening();
    }
    // enable listening for accelerometer events
    private void enableAccelerometerListening() {
        // The Activity has a SensorManager Reference.
        // This is how we get the reference to the device's SensorManager.
        SensorManager sensorManager =
                (SensorManager) this.getSystemService(
                        Context.SENSOR_SERVICE);    //The last parm specifies the type of Sensor we want to monitor


        //Now that we have a Sensor Handle, let's start "listening" for movement (accelerometer).
        //3 parms, The Listener, Sensor Type (accelerometer), and Sampling Frequency.
        sensorManager.registerListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);   //don't set this too high, otw you will kill user's battery.
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            // get x, y, and z values for the SensorEvent
            //each time the event fires, we have access to three dimensions.
            //compares these values to previous values to determine how "fast"
            // the device was shaken.
            //Ref: http://developer.android.com/reference/android/hardware/SensorEvent.html

            float x = event.values[0];   //obtaining the latest sensor data.
            float y = event.values[1];   //sort of ugly, but this is how data is captured.
            float z = event.values[2];

            // save previous acceleration value
            lastAcceleration = currentAcceleration;

            // calculate the current acceleration
            currentAcceleration = x * x + y * y + z * z;   //This is a simplified calculation, to be real we would need time and a square root.

            // calculate the change in acceleration        //Also simplified, but good enough to determine random shaking.
            acceleration = currentAcceleration *  (currentAcceleration - lastAcceleration);

            // if the acceleration is above a certain threshold
            if (acceleration > SIGNIFICANT_SHAKE) {
                NavRight();
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }
    };

    @Override
    protected void onStop() {
        disableAccelerometerListening();
        super.onStop();
    }

    // disable listening for accelerometer events
    private void disableAccelerometerListening() {

    //Disabling Sensor Event Listener is two step process.
        //1. Retrieve SensorManager Reference from the activity.
        //2. call unregisterListener to stop listening for sensor events
        //THis will prevent interruptions of other Apps and save battery.

        // get the SensorManager
        SensorManager sensorManager =
                (SensorManager) this.getSystemService(
                        Context.SENSOR_SERVICE);

        // stop listening for accelerometer events
        sensorManager.unregisterListener(sensorEventListener,
                sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgView = (ImageView) findViewById(R.id.imgView);
        btnRight = (Button) findViewById(R.id.btnRight);
        btnLeft = (Button) findViewById(R.id.btnLeft);

        matrix = new Matrix();
        SGD = new ScaleGestureDetector(this, new ScaleListener());  //listen for scaling changes, see below
        GD = new GestureDetector(this, this);   //Context, Listener as per Constructor Doc.
        anim = AnimationUtils.loadAnimation(getApplicationContext(),R.anim.rotate);

        currDrawableIndex = 0;  //ArrayList Index of Current Drawable.
        getDrawables();         //Retrieves the drawables we want, ie, prefixed with "animal_"
        imgView.setImageDrawable(null);  //Clearing out the default image from design time.
        changePicture();        //Sets the ImageView to the first drawable in the list.

        //setting up navigation call backs.  (Left and Right Buttons)
        btnLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavLeft();
            }
        });

        btnRight.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NavRight();
            }
        });

    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        imgView.startAnimation(anim);
        NavRight();
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent motionEvent) {
        return false;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener{
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            imgView.setScaleType(ImageView.ScaleType.MATRIX);  //make it so the imgView is scalable via a matrix
            SF = SF*detector.getScaleFactor();   //detector changes are passed to automatically from the Android Framework!

            SF = Math.max(0.1f, Math.min(SF, 1f));  //limiting size of stretch/shrink
            matrix.setScale(SF, SF);         //growing/shrinking proportionally in x,y.
            imgView.setImageMatrix(matrix);  //update the imgview using the new scale in the matrix.
            return true;  //we've consumed the event
        }
    }



    @Override
    public boolean onTouchEvent(MotionEvent event) {

//sse, these won't work at the same time, two gesture detectors fighting each other!
//        GD.onTouchEvent(event);  //let the gesture detector know about motion.
//        SGD.onTouchEvent(event);  //let the scale gesture detector know about motion.
//        return true;

//Using ScalingGestureDetector and GestureDetector together
//ref: https://stackoverflow.com/questions/15309743/use-scalegesturedetector-with-gesturedetector/20509292#20509292
        boolean result = SGD.onTouchEvent(event);  //give the SGD the first crack at consuming the gesture.
        // combine the result with SGD InProgress check to see if we are in the midst of scaling.
        boolean isScaling = result && SGD.isInProgress();
        if (!isScaling) {
            // if not scaling, go ahead give the regular GestureDetector an opportunity to consume. (fling, long tab, etc.)
            result = GD.onTouchEvent(event);
        }

        // if true, return true, else send it up the chain
       return result ? result : super.onTouchEvent(event); //ternary operator.  condition ? exprIfTrue : exprIfFalse
    }


    @Override
    public boolean onDown(MotionEvent e) {
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {
        return;
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }


    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (distanceX > SWIPE_MAX_OFF_PATH) {  return false;  }

        if (distanceY > 50) {
            NavLeft();
            return true;
        }
        else if (distanceY < -50) {
            NavRight();
            return true;
        }
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        return;
    }

//swiping left/right but only if significant
//ref: https://stackoverflow.com/questions/6720138/swipe-left-right-up-and-down-depending-on-velocity-or-other-variables

    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
        if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {  return false;  }

        /* positive value means right to left direction */
        final float distance = e1.getX() - e2.getX();
        final boolean enoughSpeed = Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY;
        if(distance > SWIPE_MIN_DISTANCE && enoughSpeed) {
            // right to left swipe
            NavRight();
            NavRight();
            NavRight();
            return true;
        }  else if (distance < -SWIPE_MIN_DISTANCE && enoughSpeed) {
            // left to right swipe
            NavLeft();
            NavLeft();
            NavLeft();
            return true;
        } else {
            // oooou, it didn't qualify; do nothing
            return false;
        }
    }

    public void NavRight() {
        if (currDrawableIndex == drawables.size() - 1)
            currDrawableIndex = 0;
        else
            currDrawableIndex++;
        changePicture();
    }

    public void NavLeft() {
        if (currDrawableIndex == 0)
            currDrawableIndex = drawables.size() - 1;
        else
            currDrawableIndex--;
        changePicture();
    }


    public void getDrawables() {
        Field[] drawablesFields = com.example.sse.quikdraw.R.drawable.class.getFields();  //getting array of ALL drawables.
        drawables = new ArrayList<>();  //we prefer an ArrayList, to store the drawables we are interested in.  Why ArrayList and not an Array here? A: _________

        String fieldName;
        for (Field field : drawablesFields) {   //1. Looping over the Array of All Drawables...
            try {
                fieldName = field.getName();    //2. Identifying the Drawables Name, eg, "animal_bewildered_monkey"
                Log.i("LOG_TAG", "com.your.project.R.drawable." + fieldName);
                if (fieldName.startsWith(DRAWABLE_PREFIX))  //3. Adding drawable resources that have our prefix, specifically "animal_".
                    drawables.add(getResources().getDrawable(field.getInt(null)));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Routine to change the picture in the image view dynamically.
    public void changePicture() {
        imgView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imgView.setImageDrawable(drawables.get(currDrawableIndex));  //note, this is the preferred way of changing images, don't worry about parent viewgroup size changes.
    }

}