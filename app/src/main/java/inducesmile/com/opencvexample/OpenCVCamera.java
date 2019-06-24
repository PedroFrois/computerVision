package inducesmile.com.opencvexample;


import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
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
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
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
import com.android.volley.toolbox.JsonRequest;

import java.io.File;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import inducesmile.com.opencvexample.utils.preprocess.ImagePreprocessor;
import inducesmile.com.opencvexample.utils.Constants;
import inducesmile.com.opencvexample.utils.FolderUtil;
import inducesmile.com.opencvexample.utils.Utilities;

import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.rectangle;

public class OpenCVCamera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = OpenCVCamera.class.getSimpleName();

    private OpenCameraView cameraBridgeViewBase;

    private Mat colorRgba;
    private Mat colorGray;
    private Mat aux;
    private Mat croppedAux;

    private Point start, end, center;
    private Scalar rectangleColor = new Scalar(255, 0, 0, 0);
    private int rectangleShape[] = new int[]{70, 70};
    private Rect roi;
    private Scalar wantedColor = new Scalar(0,0,0,0);

    private ArrayList<Double> listOfMeans = new ArrayList<Double>();
    private Scalar meanAux = new Scalar(0, 0, 0, 0);
    private Mat des, forward;

    private ImagePreprocessor preprocessor;

    private Boolean startedRecording;

    //envio servidor
    private String baseUrl = "https://cv-titration-server.herokuapp.com/fit";
    private String url = "";
    private RequestQueue requestQueue;
    private String dataToSend = "";
    private TextView tvRepoList;  // This will reference our repo list text box.


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

                        if(i == 0){
                            meansString = "" + ((Double)elem.next()).toString();
                            indexString = "" + i;
                        } else{
                            meansString += ";" + ((Double)elem.next()).toString();
                            indexString += ";" + i;
                        }

                    }
                    System.out.println("MeanString");
                    System.out.println(meansString);
                    //indexString = "Vai funcionar agora";
                    getRepoList("pedrofrois", meansString, indexString);

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
        colorGray = new Mat(height, width, CvType.CV_8UC1);
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
            listOfMeans.add(meanAux.val[0] / 360);
            if(meanAux.val[0] - wantedColor.val[0] < 20) {
                //ToneGenerator toneGen1 = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);
                //toneGen1.startTone(ToneGenerator.TONE_CDMA_PIP, 150);
                try {
                    System.out.println("Try 1");
                    Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                    System.out.println("Try 2");
                    Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notification);
                    System.out.println("Try 3");
                    r.play();
                    System.out.println("Try 4");
                } catch (Exception e) {
                    System.out.println("Erro aqui vei");
                    e.printStackTrace();
                }
                System.out.println("ACHOU A CARALHA");
            }
        }

        return colorRgba;
    }

    private void getRepoList(String username, String meansString, String indexString) {
        // First, we insert the username into the repo url.
        // The repo url is defined in GitHubs API docs (https://developer.github.com/v3/repos/).
        this.url = this.baseUrl;

        // Next, we create a new JsonArrayRequest. This will use Volley to make a HTTP request
        // that expects a JSON Array Response.
        // To fully understand this, I'd recommend readng the office docs: https://developer.android.com/training/volley/index.html
        JsonObjectRequest arrReq = new JsonObjectRequest(Request.Method.POST, url,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            System.out.println("Response get");
                            System.out.println(response);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        Toast.makeText(OpenCVCamera.this, "Data sent", Toast.LENGTH_SHORT).show();
                        listOfMeans.clear();
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
        ){

            @Override
            protected Map<String, String> getParams()
            {
                Map<String, String>  params = new HashMap<String, String>();
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
