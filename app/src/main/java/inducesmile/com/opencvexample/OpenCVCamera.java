package inducesmile.com.opencvexample;


import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import inducesmile.com.opencvexample.utils.preprocess.ImagePreprocessor;

import static java.lang.Math.abs;
import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.rectangle;

public class OpenCVCamera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = OpenCVCamera.class.getSimpleName();

    private OpenCameraView cameraBridgeViewBase;

    private Mat colorRgba;
    private Mat aux;
    private Mat croppedAux;

    private Point start, end, center;
    private Scalar rectangleColor = new Scalar(255, 0, 0, 0);
    private int[] rectangleShape = new int[]{70, 70};
    private Rect roi;
    private Scalar wantedColor = new Scalar(160, 0, 0, 0);

    private Boolean notificationPlayed = false;

    private ArrayList<Double> listOfMeans = new ArrayList<Double>();
    private Scalar meanAux = new Scalar(0, 0, 0, 0);
    private Mat des, forward;

    private ImagePreprocessor preprocessor;

    private Boolean startedRecording;

    //envio servidor
    private String baseUrl = "https://cv-titration-server.herokuapp.com";
    private String url;
    private RequestQueue requestQueue;


    private BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    cameraBridgeViewBase.enableView();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_open_cvcamera);

        preprocessor = new ImagePreprocessor();

        cameraBridgeViewBase = (OpenCameraView) findViewById(R.id.camera_view);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);
        cameraBridgeViewBase.disableFpsMeter();


        startedRecording = false;


        ImageView takePictureBtn = (ImageView) findViewById(R.id.take_picture);
        takePictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!startedRecording) {
                    startedRecording = true;
                    Toast.makeText(OpenCVCamera.this, "Recording Started", Toast.LENGTH_SHORT).show();

                } else {
                    startedRecording = false;
                    String meansString = "";
                    String indexString = "";

                    int i = 0;
                    for (Iterator<Double> elem = listOfMeans.iterator(); elem.hasNext(); i++) {
                        if (i == 0) {
                            meansString = "" + ((Double) elem.next()).toString();
                            indexString = "" + i;
                        } else {
                            meansString += ";" + ((Double) elem.next()).toString();
                            indexString += ";" + i;
                        }
                    }
                    sendData(meansString, indexString);

                }
            }
        });
        requestQueue = Volley.newRequestQueue(this);  // This setups up a new request queue which we will need to make HTTP requests.
    }

    @Override
    public void onPause() {
        super.onPause();
        if (cameraBridgeViewBase != null)
            cameraBridgeViewBase.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, baseLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            baseLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (cameraBridgeViewBase != null)
            cameraBridgeViewBase.disableView();
    }


    @Override
    public void onCameraViewStarted(int width, int height) {
        colorRgba = new Mat(height, width, CvType.CV_8UC4);
        aux = new Mat(height, width, CvType.CV_8UC4);
        croppedAux = new Mat(rectangleShape[1], rectangleShape[0], CvType.CV_8UC4);

        int x = width / 2;
        int y = height / 2;
        center = new Point(x, y);
        start = new Point(x + rectangleShape[0] / 2, y - rectangleShape[1] / 2);
        end = new Point(x - rectangleShape[0] / 2, y + rectangleShape[1] / 2);

        roi = new Rect((int) center.x, (int) center.y, rectangleShape[0], rectangleShape[1]);

        des = new Mat(height, width, CvType.CV_8UC4);
        forward = new Mat(height, width, CvType.CV_8UC4);
    }


    @Override
    public void onCameraViewStopped() {
        colorRgba.release();
    }


    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        colorRgba = inputFrame.rgba();
        preprocessor.changeImagePreviewOrientation(colorRgba, des, forward);
        Imgproc.cvtColor(colorRgba, aux, Imgproc.COLOR_RGB2HSV);
        rectangle(colorRgba, start, end, rectangleColor, 4);


        croppedAux = aux.submat(roi);

        meanAux = mean(croppedAux);

        if (startedRecording) {
            Double val = meanAux.val[0] / 180;
            listOfMeans.add(val);
            if (abs(meanAux.val[0] - wantedColor.val[0]) < 30) {
                try {
                    if (!notificationPlayed) {
                        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), notification);
                        mp.start();
                        notificationPlayed = true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        return colorRgba;
    }

    private void sendData(String meansString, String indexString) {
        // First, we insert the username into the repo url.
        // The repo url is defined in GitHubs API docs (https://developer.github.com/v3/repos/).
        this.url = this.baseUrl + "/fit";

        // Next, we create a new JsonArrayRequest. This will use Volley to make a HTTP request
        // that expects a JSON Array Response.
        // To fully understand this, I'd recommend readng the office docs: https://developer.android.com/training/volley/index.html
        StringRequest arrReq = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        try {
                            //("Response get");
                            JSONObject obj = new JSONObject(response);
                            //(obj);
                            String k = obj.get("k").toString();
                            String x0 = obj.get("x0").toString();
                            System.out.println(k);
                            System.out.println(x0);

                            Toast.makeText(OpenCVCamera.this, "k = " + k + "    x0 = " + x0,
                                    Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        listOfMeans.clear();
                        notificationPlayed = false;
                    }
                },

                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // If there a HTTP error then add a note to our repo list.
                        Toast.makeText(OpenCVCamera.this, "Error sending data", Toast.LENGTH_SHORT).show();
                        Log.e("Volley", error.toString());
                    }
                }
        ) {

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
                params.put("index", indexString);
                params.put("mean", meansString);

                return params;
            }

        };
        // Add the request we just defined to our request queue.
        // The request queue will automatically handle the request as soon as it can.
        requestQueue.add(arrReq);
    }
}
