package com.example.aniworld;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.CamcorderProfile;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.common.FirebaseMLException;
import com.google.firebase.ml.common.modeldownload.FirebaseModelDownloadConditions;
import com.google.firebase.ml.common.modeldownload.FirebaseModelManager;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.automl.FirebaseAutoMLRemoteModel;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class Main2Activity extends AppCompatActivity {
    private ModelRenderable renderable;
    private MediaPlayer mediaPlayer;
    private ImageView playIcon;
    private ImageView scan;
    private ArFragment arFragment;
    private FirebaseAutoMLRemoteModel remoteModel;
    private FirebaseVisionImageLabeler labeler;
    private FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder optionBuilder;
    private ProgressDialog progressDialog;
    private FirebaseModelDownloadConditions conditions;
    private FirebaseVisionImage image;
    private String modelToBeLoaded;
    private String finalModel;
    private FirebaseStorage storage;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        checkConnection();
        FirebaseApp.initializeApp(this);
        scan=findViewById(R.id.downloadBtn);
        scan.setOnClickListener(v -> fromRemoteModel());
        arFragment= (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            AnchorNode anchorNode=new AnchorNode(hitResult.createAnchor());
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            TransformableNode transformableNode=new TransformableNode(arFragment.getTransformationSystem());
            transformableNode.getScaleController().setMaxScale(4.00f);
            transformableNode.getScaleController().setMinScale(0.01f);
            transformableNode.setParent(anchorNode);
            transformableNode.setRenderable(renderable);
            arFragment.getArSceneView().getScene().addChild(anchorNode);
            transformableNode.select();
        });
        playIcon=findViewById(R.id.playIcon);
        playMusic(playIcon);
    }
    //Internet connectivity
    public void checkConnection()
    {
        ConnectivityManager manager=(ConnectivityManager)getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork=manager.getActiveNetworkInfo();
        if(activeNetwork!=null){
            if(activeNetwork.getType()==ConnectivityManager.TYPE_WIFI){
                Toast.makeText(this, "Wifi Enabled", Toast.LENGTH_SHORT).show();
            }
            else if(activeNetwork.getType()==ConnectivityManager.TYPE_MOBILE){
                Toast.makeText(this,"Data Network Enabled",Toast.LENGTH_SHORT).show();
            }
        }
        else
        {
            Toast.makeText(this,"No Internet Connection",Toast.LENGTH_SHORT).show();
        }
    }
    private void playMusic(ImageView playIcon) {
        playIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!mediaPlayer.isPlaying())
                {
                    mediaPlayer.start();
                    playIcon.setImageResource(R.drawable.ic_pause_button);
                }
                else
                {
                    mediaPlayer.pause();
                    playIcon.setImageResource(R.drawable.ic_music_note);
                }
            }
        });
        mediaPlayer=MediaPlayer.create(this,R.raw.jungle);
        mediaPlayer.setLooping(true);
    }

    private void buildModel(File file) {
        RenderableSource renderableSource=RenderableSource
                .builder()
                .setSource(this, Uri.parse(file.getPath()), RenderableSource.SourceType.GLB)
                .setRecenterMode(RenderableSource.RecenterMode.ROOT)
                .build();
        ModelRenderable
                .builder()
                .setSource(this,renderableSource)
                .setRegistryId(file.getPath())
                .build()
                .thenAccept(modelRenderable -> {
                    Toast.makeText(this,"Model Built",Toast.LENGTH_SHORT).show();
                    renderable=modelRenderable;
                });
    }
    //Scanning image and getting image info
    private void fromRemoteModel() {
        showProgressBar();
        remoteModel=new FirebaseAutoMLRemoteModel.Builder("Animal_Images_202066152522").build();
        conditions=new FirebaseModelDownloadConditions.Builder().build();
        FirebaseModelManager.getInstance().download(remoteModel,conditions)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        CropImage.activity().start(Main2Activity.this);
                    }
                });
    }

    private void showProgressBar() {
        progressDialog=new ProgressDialog(Main2Activity.this);
        progressDialog.setMessage("Loading....");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            CropImage.ActivityResult result=CropImage.getActivityResult(data);
            if(resultCode==RESULT_OK) {
                if (result != null) {
                    Uri uri = result.getUri();
                    setLabelerFromRemoteLabel(uri);
                } else
                    progressDialog.cancel();
            }else
                progressDialog.cancel();
        }
    }

    private void setLabelerFromRemoteLabel(final Uri uri) {
        FirebaseModelManager.getInstance().isModelDownloaded(remoteModel)
                .addOnSuccessListener(new OnSuccessListener<Boolean>() {
                    @Override
                    public void onSuccess(Boolean isDownloaded) {
                        if (isDownloaded) {
                            optionBuilder = new FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder(remoteModel);
                            FirebaseVisionOnDeviceAutoMLImageLabelerOptions options = optionBuilder
                                    .setConfidenceThreshold(0.0f)
                                    .build();
                            try {
                                labeler = FirebaseVision.getInstance().getOnDeviceAutoMLImageLabeler(options);
                                image = FirebaseVisionImage.fromFilePath(Main2Activity.this, uri);
                                processImageLabeler(labeler, image);
                            } catch (FirebaseMLException | IOException exception) {
                                exception.printStackTrace();
                            }
                        }
                    }
                });
    }

    private void processImageLabeler(FirebaseVisionImageLabeler labeler, FirebaseVisionImage image) {
        labeler.processImage(image)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                        progressDialog.cancel();
                        for (FirebaseVisionImageLabel label : labels) {
                            String eachlabel = label.getText().toLowerCase();
                            modelToBeLoaded=eachlabel;
                            break;
                        }
                        downloadModelFromFirebase();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(Main2Activity.this, "Something went wrong|" + e,Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void downloadModelFromFirebase() {
        finalModel=modelToBeLoaded+".glb";
        storage=FirebaseStorage.getInstance();
        StorageReference modelRef=storage.getReference().child(finalModel);
        try {
            File file = File.createTempFile(modelToBeLoaded, "glb");
            modelRef.getFile(file).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
                    buildModel(file);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
