package at.bluephoenix.BlobDetector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.util.Log;
import android.view.MotionEvent;

import at.bluephoenix.BlobDetector.Utils.Beacon;
import at.bluephoenix.BlobDetector.Utils.Blob;

public class BlobDetector {

    // TAG for debug messages
    public static final String TAG = "BlobDetector";

    private static final Integer defaultAreaThreshold = 500;
    private static final Scalar defaultColorTolerance = new Scalar(5, 40, 40);
    private static final Scalar calibrationColorTolerance = new Scalar(15, 100,
            100);
    private static final Double fov = 60.5;
    private static final Integer displayWidth = 800;
    private static final Double beaconTolerance = 50.0;

    /**
     * wrapper for findBlobs using defaultColorTolerance and
     * defaultAreaThreshold.
     */
    public static List<Blob> findBlobs(Mat rgbaFrame, Scalar color) {
        return findBlobs(rgbaFrame, color, defaultColorTolerance,
                defaultAreaThreshold);
    }

    /**
     * wrapper for findBlobs using defaultAreaThreshold.
     */
    public static List<Blob> findBlobs(Mat rgbaFrame, Scalar color,
            Scalar tolerance) {
        return findBlobs(rgbaFrame, color, tolerance, defaultAreaThreshold);
    }

    /**
     * returns a sorted list of blobs with given color. The first element is the
     * blob with the biggest area.
     * 
     * @param rgbaFrame
     *            rgba matrix of the frame
     * @param color
     *            color to look for
     * @param colorTolerance
     *            color tolerance for blob searching
     * @param areaThreshold
     *            blobs with area lower than this will be ignored
     * 
     * @return sorted list (biggest first)
     */
    public static List<Blob> findBlobs(Mat rgbaFrame, Scalar color,
            Scalar colorTolerance, Integer areaThreshold) {
        // blur image
        Mat mPyrDown = new Mat();
        Imgproc.pyrDown(rgbaFrame, mPyrDown);
        Imgproc.pyrDown(mPyrDown, mPyrDown);

        // get HSV
        Mat mHsv = new Mat();
        Imgproc.cvtColor(mPyrDown, mHsv, Imgproc.COLOR_RGB2HSV_FULL);
        mPyrDown.release();

        // calc lower / upper color boundaries
        Scalar lower = new Scalar(color.val[0] - colorTolerance.val[0],
                color.val[1] - colorTolerance.val[1], color.val[2]
                        - colorTolerance.val[2]);

        Scalar upper = new Scalar(color.val[0] + colorTolerance.val[0],
                color.val[1] + colorTolerance.val[1], color.val[2]
                        + colorTolerance.val[2]);

        // calc threshold
        Mat mMask = new Mat();
        Core.inRange(mHsv, lower, upper, mMask);
        mHsv.release();

        // dilates
        Mat mDilate = new Mat();
        Imgproc.dilate(mMask, mDilate, new Mat());

        // get contours
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mDilate, contours, new Mat(),
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
        mDilate.release();

        // fill blobs
        List<Blob> blobs = new ArrayList<Blob>();
        for (MatOfPoint m : contours) {
            Core.multiply(m, new Scalar(4, 4), m);
            Blob b = new Blob(m, color);
            if (b.getArea() >= areaThreshold)
                blobs.add(b);
        }
        Collections.sort(blobs, new Blob.compareArea());
        Collections.reverse(blobs);

        return blobs;
    }

    /**
     * find color of touched frame.
     * 
     * @param rgbaFrame
     *            the frame to search in
     * @param event
     *            touch event
     * @param width
     *            frame width
     * @param height
     *            frame height
     * 
     * @return a scalar containing the color (HSV)
     */
    public static Scalar findTouchedColor(Mat rgbaFrame, MotionEvent event,
            Integer width, Integer height) {

        int cols = rgbaFrame.cols();
        int rows = rgbaFrame.rows();

        int xOffset = (width - cols) / 2;
        int yOffset = (height - rows) / 2;

        int x = (int) event.getX() - xOffset;
        int y = (int) event.getY() - yOffset;

        // check if coords are good
        if ((x < 0) || (y < 0) || (x > cols) || (y > rows))
            return null;

        Rect touchedRect = new Rect();

        // to left corner
        touchedRect.x = (x > 4) ? x - 4 : 0;
        touchedRect.y = (y > 4) ? y - 4 : 0;

        // size
        touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols
                - touchedRect.x;
        touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows
                - touchedRect.y;

        // get sub matrix from frame
        Mat touchedRegionRgba = rgbaFrame.submat(touchedRect);

        // convert to HSV
        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv,
                Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        Scalar color = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width * touchedRect.height;
        for (int i = 0; i < color.val.length; i++)
            color.val[i] /= pointCount;

        touchedRegionRgba.release();
        touchedRegionHsv.release();

        return color;
    }

    /**
     * calibrate camera.
     * 
     * @param rgbaFrame
     *            rgba matrix of a frame
     * 
     * @return homography matrix
     */
    public static Mat calibrateCamera(Mat rgbaFrame) {
        Beacon topleft;
        Beacon topright;
        Beacon botleft;
        Beacon botright;

        Scalar Sred = new Scalar(9, 255, 176);
        Scalar Sblue = new Scalar(48, 210, 56);
        Scalar Sgreen = new Scalar(113, 148, 32);
        Scalar Syellow = new Scalar(33, 188, 210);

        List<Blob> red = findBlobs(rgbaFrame, Sred, calibrationColorTolerance);
        List<Blob> yellow = findBlobs(rgbaFrame, Syellow,
                calibrationColorTolerance);
        List<Blob> green = findBlobs(rgbaFrame, Sgreen,
                calibrationColorTolerance);
        List<Blob> blue = findBlobs(rgbaFrame, Sblue, calibrationColorTolerance);

        topleft = findBeacon(red, yellow);
        topright = findBeacon(blue, red);
        botleft = findBeacon(yellow, blue);
        botright = findBeacon(green, blue);

        if (topleft == null || topright == null || botleft == null
                || botright == null)
            return null;

        topleft.setAbsCoords(new Point(18.0, 113.0));
        topright.setAbsCoords(new Point(776.0, 89.0));
        botleft.setAbsCoords(new Point(0.0, 480.0));
        botright.setAbsCoords(new Point(800.0, 480.0));

        Point[] pixels = { new Point(18.0, 113.0), new Point(776.0, 89.0),
                new Point(0.0, 480.0), new Point(800.0, 480.0) };

        Point[] blobs = { new Point(-80.0, 150.0), new Point(82.0, 150.0),
                new Point(-16.0, 21.0), new Point(14.0, 21.0) };

        MatOfPoint2f src = new MatOfPoint2f();
        MatOfPoint2f dst = new MatOfPoint2f();
        src.fromArray(pixels);
        dst.fromArray(blobs);

        Mat h = Imgproc.getPerspectiveTransform(src, dst);

        src.release();
        dst.release();

        Log.i("homography",
                h.get(0, 0)[0] + " " + h.get(0, 1)[0] + " " + h.get(0, 2)[0]
                        + " " + h.get(1, 0)[0] + " " + h.get(1, 1)[0] + " "
                        + h.get(1, 2)[0] + " " + h.get(2, 0)[0] + " "
                        + h.get(2, 1)[0] + " " + h.get(2, 2)[0] + " ");

        return h;
    }

    /**
     * get ego centric world coords from display coords.
     * 
     * @param src
     *            point with image coords
     * @param homography
     *            homography matrix used for transformation
     * 
     * @return point with real world coords
     */
    public static Point calcEgoCentCoords(Point src, Mat homography) {
        Mat m1 = new Mat(1, 1, CvType.CV_32FC2);
        Mat m2 = new Mat(1, 1, CvType.CV_32FC2);
        m1.put(0, 0, new double[] { src.x, src.y });

        Core.perspectiveTransform(m1, m2, homography);

        Point ret = new Point(m2.get(0, 0)[0], m2.get(0, 0)[1]);

        m1.release();
        m2.release();

        return ret;
    }

    /**
     * calculate angle of a given point in ego centric system.
     * 
     * @param src
     *            source point with ego centric coords
     * 
     * @return angle relative to display center (- left / + right) in degree
     */
    public static Double calcEgoCentAngle(Point src) {
        double fact = fov / ((double) displayWidth);
        return src.x * fact - (fov / 2);
    }

    /**
     * find a beacon using two blobs.
     * 
     * @param list1
     *            list of blobs with bottom color
     * @param list2
     *            list of blobs with top color
     * 
     * @return found beacon / null if no beacon was found
     */
    public static Beacon findBeacon(List<Blob> listBot, List<Blob> listTop) {
        for (Blob bot : listBot) {
            for (Blob top : listTop) {
                Double diff;

                // check TL x pos
                diff = bot.getBox().tl().x - top.getBox().tl().x;
                if (!(-beaconTolerance <= diff && diff <= beaconTolerance))
                    continue;

                // check BR x pos
                diff = bot.getBox().br().x - top.getBox().br().x;
                if (!(-beaconTolerance <= diff && diff <= beaconTolerance))
                    continue;

                // check TL y pos
                diff = bot.getBox().tl().y - bot.getBox().height
                        - top.getBox().tl().y;
                if (!(-beaconTolerance <= diff && diff <= beaconTolerance))
                    continue;

                // check BR y pos
                diff = bot.getBox().br().y - bot.getBox().height
                        - top.getBox().br().y;
                if (!(-beaconTolerance <= diff && diff <= beaconTolerance))
                    continue;

                return new Beacon(bot, top);
            }
        }

        return null;
    }

    /**
     * uses two beacons with set coordinates to determin the robot's position.
     * will throw a NullPointerException if beacon coords not propperly set.
     * 
     * @param l
     *            left beacon
     * @param r
     *            right beacon
     * @return Point containing robot's position
     * 
     * @throws NullPointerException
     */
    public static Point calcAbsCoords(Beacon left, Beacon right)
            throws NullPointerException {

        // swap beacons if needed
        if (left.getAngle() > right.getAngle()) {
            Beacon help = left;
            left = right;
            right = help;
        }

        Point l = left.getAbsCoords();
        Point r = right.getAbsCoords();

        Double beaconDistance = Math.sqrt(Math.pow((l.x - r.x), 2)
                + Math.pow((l.y - r.y), 2));

        Double alpha = (Math.pow(right.getDistance(), 2)
                - Math.pow(left.getDistance(), 2) - Math.pow(beaconDistance, 2))
                / (-2 * left.getDistance() * beaconDistance);
        alpha = Math.acos(alpha);

        Double coordX = Math.cos(alpha) * (r.x - l.x) + Math.sin(alpha)
                * (r.y - l.y);
        Double coordY = -Math.sin(alpha) * (r.x - l.x) + Math.cos(alpha)
                * (r.y - l.y);

        coordX = left.getDistance() / beaconDistance * coordX + l.x;
        coordY = left.getDistance() / beaconDistance * coordY + l.y;

        return new Point(coordX, coordY);
    }

    /**
     * calculate absolute angle between two points.
     * 
     * @param src
     *            source
     * @param dst
     *            destination
     * 
     * @return absolute angle in degree
     */
    public static Double calcAbsAngle(Point src, Point dst) {

        Double vx = dst.x - src.x;
        Double vy = dst.y - src.y;

        Double angle = Math.atan(vy / vx);

        if (vx < 0)
            angle += Math.PI;

        if (vx >= 0 && vy < 0)
            angle += 2 * Math.PI;

        return angle * 180 / Math.PI;
    }

    /**
     * calulate absolute center view angle using a reference beacon. will throw
     * a NullPointerException if the beacon's coords are not set.
     * 
     * @param position
     *            robot absolute coords
     * @param beacon
     *            reference beacon
     * 
     * @return absolute angle in degree
     * 
     * @throws NullPointerException
     */
    public static Double calcAbsViewAngle(Point pos, Beacon beacon)
            throws NullPointerException {
        return calcAbsAngle(pos, beacon.getAbsCoords()) + beacon.getAngle();
    }
}
