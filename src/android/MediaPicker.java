package com.dmc.mediaPickerPlugin;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia;



import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * This class echoes a string called from JavaScript.
 */
public class MediaPicker extends CordovaPlugin {
    private  CallbackContext callback;
    private  int thumbnailQuality=50;
    private  int quality=100;//default original
    private  int thumbnailW=200;
    private  int thumbnailH=200;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMultipleMedia;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        if (pickMedia == null) {
            pickMedia = cordova.getActivity().registerForActivityResult(
                    new ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        // Handle the selected media URI (single selection)
                        if (uri != null) {
                            // Process the selected media
                            handleSelectedMedia(uri, callback);
                        } else {
                            // User canceled or no media selected
                            // Handle user cancellation
                            callback.success(new JSONArray());
                        }
                    });
        }

        if (pickMultipleMedia == null) {
            pickMultipleMedia = cordova.getActivity().registerForActivityResult(
                    new PickMultipleVisualMedia(),
                    uris -> {
                        // Handle the list of selected media URIs (multiple selection)
                        if (!uris.isEmpty()) {
                            // Process the selected media
                            handleSelectedMediaMultiple(uris, callback);
                        } else {
                            // User canceled or no media selected
                            // Handle user cancellation
                            callback.success(new JSONArray());
                        }
                    });
        }
        super.initialize(cordova, webView);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        getPublicArgs(args);


        switch (action) {
            case "getMedias":
            case "photoLibrary":
                this.getMedias(args, callbackContext);
                return true;
            case "takePhoto":
                this.takePhoto(args, callbackContext);
                return true;
            case "extractThumbnail":
                this.extractThumbnail(args, callbackContext);
                return true;
            case "compressImage":
                this.compressImage(args, callbackContext);
                return true;
            case "fileToBlob":
                this.fileToBlob(args.getString(0), callbackContext);
                return true;
            case "getExifForKey":
                this.getExifForKey(args.getString(0), args.getString(1), callbackContext);
                return true;
            case "getFileInfo":
                this.getFileInfo(args, callbackContext);
                return true;
        }
        return false;
    }

    private void takePhoto(JSONArray args, CallbackContext callbackContext) {
        this.callback=callbackContext;
//        Intent intent =new Intent(cordova.getActivity(), TakePhotoActivity.class); //Take a photo with a camera
//        this.cordova.startActivityForResult(this,intent,200);
    }

    private void handleSelectedMedia(Uri uri, CallbackContext callbackContext) {
        JSONArray jsonArray = new JSONArray();
        JSONObject object = new JSONObject();
        fillJsonObjectFromUri(uri, object); // Use helper function
        jsonArray.put(object);
        callbackContext.success(jsonArray);
    }

    private void handleSelectedMediaMultiple(List<Uri> uris, CallbackContext callbackContext) {
        JSONArray jsonArray = new JSONArray();

        for (int i = 0; i < uris.size(); i++) {
            Uri uri = uris.get(i);
            JSONObject object = new JSONObject();

            try {
                fillJsonObjectFromUri(uri, object);
                object.put("index", i); // Add index for multiple items
                jsonArray.put(object);
            } catch (JSONException e) {
                callbackContext.error("Error processing URI " + uri + ": " + e.getMessage());
                return; // Stop processing if one URI fails
            }
        }

        callbackContext.success(jsonArray);
    }

    private void fillJsonObjectFromUri(Uri uri, JSONObject object) {
        String path = FileHelper.getRealPath(uri, cordova);
        String name = uri.getLastPathSegment();

        if (name != null && !name.contains(".")) {
            name = name + "." +  FileHelper.getMimeType(cordova.getContext(), uri);
        }
        long size = 0;
        String mediaType = "";

        if (path != null) {
            String mimeType = FileHelper.getMimeType(uri.toString(), cordova);
            if (mimeType != null) {
                mediaType = mimeType.startsWith("video") ? "video" : mimeType.startsWith("image") ? "image" : "";
            }
        }

        try {
            if (path != null) { // Check for null path before creating File object
                File file = new File(path);
                if (file.exists()) {
                    size = file.length();
                    uri = Uri.fromFile(file);
                }
            }
        } catch (SecurityException e) {
            // Log the exception for debugging purposes
            Log.w("Media Plugin", "Security Exception getting file size: " + e.getMessage());
        }

        try {
            object.put("path", path);
            object.put("uri", uri.toString());
            object.put("size", size);
            object.put("name", name);
            object.put("mediaType", mediaType);
        } catch (JSONException e) {
            // This should not happen since we're putting primitive types, but good practice to handle it
            Log.e("Media Plugin", "JSONException in fillJsonObjectFromUri: " + e.getMessage());
        }
    }

    private String getFileExtension(String path) {
        if (path == null) return null;
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < path.length() - 1) {
            return path.substring(dotIndex + 1).toLowerCase(); // Convert to lowercase for case-insensitive comparison
        }
        return null;
    }

    private void getMedias(JSONArray args, CallbackContext callbackContext) throws JSONException {
//101=picker image and video , 100=image , 102=video
        this.callback=callbackContext;

        int selectMode = args.getJSONObject(0).getInt("selectMode");
        switch (selectMode) {
            case 101:
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo.INSTANCE)
                        .build());
                break;
            case 100:
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build());
                break;

            case 102:
                pickMedia.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                        .build());
                break;
        }
    }

    public  void getPublicArgs(JSONArray args){
        JSONObject jsonObject=new JSONObject();
        if (args != null && args.length() > 0) {
            try {
                jsonObject = args.getJSONObject(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                thumbnailQuality = jsonObject.getInt("thumbnailQuality");
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                thumbnailW = jsonObject.getInt("thumbnailW");
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                thumbnailH = jsonObject.getInt("thumbnailH");
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                quality = jsonObject.getInt("quality");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


//    @Override
//    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
//        super.onActivityResult(requestCode, resultCode, intent);
//        try {
//            if(requestCode==200&&resultCode==PickerConfig.RESULT_CODE){
//                final ArrayList<Media> select=intent.getParcelableArrayListExtra(PickerConfig.EXTRA_RESULT);
//                final JSONArray jsonArray=new JSONArray();
//
//                cordova.getThreadPool().execute(new Runnable() {
//                    public void run() {
//                        try {
//                            int index=0;
//                            for(Media media:select){
//                                JSONObject object=new JSONObject();
//                                object.put("path",media.path);
//                                object.put("uri",Uri.fromFile(new File(media.path)));//Uri.fromFile(file).toString() || [NSURL fileURLWithPath:filePath] absoluteString]
//                                object.put("size",media.size);
//                                object.put("name",media.name);
//                                object.put("index",index);
//                                object.put("mediaType",media.mediaType==3?"video":"image");
//                                jsonArray.put(object);
//                                index++;
//                            }
//                            MediaPicker.this.callback.success(jsonArray);
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                });
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }

    public  void extractThumbnail(JSONArray args, CallbackContext callbackContext){
        JSONObject jsonObject=new JSONObject();
        if (args != null && args.length() > 0) {
            try {
                jsonObject = args.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                thumbnailQuality = jsonObject.getInt("thumbnailQuality");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                String path =jsonObject.getString("path");
                jsonObject.put("exifRotate",getBitmapRotate(path));
                int mediatype = "video".equals(jsonObject.getString("mediaType"))?3:1;
                jsonObject.put("thumbnailBase64",extractThumbnail(path,mediatype,thumbnailQuality));
            } catch (Exception e) {
                e.printStackTrace();
            }
            callbackContext.success(jsonObject);
        }
    }

    public  String extractThumbnail(String path,int mediaType,int quality) {
        String encodedImage = null;
        try {
            Bitmap thumbImage;
            if (mediaType == 3) {
                thumbImage = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            } else {
                thumbImage = BitmapFactory.decodeFile(path);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            thumbImage.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            byte[] imageBytes = baos.toByteArray();
            encodedImage = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            baos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return encodedImage;
    }

    public void  compressImage( JSONArray args, CallbackContext callbackContext){
        this.callback=callbackContext;
        try {
            JSONObject jsonObject = args.getJSONObject(0);
            String path = jsonObject.getString("path");
            int quality=jsonObject.getInt("quality");
            if(quality<100) {
                File file = compressImage(path, quality);
                jsonObject.put("path", file.getPath());
                jsonObject.put("uri", Uri.fromFile(new File(file.getPath())));
                jsonObject.put("size", file.length());
                jsonObject.put("name", file.getName());
                callbackContext.success(jsonObject);
            }else{
                callbackContext.success(jsonObject);
            }
        } catch (Exception e) {
            callbackContext.error("compressImage error"+e);
            e.printStackTrace();
        }
    }

    public void  getFileInfo( JSONArray args, CallbackContext callbackContext){
        this.callback=callbackContext;
        try {
            String type=args.getString(1);
            File file;
            if("uri".equals(type)){
                file=new File(FileHelper.getRealPath(args.getString(0),cordova));
            }else{
                file=new File(args.getString(0));
            }
            JSONObject jsonObject=new JSONObject();
            jsonObject.put("path", file.getPath());
            jsonObject.put("uri", Uri.fromFile(new File(file.getPath())));
            jsonObject.put("size", file.length());
            jsonObject.put("name", file.getName());
            String mimeType = FileHelper.getMimeType(jsonObject.getString("uri"),cordova);
            String mediaType = mimeType.indexOf("video")!=-1?"video":"image";
            jsonObject.put("mediaType",mediaType);
            callbackContext.success(jsonObject);
        } catch (Exception e) {
            callbackContext.error("getFileInfo error"+e);
            e.printStackTrace();
        }
    }

    public File compressImage(String path,int quality){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String compFileName="dmcMediaPickerCompress"+System.currentTimeMillis()+".jpg";
        File file= new File(cordova.getActivity().getExternalCacheDir(),compFileName);
        rotatingImage(getBitmapRotate(path),BitmapFactory.decodeFile(path)).compress(Bitmap.CompressFormat.JPEG, quality, baos);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(baos.toByteArray());
            fos.flush();
            fos.close();
        } catch (Exception e) {
            MediaPicker.this.callback.error("compressImage error"+e);
            e.printStackTrace();
        }
        return  file;
    }

    public  int getBitmapRotate(String path) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return degree;
    }

    private static Bitmap rotatingImage(int angle, Bitmap bitmap) {
        //rotate image
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);

        //create a new image
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix,
                true);
    }


    public  byte[] extractThumbnailByte(String path,int mediaType,int quality) {

        try {
            Bitmap thumbImage;
            if (mediaType == 3) {
                thumbImage = ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            } else {
                thumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(path), thumbnailW, thumbnailH);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            thumbImage.compress(Bitmap.CompressFormat.JPEG, quality, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public  void getExifForKey(String path,String tag, CallbackContext callbackContext) {
        try {
            ExifInterface exifInterface = new ExifInterface(path);
            String object = exifInterface.getAttribute(tag);
            callbackContext.success(object);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public  String fileToBase64(String path) {
        byte[] data = null;
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(path));
            data = new byte[in.available()];
            in.read(data);
            in.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    public  void fileToBlob(String path, CallbackContext callbackContext) {
        byte[] data = null;
        try {
            BufferedInputStream in = new BufferedInputStream(new FileInputStream(path));
            data = new byte[in.available()];
            in.read(data);
            in.close();
        } catch (IOException e) {
            callbackContext.error("fileToBlob "+e);
            e.printStackTrace();
        }
        callbackContext.success(data);
    }
}

