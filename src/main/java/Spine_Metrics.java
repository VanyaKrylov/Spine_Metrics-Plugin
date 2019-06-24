import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.*;
import ij.plugin.PointToolOptions;
import ij.plugin.filter.Binary;
import ij.plugin.filter.Convolver;
import ij.plugin.filter.Filters;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.ThresholdAdjuster;
import ij.process.BinaryProcessor;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Optional;

public class Spine_Metrics extends PlugInFrame implements MouseListener {

    private ImageCanvas canvas;
    private static Frame instance;
    private ImagePlus img;
    private ImageWindow win;
    private ImageProcessor imageProcessor;
    private boolean toFindEdges;
    private int baseLineOrientation; /**Horizontal = 0, Vertival = 1 */

    private static final Point nullPoint = new Point(-1, -1);

    private int maxDistanceToBaselineEnd = 5;

    private Point p_l, p_r, p_c;
    //private Optional<Point> p_l, p_r, p_c;
    private int prevImgHash;

    public Spine_Metrics() {
        super("Testing");
        instance = this;

        try {
            img = WindowManager.getCurrentImage();
            ImagePlus img2 = img.duplicate();
            win = img.getWindow();
            canvas = win.getCanvas();
            imageProcessor = img.getProcessor();
            imageProcessor.setColor(128);
            Convolver cv = new Convolver();
            cv.setNormalize(false);
            float[] K = { -0.125f, -0.125f, -0.125f,
                          -0.125f,    1.0f,    -0.125f,
                          -0.125f, -0.125f, -0.125f };
            cv.convolve(imageProcessor, K, 3, 3);
            IJ.setThreshold(0, 255, "Over/Under");
            //IJ.setThreshold(1, 255);
            int H = img.getHeight();
            int W  = img.getWidth();
            for (int i = 0; i < H; i++) {
                for (int j = 0; j < W; j++) {
                    if (imageProcessor.get(i,j) > 0)
                        imageProcessor.set(i,j,255);
                }
            }
            IJ.run(img2, "Skeletonize (2D/3D)", "");

            //imageProcessor.copyBits(img2.getProcessor(), 0, 0, Blitter.MAX);

            canvas.imageUpdate(img.getImage(), 32, 0, 0, W, H);
            prevImgHash = img.hashCode();
            toFindEdges = true;
            p_l = p_c = p_r = nullPoint;
            //p_l = p_c = p_r = Optional.empty();
            canvas.addMouseListener(this);
        } catch (Exception e) {
            IJ.showMessage("Error", e.getStackTrace().toString());
        }
    }

    //PointToolOptions

    public Point getP_l() {
        return p_l;
    }

    public Point getP_c() {
        return p_c;
    }

    public Point getP_r() {
        return p_r;
    }

    /**
     * @param e - basically MouseClicked event
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        imageProcessor.setColor(128);
        Point p = nullPoint;
        int x = canvas.offScreenX(e.getX());
        int y = canvas.offScreenY(e.getY());
        img = canvas.getImage();

        p = new Point(x,y);
        if (verifyPoint(img, p)) {
            imageProcessor.drawDot(p.x, p.y);
            if (p_l.equals(nullPoint))
                p_l = p;
            else {
                baseLineOrientation = 1;
                if (isBaseLineHorizontal(p_l.x, p_l.y, p.x, p.y)) {
                    baseLineOrientation = 0;
                    if (p.x < p_l.x) {
                        p_r = p_l;
                        p_l = p;
                    } else
                        p_r = p;
                } else if (p.y < p_l.y) {
                    p_r = p_l;
                    p_l = p;
                } else
                    p_r = p;
                process2(img);
            }
        } else {
            IJ.showMessage("Wrong Input", "Can't choose background pixels");
        }


            //img = WindowManager.getCurrentImage();

        /*if (prevImgHash != img.hashCode()) {
            prevImgHash = img.hashCode();
            p_l = nullPoint;
            p_c = nullPoint;
            p_r = nullPoint;

        }*/

            /*p = (verifyPoint(img, new Point(x, y))) ?
                    new Point(x, y) :
                    autoCorrectPoint(img, new Point(x, y));

            if (!p.equals(nullPoint)) {
                if (p_l.equals(nullPoint)) {
                    p_l = p;
                    imageProcessor.drawDot(p_l.x, p_l.y);
                } else if (p_r.equals(nullPoint)) {
                    p_r = p;
                    if (!verifyBasePoints(img, p_l, p_r)) {
                        IJ.showMessage("Wrong Input", "Error, the length of the base line is too big\n Discarding previous input");
                        p_l = nullPoint;
                        p_r = nullPoint;
                    } else {
                        imageProcessor.drawLine(p_l.x, p_l.y, p_r.x, p_r.y);
                        img.updateImage();

                        //new logic for edges
                        process(img);

                        p_l = nullPoint;
                        p_c = nullPoint;
                        p_r = nullPoint;
                    }
                } /* else if (p_c.equals(nullPoint)) {
                    p_c = p;
                    imageProcessor.drawDot(p_c.x, p_c.y);
                    //IJ.showMessage(" p_l = " + p_l.x + " " + p_l.y + " p_r = " + p_r.x + " " + p_r.y + " p_c = " + p_c.x + " " + p_c.y);
                    process(img);

                    p_l = nullPoint;
                    p_c = nullPoint;
                    p_r = nullPoint;
                }  else {
                    e.consume();
                    //process(img);
                    //IJ.showMessage(" p_l = " + p_l.toString() + " p_r = " + p_r.toString() + " p_c = " + p_c.toString());
                }
            } else
                IJ.showMessage("Wrong Input", "Can't choose background pixels");

             */

    }


    boolean isBaseLineHorizontal(int x0, int y0, int x1, int y1) {
        if (y0 == y1) return true;
        return (Math.abs(x1-x0)/Math.abs(y1-y0) >= 1);
    }


    void process2(ImagePlus img) {
        //imageProcessor.setColor(226);
        Point nextPoint = p_l;
        ArrayList<Point> edge = new ArrayList<>();
        edge.add(new Point(p_l));
        boolean isMovingUp = false;
        //int arrayPad = 1;
        if (baseLineOrientation == 0) {

            while (verifyPoint(img, nextPoint.x+1, nextPoint.y)) {
                nextPoint.x += 1;
                edge.add(new Point(nextPoint));
                //if (arrayPad > 0) arrayPad--;
            }

            if (verifyPoint(img, nextPoint.x, nextPoint.y-1)) {
                nextPoint.y -= 1;
                isMovingUp = true;
                edge.add(new Point(nextPoint));
            } else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)) {
                nextPoint.y+=1;
                edge.add(new Point(nextPoint));
            } else {
                IJ.showMessage("Error", "Unable to recognize srtucture");
                return;
            }

            while (!nextPoint.equals(p_r)) {

                /**LEFT*/
                if (verifyPoint(img, nextPoint.x-1, nextPoint.y)
                        && !isPrevious(edge.get(edge.size()-2), nextPoint.x-1, nextPoint.y)
                        && !isSingle4Neighboured(nextPoint.x-1, nextPoint.y)) {
                    /*if (nextPoint.x-1 == p_r.x && nextPoint.y == p_r.y)
                        nextPoint = p_r;
                    else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)) {
                        if (nextPoint.y+1 < p_r.y && (Math.abs(p_l.x - nextPoint.x) > Math.abs(p_r.x - nextPoint.x)) )
                            nextPoint.y += 1;
                        else
                            nextPoint.x -= 1;
                    } else if (verifyPoint(img, nextPoint.x, nextPoint.y-1)) {
                        if (nextPoint.y-1 > p_r.y && (Math.abs(p_l.x - nextPoint.x) > Math.abs(p_r.x - nextPoint.x)))
                            nextPoint.y -= 1;
                        else
                            nextPoint.x -= 1;
                    } else
                     */
                    nextPoint.x -= 1;
                    edge.add(new Point(nextPoint));
                }

                /**UP*/
                else if (verifyPoint(img, nextPoint.x, nextPoint.y-1)
                        && !isPrevious(edge.get(edge.size()-2), nextPoint.x, nextPoint.y-1)
                        && !isSingle4Neighboured(nextPoint.x, nextPoint.y-1)) {
                    /*if (nextPoint.x == p_r.x && nextPoint.y-1 == p_r.y)
                        nextPoint = p_r;
                    else if (verifyPoint(img, nextPoint.x+1, nextPoint.y)) {
                        if (nextPoint.y > p_r.y && (Math.abs(p_l.x+1 - nextPoint.x) > Math.abs(p_r.x+1 - nextPoint.x)) )
                            nextPoint.y += 1;
                        else
                            nextPoint.x -= 1;
                    } else
                     */
                    if (verifyPoint(img, nextPoint.x, nextPoint.y+1) && !isMovingUp)
                        nextPoint.y += 1;
                    else {
                        nextPoint.y -= 1;
                        isMovingUp = true;
                    }
                    edge.add(new Point(nextPoint));
                }

                /**RIGHT*/
                else if (verifyPoint(img, nextPoint.x+1, nextPoint.y)
                        && !isPrevious(edge.get(edge.size()-2), nextPoint.x+1, nextPoint.y)
                        && !isSingle4Neighboured(nextPoint.x+1, nextPoint.y)) {
                    nextPoint.x += 1;
                    edge.add(new Point(nextPoint));
                }

                /**DOWN*/
                else if (verifyPoint(img, nextPoint.x, nextPoint.y+1)
                        && !isPrevious(edge.get(edge.size()-2), nextPoint.x, nextPoint.y+1)
                        && !isSingle4Neighboured(nextPoint.x, nextPoint.y+1)) {
                    isMovingUp=false;
                    nextPoint.y += 1;
                    edge.add(new Point(nextPoint));
                }

                else
                    break;
            }
        }

        int[] xp = new int[edge.size()/2 + 1];
        int[] yp = new int[edge.size()/2 + 1];
        int nPoints = 0;
        LineSegment maxLine = new LineSegment(p_l.x, p_l.y, p_r.x, p_r.y);
        boolean isNewMax = false;
        for (int i = 0; i < edge.size()/2; i++) {
            LineSegment line = new LineSegment(edge.get(i).x, edge.get(i).y, edge.get(edge.size()-i-1).x, edge.get(edge.size()-i-1).y);
            if (line.getLength() > maxLine.getLength()) {
                maxLine = line;
                isNewMax = true;
            }
            //imageProcessor.drawDot((int)line.midPoint().x, (int)line.midPoint().y);
            xp[i] = (int)line.midPoint().x;
            yp[i] = (int)line.midPoint().y;
            nPoints++;
        }
        PolygonRoi skeleton = new PolygonRoi(xp, yp, nPoints, Roi.POLYLINE);
        int[] xp2 = new int[2];
        int[] yp2 = new int[2];
        xp2[0] = (int)maxLine.p0.x;
        xp2[1] = (int)maxLine.p1.x;
        yp2[0] = (int)maxLine.p0.y;
        yp2[1] = (int)maxLine.p1.y;
        PolygonRoi maxPolyLine = new PolygonRoi(xp2, yp2, 2, Roi.POLYLINE);
        imageProcessor.drawRoi(skeleton);
        imageProcessor.drawRoi(maxPolyLine);
        //imageProcessor.drawLine((int)maxLine.p0.x, (int)maxLine.p0.y, (int)maxLine.p1.x, (int)maxLine.p1.y);

        String string = "";
        for (Point p : edge) {
            imageProcessor.drawDot(p.x, p.y);
             string+=p.x+","+p.y+";";
        }

        p_l = p_r = nullPoint;

        if (isNewMax)
            string = "Shroomie spine";
        else
            string = "Penek spine";
        IJ.showMessage("Result", string);
        //IJ.showMessage(string);
        //IJ.showMessage("Result Edge", edge.get(edge.size()-1) + "\n" + p_r);
    }

    boolean isPrevious(Point previous, int x, int y) {
        return (previous.x == x && previous.y == y);
    }

    boolean isSingle4Neighboured(int x, int y) {
        int count = 0;
        if (verifyPoint(img,x-1,y))
            count++;
        if (verifyPoint(img,x+1,y))
            count++;
        if (verifyPoint(img,x,y-1))
            count++;
        if (verifyPoint(img,x,y+1))
            count++;
        return count <= 1;
    }

    //public for testing
    /**
     * Spine metrics processing
     * @param imp
     * @return
     */
    public int process(ImagePlus imp) {

        Coordinate baseLeftBoundary = new Coordinate(p_l.x, p_l.y),baseRightBoundary = new Coordinate(p_r.x, p_r.y);
        LineSegment baseLine = new LineSegment(baseLeftBoundary, baseRightBoundary);
        Coordinate spineCenter = new Coordinate(p_c.x, p_c.y);
        Coordinate perpendPointProj = baseLine.closestPoint(spineCenter);

        imageProcessor.drawLine((int)perpendPointProj.x, (int)perpendPointProj.y, (int)spineCenter.x, (int)spineCenter.y);

        int offset = ( perpendPointProj.y < spineCenter.y ) ? 1 : -1;

/*
        Coordinate midPoint = baseLine.midPoint();
        LineSegment spineLine = baseLine;
        while ( verifyCoordinate(imp, midPoint = spineLine.pointAlongOffset(spineLine.getLength()/2, offset)) ) {
            while (  )
        } */

        LineSegment perpendicularToBase = new LineSegment(spineCenter, perpendPointProj);
        Coordinate startLeft = baseLine.pointAlongOffset(0, offset);
        Coordinate endLeft = baseLine.pointAlongOffset(1, offset);
        //LineString spineSkeletonLine = new GeometryFactory().createLineString(new Coordinate[]{startLeft, endLeft});
        LineSegment spineSkeletonLine = new LineSegment(startLeft, endLeft);

        return 0;

    }



    public boolean verifyBasePoints(ImagePlus imp, Point left_p, Point right_p) {
        Coordinate baseLeftBoundary = new Coordinate(left_p.x, left_p.y),
                   baseRightBoundary = new Coordinate(right_p.x, right_p.y);

        LineSegment baseLine = new LineSegment(baseLeftBoundary, baseRightBoundary);
        int i = 0;

        if ( baseLine.getLength() > 40 )
            return false;

        while ( verifyPoint(imp, new Point((int) baseLeftBoundary.x, (int) baseLeftBoundary.y))
                &&
                verifyPoint(imp, new Point((int) baseRightBoundary.x, (int) baseRightBoundary.y)) ) {
            i++;
            baseLeftBoundary = baseLine.pointAlong(- i/baseLine.getLength());
            baseRightBoundary = baseLine.pointAlong(1 + i/baseLine.getLength());
            if ( i > maxDistanceToBaselineEnd*2 ) {
                return false;
            }
        }
        return true;
    }



    public boolean verifyPoint(ImagePlus imp, Point point) {
        return imageProcessor.getPixel(point.x, point.y) > 0;
    }

    public boolean verifyPoint(ImagePlus imp, int x, int y) {
        return imageProcessor.getPixel(x, y) > 0;
    }


    public boolean verifyCoordinate(ImagePlus imp, Coordinate coordinate) {
        return imageProcessor.getPixel((int) coordinate.x, (int) coordinate.y) > 0;

    }


    public Point autoCorrectPoint(ImagePlus imp, Point point) {
        int x = 6, y = 6;
        double distance = 6, tempDistance;
        Coordinate initPoint = new Coordinate(point.x, point.y);

        for (int i = 0; i <= 4; i++) {
            for (int j = 0; j <= 4 ; j++) {
                if ( verifyPoint(imp, new Point(point.x - 2 + i, point.y - 2 + j)) ) {
                    tempDistance = initPoint.distance(new Coordinate(point.x - 2 + i, point.y - 2 + j));
                    if ( tempDistance < distance ) {
                        distance = tempDistance;
                        x = i - 2;
                        y = j - 2;
                    }
                }
            }
        }
        if ( x != 6 ) {
            if ( y != 6 )
                return new Point(point.x + x, point.y + y);
            else
                return new Point(point.x + x, point.y);
        } else {
            if (y != 6)
                return new Point(point.x, point.y + y);
            else
                return nullPoint;
        }
    }


    @Override
    public void mousePressed(MouseEvent e) {}

    @Override
    public void mouseReleased(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
}
