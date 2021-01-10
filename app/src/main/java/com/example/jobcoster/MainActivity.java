package com.example.jobcoster;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import dmax.dialog.SpotsDialog;
import edmt.dev.edmtdevcognitiveface.Contract.Face;
import edmt.dev.edmtdevcognitiveface.Contract.IdentifyResult;
import edmt.dev.edmtdevcognitiveface.Contract.Person;
import edmt.dev.edmtdevcognitiveface.Contract.TrainingStatus;
import edmt.dev.edmtdevcognitiveface.FaceServiceClient;
import edmt.dev.edmtdevcognitiveface.FaceServiceRestClient;
import edmt.dev.edmtdevcognitiveface.Rest.ClientException;
import edmt.dev.edmtdevcognitiveface.Rest.Utils;

public class MainActivity extends AppCompatActivity {

    private final String API_KEY="d508f60d5c5a48b296e4e35e6e0f6402";
    private final String API_LINK="https://westus2.api.cognitive.microsoft.com/face/v1.0";

    // API Connection
    private FaceServiceClient faceServiceClient = new FaceServiceRestClient(API_LINK, API_KEY);

    // Person Group
    private final String personGroupID = "employees";

    // Variables for detecting and identifying pic from drawable storage
    // From Video
    ImageView img_view;
    Bitmap bitmap;
    Face[] faceDetected;
    Button btn_detect, btn_identify;

    // Variables for taking a picture from camera
    // (From Article)
    private static final String TAG = "CapturePicture";
    static final int REQUEST_PICTURE_CAPTURE = 1;
    private ImageView image;
    private String pictureFilePath;

    // Detects Face and forms orange rectangle around detected face
    // From Video
    class detectTask extends AsyncTask<InputStream, String, Face[]>{

        AlertDialog alertDialog = new SpotsDialog.Builder()
                .setContext(MainActivity.this)
                .setCancelable(false)
                .build();

        @Override
        protected void onPreExecute() {
            alertDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            alertDialog.setMessage(values[0]);
        }

        @Override
        protected Face[] doInBackground(InputStream... inputStreams) {

            try {
                publishProgress("Detecting");
                Face[] result = faceServiceClient.detect(inputStreams[0], true, false, null);
                if(result == null)
                {
                    return null;
                }
                else
                {
                    return result;
                }
            } catch (ClientException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Face[] faces) {

            alertDialog.dismiss();

            if(faces == null)
            {
                Toast.makeText(MainActivity.this, "No face detected", Toast.LENGTH_SHORT).show();
            }
            else
            {
                img_view.setImageBitmap(Utils.drawFaceRectangleOnBitmap(bitmap, faces, Color.YELLOW));
                faceDetected = faces;

                Log.d("testfacedetect", faceDetected[0].faceId.toString());
                btn_identify.setEnabled(true);
            }
        }
    }

    // Identifies detected face using API against Azure database
    // From Video
    class IdentificationTask extends AsyncTask<UUID, String, IdentifyResult[]>{

        AlertDialog alertDialog = new SpotsDialog.Builder()
                .setContext(MainActivity.this)
                .setCancelable(false)
                .build();

        @Override
        protected void onPreExecute() {
            alertDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... values) {
            alertDialog.setMessage(values[0]);
        }

        @Override
        protected IdentifyResult[] doInBackground(UUID... uuids) {
            try{
                publishProgress("Getting person group status");
                TrainingStatus trainingStatus = faceServiceClient.getPersonGroupTrainingStatus(personGroupID);

                if(trainingStatus.status != TrainingStatus.Status.Succeeded)
                {
                    Log.d("ERROR", "Person Group Training status is"+trainingStatus.status);
                    return null;
                }
                publishProgress("Identifying");
                IdentifyResult[] result = faceServiceClient.identity(personGroupID, uuids, 1);
                return result;
            } catch (ClientException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(IdentifyResult[] identifyResults) {
            alertDialog.dismiss();
            if(identifyResults != null)
            {
                for(IdentifyResult identifyResult:identifyResults)
                    new PersonDetectionTask().execute(identifyResult.candidates.get(0).personId);
            }
        }
    }
    
    // From Video
    class PersonDetectionTask extends AsyncTask<UUID, String, Person>{

        AlertDialog alertDialog = new SpotsDialog.Builder()
                .setContext(MainActivity.this)
                .setCancelable(false)
                .build();

        @Override
        protected void onPreExecute() {
            alertDialog.show();
        }


        @Override
        protected void onProgressUpdate(String... values) {
            alertDialog.setMessage(values[0]);
        }

        @Override
        protected Person doInBackground(UUID... uuids) {
            try {
                return faceServiceClient.getPerson(personGroupID, uuids[0]);
            } catch (ClientException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Person person) {
            alertDialog.dismiss();

            img_view.setImageBitmap(Utils.drawFaceRectangleWithTextOnBitmap(bitmap,
                    faceDetected,
                    person.name,
                    Color.YELLOW,
                    100));
        }
    }

    // (From Article)
    private File getPictureFile () throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String pictureFile = "JOBCOSTER_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(pictureFile, ".jpg", storageDir);
            pictureFilePath = image.getAbsolutePath();

        Log.d("filepath", pictureFilePath);

        return image;
    }

    // (From Article)
    private void sendTakePictureIntent() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(cameraIntent, REQUEST_PICTURE_CAPTURE);

            File pictureFile = null;
            try {
                pictureFile = getPictureFile();
            } catch (IOException ex) {
                Toast.makeText(this,
                        "Photo file can't be created, please try again",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (pictureFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.jobcoster.fileprovider",
                        pictureFile);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                //startActivityForResult(cameraIntent, REQUEST_PICTURE_CAPTURE);
            }
        }
    }

    // (From Article)
    /*@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICTURE_CAPTURE && resultCode == RESULT_OK) {
            File imgFile = new  File(pictureFilePath);
            if(imgFile.exists())            {
                image.setImageURI(Uri.fromFile(imgFile));
            }
        }
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sendTakePictureIntent();

        Log.d("testing", "testing");

        //Set bitmap for ImageView
        bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marco);
        img_view = (ImageView) findViewById(R.id.img_view);
        img_view.setImageBitmap(bitmap);

        btn_detect = (Button)findViewById(R.id.btn_detect);
        btn_identify = (Button)findViewById(R.id.btn_identify);

        //Event
        btn_detect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Convert bitmap to byte array
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
                new detectTask().execute(inputStream);

                btn_identify.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(faceDetected.length > 0)
                        {
                            final UUID[] faceIds = new UUID[faceDetected.length];
                            for(int i = 0; i < faceDetected.length; i++){
                                faceIds[i] = faceDetected[i].faceId;

                                new IdentificationTask().execute(faceIds);
                            }
                        }
                        else
                        {
                            Toast.makeText(MainActivity.this, "No face to detect", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

    }

}