package inducesmile.com.opencvexample;


import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

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

import java.io.File;
import java.sql.SQLOutput;
import java.util.ArrayList;

import inducesmile.com.opencvexample.utils.preprocess.ImagePreprocessor;
import inducesmile.com.opencvexample.utils.Constants;
import inducesmile.com.opencvexample.utils.FolderUtil;
import inducesmile.com.opencvexample.utils.Utilities;

import static org.opencv.core.Core.mean;
import static org.opencv.core.Core.rectangle;

public class OpenCVCamera extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String TAG = OpenCVCamera.class.getSimpleName();

    private OpenCameraView cameraBridgeViewBase;

    private Mat colorRgba;
    private Mat colorGray;
    private Mat aux;
    private Mat croppedAux;

    private Point start, end, center;
    private Scalar rectangleColor = new Scalar(255,0,0,0);
    private int rectangleShape[] = new int[]{70,70};
    Rect roi;

    private ArrayList<Double> listOfMeans = new ArrayList<Double>();
    private Scalar meanAux = new Scalar(0,0,0,0);
    private Mat des, forward;

    private ImagePreprocessor preprocessor;

    private Boolean startedRecording;


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


        ImageView takePictureBtn = (ImageView)findViewById(R.id.take_picture);
        takePictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //String outPicture = Constants.SCAN_IMAGE_LOCATION + File.separator + Utilities.generateFilename();
                //FolderUtil.createDefaultFolder(Constants.SCAN_IMAGE_LOCATION);
                if(!startedRecording){
                    startedRecording = true;
                    Toast.makeText(OpenCVCamera.this, "Recording Started", Toast.LENGTH_LONG).show();

                } else{
                    startedRecording = false;
                    System.out.println();
                    System.out.println("MANDAR REQUISICAO");
                    System.out.println();
                    Toast.makeText(OpenCVCamera.this, "Data sent", Toast.LENGTH_LONG).show();
                    listOfMeans.clear();
                }
                //cameraBridgeViewBase.takePicture(outPicture);
                //Log.d(TAG, "Path " + outPicture);
            }
        });
    }



    @Override
    public void onPause() {
        super.onPause();
        if (cameraBridgeViewBase != null)
            cameraBridgeViewBase.disableView();
    }

    @Override
    public void onResume(){
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
        croppedAux = new Mat(rectangleShape[1],rectangleShape[0], CvType.CV_8UC4);

        int x = width/2;
        int y = height/2;
        center = new Point( x, y);
        start = new Point(x + rectangleShape[0]/2,y - rectangleShape[1]/2);
        end = new Point(x - rectangleShape[0]/2,y + rectangleShape[1]/2);

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
        Imgproc.cvtColor(colorRgba,aux,Imgproc.COLOR_RGB2HSV);
        rectangle(colorRgba,start, end, rectangleColor, 4);

        //System.out.println();
        //System.out.println(mean(croppedAux));

        croppedAux = aux.submat(roi);

        //System.out.println(mean(croppedAux));

        meanAux = mean(croppedAux);
        if(startedRecording)
            listOfMeans.add(meanAux.val[0]/360);

        return colorRgba;
    }
}
