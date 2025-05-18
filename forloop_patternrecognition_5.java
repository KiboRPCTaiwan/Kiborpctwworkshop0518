package jp.jaxa.iss.kibo.rpc.sampleapk;

import jp.jaxa.iss.kibo.rpc.api.KiboRpcService;

import gov.nasa.arc.astrobee.types.Point;
import gov.nasa.arc.astrobee.types.Quaternion;

import org.opencv.core.Mat;

// new imports
import android.util.Log;

import java.util.List;
import java.util.ArrayList;


// new OpenCV imports
import org.opencv.aruco.Dictionary;
import org.opencv.aruco.Aruco;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import java.io.InputStream;
import java.io.IOException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.opencv.android.Utils;
import org.opencv.imgproc.Imgproc;
import org.opencv.core.Size;
import org.opencv.core.Core;


/**
 * Class meant to handle commands from the Ground Data System and execute them in Astrobee.
 */

public class YourService extends KiboRpcService {

    // The TAG is used for logging.
    // You can use it to check the log in the Android Studio.
    private final String TAG = this.getClass().getSimpleName();

    // Type all the image in the template folder.
    //'coin.png', 'compass.png', 'coral.png', 'crystal.png',
    // 'diamond.png', 'emerald.png', 'fossil.png', 'key.png',
    // 'letter.png', 'shell.png', 'treasure_box.png']
    private final String[] TEMPLATE_FILE_NAMES = {
            "coin.png",
            "compass.png",
            "coral.png",
            // "crystal.png",
            // "diamond.png",
            // "emerald.png",
            "fossil.png",
            "key.png",
            "letter.png",
            "shell.png",
            "treasure_box.png"
    };

    private final String[] TEMPLATE_NAMES = {
            "coin",
            "compass",
            "coral",
            // "crystal",
            // "diamond",
            // "emerald",
            "fossil",
            "key",
            "letter",
            "shell",
            "treasure_box"
    };

    private final Point[] AREA_POINTS = {
        new Point(10.9d, -10.0000d, 5.195d),    // Area 1
        new Point(10.925d, -8.875d, 4.602d),    // Area 2
        new Point(10.925d, -7.925d, 4.60093d),    // Area 3
        new Point(10.766d, -6.852d, 4.945d)     // Area 4
    };

    private final Quaternion[] AREA_QUATERNIONS = {
        new Quaternion(0f, 0f, -0.707f, 0.707f),    // Area 1
        new Quaternion(0f, 0.707f, 0f, 0.707f),     // Area 2
        new Quaternion(0f, 0.707f, 0f, 0.707f),     // Area 3
        new Quaternion(0f, 0f, 1f, 0f)      // Area 4
    };

    @Override
    protected void runPlan1(){
        // Log the start of the mission.
        Log.i(TAG, "Start mission");
        // The mission starts.
        api.startMission();

        // Prepare the variables to store treasure item locations
        String[] foundItems = new String[AREA_POINTS.length];
        int[] itemCounts = new int[AREA_POINTS.length];
        
        // Load template images
        Mat[] templates = new Mat[TEMPLATE_FILE_NAMES.length];
        for (int i = 0; i < TEMPLATE_FILE_NAMES.length; i++) {
            try{
                // Open the template image file in Bitmap from the file name and convert to Mat
                InputStream inputStream = getAssets().open(TEMPLATE_FILE_NAMES[i]);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                Mat mat = new Mat();
                Utils.bitmapToMat(bitmap, mat);

                // Convert the Bitmap to grayscale
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2GRAY);

                // Assign to an array of templates
                templates[i] = mat;

                // Release the InputStream
                inputStream.close();

            } catch (IOException e) {
                Log.e(TAG, "Error loading template image: " + TEMPLATE_FILE_NAMES[i], e);
                e.printStackTrace();
            }
        }

        // Visit each area
        for (int areaId = 0; areaId < AREA_POINTS.length; areaId++) {
            // Move to the current area
            api.moveTo(AREA_POINTS[areaId], AREA_QUATERNIONS[areaId], false);

            // Get a camera image
            Mat image = api.getMatNavCam();
            // Save the image for debugging
            api.saveMatImage(image, "area_" + (areaId + 1) + ".png");

            /* ********************************************** */
            /* Start image recognition process */
            /* ********************************************** */
            // AR tag detection
            Dictionary dictionary = Aruco.getPredefinedDictionary(Aruco.DICT_5X5_250);
            List<Mat> corners = new ArrayList<>();
            Mat ids = new Mat();
            Aruco.detectMarkers(image, dictionary, corners, ids);

            // Undistort the image
            Mat cameraMatrix = new Mat(3,3,CvType.CV_64F);
            cameraMatrix.put(0, 0, api.getNavCamIntrinsics()[0]);

            Mat cameraCoefficients = new Mat(1, 5, CvType.CV_64F);
            cameraCoefficients.put(0, 0, api.getNavCamIntrinsics()[1]);
            cameraCoefficients.convertTo(cameraCoefficients, CvType.CV_64F);

            Mat undistortImg = new Mat();
            Calib3d.undistort(image, undistortImg, cameraMatrix, cameraCoefficients);

            // Number of matches for each template
            int[] numMatches = new int[TEMPLATE_FILE_NAMES.length];

            // Get the number of template matches
            for (int tempNum = 0; tempNum < templates.length; tempNum++) {
                // number of matches
                int matchCount = 0;
                // Coordinates of the match location
                List<org.opencv.core.Point> matchLocations = new ArrayList<>();

                // Load the template image
                Mat template = templates[tempNum].clone();
                // Target image is the undistorted image
                Mat targetImg = undistortImg.clone();

                // Pattern matching
                int widthMin = 20; //[px]
                int widthMax = 100; //[px]
                int changeWidth = 5; //[px]
                int changeAngle = 45; //[px]

                for (int size=widthMin; size <= widthMax; size += changeWidth){
                    for (int angle=0; angle<360; angle+=changeAngle){
                        // Resize the template image
                        Mat resizedTemplate = scalingresizeImage(template, size);
                        // Rotate the template image
                        Mat rotatedTemplate = rotImg(resizedTemplate, angle);

                        // Perform template matching
                        Mat result = new Mat();
                        Imgproc.matchTemplate(targetImg, rotatedTemplate, result, Imgproc.TM_CCOEFF_NORMED);

                        // Thresholding
                        double threshold = 0.7;
                        Core.MinMaxLocResult mmlr = Core.minMaxLoc(result);

                        double maxVal = mmlr.maxVal;

                        if (maxVal >= threshold) {
                            // Create a mask for the detected region
                            Mat thresholdResult = new Mat();
                            Imgproc.threshold(result, thresholdResult, threshold, 1, Imgproc.THRESH_TOZERO);

                            // Get coordinates of the detected region
                            for(int y=0; y<thresholdResult.rows(); y++){
                                for(int x=0; x<thresholdResult.cols(); x++){
                                    if(thresholdResult.get(y, x)[0] > 0){
                                        matchLocations.add(new org.opencv.core.Point(x, y));
                                    }
                                }
                            }
                            
                            // Release resources
                            thresholdResult.release();
                        }
                        
                        // Release resources
                        result.release();
                        rotatedTemplate.release();
                        resizedTemplate.release();
                    }
                }

                List<org.opencv.core.Point> filteredMatches = removeDuplicates(matchLocations);
                matchCount += filteredMatches.size();

                // Number of matches
                numMatches[tempNum] = matchCount;
                
                // Release resources
                template.release();
                targetImg.release();
            }
            
            // Find the most matched template
            int mostMatchTemplateNum = getMxIndex(numMatches);
            
            // Store the results
            foundItems[areaId] = TEMPLATE_NAMES[mostMatchTemplateNum];
            itemCounts[areaId] = numMatches[mostMatchTemplateNum];
            /* ********************************************** */
            /* End image recognition process  and Reprot*/
            // Report what was found in this area
            api.setAreaInfo(areaId + 1, foundItems[areaId], itemCounts[areaId]);
            
            // Log the results for debugging
            Log.i(TAG, "Area " + (areaId + 1) + ": Found " + itemCounts[areaId] + " of " + foundItems[areaId]);
            
            // Release resources
            image.release();
            undistortImg.release();
            cameraMatrix.release();
            cameraCoefficients.release();
            ids.release();
            for (Mat corner : corners) {
                corner.release();
            }
            
        }

        // Move to the astronaut for reporting
        Point astronautPoint = new Point(11.143, -6.7607, 4.9654);
        Quaternion astronautQuaternion = new Quaternion(0f, 0f, 0.707f, 0.707f);
        api.moveTo(astronautPoint, astronautQuaternion, false);
        api.reportRoundingCompletion();

        // Get information about the target item from the astronaut
        // Mat targetImage = api.getMatNavCam();
        // api.saveMatImage(targetImage, "target_item.png");
        
        // TODO: Implement logic to identify the target item from astronaut's information
        // For now, let's assume the target is in the first area
        // String targetItem = TEMPLATE_NAMES[0]; // Replace with actual target item identification
        
        // Notify that we recognized the target item
        api.notifyRecognitionItem();

        // // Find which area contains the target item and move there
        // int targetArea = 0; // Default to first area if not found
        // for (int i = 0; i < foundItems.length; i++) {
        //     if (foundItems[i].equals(targetItem)) {
        //         targetArea = i;
        //         break;
        //     }
        // }
        
        // Move to the area with the target item
        // api.moveTo(AREA_POINTS[targetArea], AREA_QUATERNIONS[targetArea], false);
        
        // Take a snapshot of the target item
        api.takeTargetItemSnapshot();
        
        // Release resources
        // targetImage.release();
        // for (Mat template : templates) {
        //     if (template != null) {
        //         template.release();
        //     }
        // }
    }

    @Override
    protected void runPlan2(){
        // write your plan 2 here.
    }

    @Override
    protected void runPlan3(){
        // write your plan 3 here.
    }

    private Mat scalingresizeImage(Mat image, int width) {
        int height = (int) ((double) image.rows() / image.cols() * width);
        // Create a new Mat object to hold the resized image
        Mat resizedImage = new Mat();

        // Resize the image using the specified width and height
        Imgproc.resize(image, resizedImage, new Size(width, height));

        return resizedImage;
    }

    private Mat rotImg(Mat img, int angle) {
        // Get the center of the image
        org.opencv.core.Point center = new org.opencv.core.Point(img.cols() / 2, img.rows() / 2);

        // Create a rotation matrix
        Mat rotMat = Imgproc.getRotationMatrix2D(center, angle, 1.0);

        // Create a new Mat object to hold the rotated image
        Mat rotatedImg = new Mat();

        // Rotate the image using the rotation matrix
        Imgproc.warpAffine(img, rotatedImg, rotMat, img.size());
        
        // Release resources
        rotMat.release();

        return rotatedImg;
    }

    private List<org.opencv.core.Point> removeDuplicates(List<org.opencv.core.Point> points) {
        double length = 10; // within 10 px
        List<org.opencv.core.Point> uniquePoints = new ArrayList<>();
        for (org.opencv.core.Point point : points) {
            boolean isIncluded = false;
            for (org.opencv.core.Point uniquePoint : uniquePoints) {
                double distance = calculateDistance(point, uniquePoint);

                if (distance <= length){
                    isIncluded = true;
                    break;
                }
            }

            if (!isIncluded) {
                uniquePoints.add(point);
            }
        }
        return uniquePoints;
    }

    private double calculateDistance(org.opencv.core.Point p1, org.opencv.core.Point p2) {
        // Calculate the distance between two points using the Euclidean distance formula
        return Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
    }

    private int getMxIndex(int[] array){
        int max = 0;
        int maxIndex = 0;

        for (int i = 0; i < array.length; i++) {
            if (array[i] > max) {
                max = array[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }
}